"""Pochi desktop — state aplikasi & controller (event bus).

UI tidak pernah memanggil backend secara langsung. Semua alur:
``UI → Controller → PochiBridge → adapter`` dan balik lewat sinyal Qt.
"""
from __future__ import annotations

import time
import uuid
from dataclasses import dataclass, field

from PyQt6.QtCore import QObject, QTimer, pyqtSignal

from app import theme
from services.memory import MemoryStore


class Status:
    IDLE = "idle"
    LISTENING = "listening"
    THINKING = "thinking"
    WORKING = "working"
    SUCCESS = "success"
    ERROR = "error"

    LABELS = {
        IDLE: "Idle",
        LISTENING: "Listening",
        THINKING: "Thinking",
        WORKING: "Working",
        SUCCESS: "Success",
        ERROR: "Error",
    }
    DETAILS = {
        IDLE: "Siap membantu",
        LISTENING: "Mendengarkan…",
        THINKING: "Memproses…",
        WORKING: "Sedang bekerja…",
        SUCCESS: "Selesai",
        ERROR: "Butuh perhatian",
    }
    COLORS = {
        IDLE: theme.TEXT.name(),
        LISTENING: theme.AMBER.name(),
        THINKING: theme.CYAN.name(),
        WORKING: theme.CYAN.name(),
        SUCCESS: theme.GREEN.name(),
        ERROR: theme.RED.name(),
    }

    @classmethod
    def color(cls, s: str):
        from PyQt6.QtGui import QColor
        return QColor(cls.COLORS.get(s, cls.COLORS[cls.IDLE]))


@dataclass
class ChatMessage:
    role: str                    # user | assistant | system
    text: str
    when: float = field(default_factory=time.time)
    meta: str = ""               # HERMES | 9ROUTER | DEMO | ERROR
    streaming: bool = False
    error: bool = False
    files: tuple = ()            # lampiran (jalur file lokal)


@dataclass
class Activity:
    kind: str = "tool"           # tool | thinking | timer
    title: str = ""
    detail: str = ""
    state: str = "running"       # running | done | error
    progress: float | None = None
    id: str = ""


@dataclass
class Task:
    id: str
    label: str
    total: float
    ends_at: float
    done: bool = False

    def remaining(self, now: float | None = None) -> float:
        return max(0.0, self.ends_at - (now if now is not None else time.monotonic()))

    @property
    def fraction(self) -> float:
        if self.total <= 0:
            return 0.0
        return min(1.0, max(0.0, self.remaining() / self.total))


@dataclass
class Notice:
    title: str
    message: str
    level: str = "info"
    when: float = field(default_factory=time.time)


@dataclass
class Connection:
    backend: str = "starting"
    label: str = "Menghubungkan…"
    model: str = ""
    ok: bool = False
    detail: str = ""
    latency_ms: int = 0

    @classmethod
    def from_dict(cls, d: dict) -> "Connection":
        return cls(
            backend=str(d.get("backend", "unknown")),
            label=str(d.get("label", "Unknown")),
            model=str(d.get("model", "")),
            ok=bool(d.get("ok", False)),
            detail=str(d.get("detail", "")),
            latency_ms=int(d.get("latency_ms", 0) or 0),
        )


def _fmt_duration(seconds: float) -> str:
    s = max(0, int(seconds + 0.5))
    if s >= 3600:
        return f"{s // 3600}:{(s % 3600) // 60:02d}:{s % 60:02d}"
    return f"{s // 60}:{s % 60:02d}"


class Controller(QObject):
    """Menyimpan state dan memancarkan sinyal ke semua komponen UI."""

    statusChanged = pyqtSignal(str)
    messagesChanged = pyqtSignal()
    streamChanged = pyqtSignal()
    activityChanged = pyqtSignal(object)      # Activity | None
    tasksChanged = pyqtSignal()
    connectionChanged = pyqtSignal(object)    # Connection
    noticesChanged = pyqtSignal()
    usageChanged = pyqtSignal(int, int)       # used, size
    permissionRequested = pyqtSignal(object)  # dict | None
    errorOccurred = pyqtSignal(str)
    replyReady = pyqtSignal(str)
    toast = pyqtSignal(str, str, str)         # title, message, level

    def __init__(self, cfg, bridge, parent=None, memory: MemoryStore | None = None):
        super().__init__(parent)
        self.cfg = cfg
        self.bridge = bridge
        self.memory = memory if memory is not None else MemoryStore()
        # ID sesi memisahkan percakapan berjalan dari memori sesi lama, agar
        # konteks lama tidak terkirim dua kali (history UI + memori).
        self._session_id = uuid.uuid4().hex[:12]
        self._memory_seed: list[dict] | None = None
        self._status = Status.IDLE
        self._messages: list[ChatMessage] = []
        self._notices: list[Notice] = []
        self._tasks: list[Task] = []
        self._activity: Activity | None = None
        self._permission: dict | None = None
        self._connection = Connection()
        self._context_used = 0
        self._context_size = 0

        self._pending_delta = ""
        self._delta_timer = QTimer(self)
        self._delta_timer.setSingleShot(True)
        self._delta_timer.setInterval(28)
        self._delta_timer.timeout.connect(self._flush_delta)

        self._flash_timer = QTimer(self)
        self._flash_timer.setSingleShot(True)
        self._flash_timer.timeout.connect(lambda: self.set_status(Status.IDLE))
        self._error_timer = QTimer(self)
        self._error_timer.setSingleShot(True)
        self._error_timer.timeout.connect(lambda: self.set_status(Status.IDLE))
        self._activity_timer = QTimer(self)
        self._activity_timer.setSingleShot(True)
        self._activity_timer.timeout.connect(lambda: self._set_activity(None))

        self._task_timer = QTimer(self)
        self._task_timer.setInterval(1000)
        self._task_timer.timeout.connect(self._tick_tasks)

        self._wire_bridge()

    # ------------------------------------------------------------- bridge
    def _wire_bridge(self) -> None:
        b = self.bridge
        b.on_status.connect(self._on_bridge_status)
        b.on_delta.connect(self._on_delta)
        b.on_message.connect(self._on_message)
        b.on_activity.connect(self._on_activity)
        b.on_permission.connect(self._on_permission)
        b.on_error.connect(self._on_error)
        b.on_connection.connect(self._on_connection)
        b.on_usage.connect(self._on_usage)

    def _unwire_bridge(self) -> None:
        b = self.bridge
        for signal, slot in (
            (b.on_status, self._on_bridge_status),
            (b.on_delta, self._on_delta),
            (b.on_message, self._on_message),
            (b.on_activity, self._on_activity),
            (b.on_permission, self._on_permission),
            (b.on_error, self._on_error),
            (b.on_connection, self._on_connection),
            (b.on_usage, self._on_usage),
        ):
            try:
                signal.disconnect(slot)
            except Exception:
                pass

    def start(self) -> None:
        try:
            self.bridge.start()
        except Exception as exc:
            self._on_error(f"Backend gagal dimulai: {exc}")

    def replace_bridge(self, bridge) -> None:
        """Ganti backend saat runtime (dipakai setelah pengaturan disimpan)."""
        self.cancel()
        self._unwire_bridge()
        old, self.bridge = self.bridge, bridge
        try:
            old.stop()
        except Exception:
            pass
        self._permission = None
        self.permissionRequested.emit(None)
        self._connection = Connection(
            backend="starting", label="Menghubungkan…", ok=False,
            detail="Backend baru dimulai.")
        self.connectionChanged.emit(self._connection)
        self._set_activity(None)
        self._wire_bridge()
        self.start()

    # ------------------------------------------------------------- status
    @property
    def status(self) -> str:
        return self._status

    @property
    def busy(self) -> bool:
        return self._status in (Status.THINKING, Status.WORKING, Status.LISTENING)

    def set_status(self, s: str) -> None:
        if s not in Status.LABELS:
            return
        if s != self._status:
            self._status = s
            self.statusChanged.emit(s)

    def _on_bridge_status(self, s: str) -> None:
        if s == Status.SUCCESS:
            self._flash_success()
        elif s == Status.ERROR:
            self.set_status(Status.ERROR)
        else:
            self.set_status(s)

    def _flash_success(self) -> None:
        self.set_status(Status.SUCCESS)
        self._flash_timer.start(1300)

    # ------------------------------------------------------------- pesan
    @property
    def messages(self) -> list[ChatMessage]:
        return self._messages

    @property
    def activity(self) -> Activity | None:
        return self._activity

    @property
    def tasks(self) -> list[Task]:
        return self._tasks

    @property
    def notices(self) -> list[Notice]:
        return self._notices

    @property
    def connection(self) -> Connection:
        return self._connection

    @property
    def permission(self) -> dict | None:
        return self._permission

    @property
    def context_usage(self) -> tuple[int, int]:
        return self._context_used, self._context_size

    def _history(self) -> list[dict]:
        """Riwayat UI + konteks memori sesi/backend sebelumnya."""
        history = [
            {"role": m.role, "content": m.text}
            for m in self._messages[-24:]
            if m.role in ("user", "assistant") and not m.error and not m.streaming
        ]
        if self._memory_seed is None:
            self._memory_seed = self.memory.seed_messages(
                exclude_session=self._session_id)
        return list(self._memory_seed) + history

    def send(self, text: str, files: list | tuple = ()) -> bool:
        text = (text or "").strip()
        if not text and not files:
            return False
        if self._permission is not None:
            self.notify("Izin tertunda", "Setujui atau tolak permintaan tool lebih dulu.", "warning")
            return False
        if self.busy:
            self.notify("Pochi masih bekerja", "Tunggu balasan atau hentikan dengan tombol stop.", "warning")
            return False
        history = self._history()
        self._messages.append(ChatMessage(role="user", text=text, files=tuple(files)))
        self.memory.add("user", text, session=self._session_id,
                        meta=self._connection.backend)
        self.messagesChanged.emit()
        self._pending_delta = ""
        self.set_status(Status.THINKING)
        self._activity_timer.stop()
        self._activity = Activity(kind="thinking", title="Memahami permintaan",
                                  detail="Menunggu model…", state="running")
        self.activityChanged.emit(self._activity)
        try:
            self.bridge.send(text, history, list(files))
        except Exception as exc:  # adapter bermasalah tidak boleh mematikan UI
            self._on_error(f"Gagal mengirim: {exc}")
        return True

    def _on_delta(self, chunk: str) -> None:
        if not chunk:
            return
        self._pending_delta += chunk
        if not self._delta_timer.isActive():
            self._delta_timer.start()

    def _flush_delta(self) -> None:
        chunk, self._pending_delta = self._pending_delta, ""
        if not chunk:
            return
        last = self._messages[-1] if self._messages else None
        if last is None or last.role != "assistant" or not last.streaming:
            self._messages.append(ChatMessage(
                role="assistant", text=chunk, streaming=True,
                meta=self._meta()))
            self.messagesChanged.emit()
        else:
            last.text += chunk
        self.streamChanged.emit()
        if self._status == Status.THINKING:
            self.set_status(Status.WORKING)
        if self._activity is not None and self._activity.kind == "thinking":
            self._set_activity(None)

    def _on_message(self, text: str, meta: str) -> None:
        if self._delta_timer.isActive():
            self._delta_timer.stop()
        self._flush_delta()
        meta = meta or self._meta()
        last = self._messages[-1] if self._messages else None
        if last is not None and last.role == "assistant" and last.streaming:
            last.streaming = False
            last.meta = meta
            if text and text.strip() and not last.text.strip():
                last.text = text
            elif text and text.strip() and text.strip() != last.text.strip():
                # backend mengirim teks final yang berbeda (mis. tanpa chunk)
                if len(text) > len(last.text):
                    last.text = text
            final = last.text
        else:
            final = text
            self._messages.append(
                ChatMessage(role="assistant", text=text, meta=meta))
        if final.strip():
            self.memory.add("assistant", final, meta=meta,
                            session=self._session_id)
        self.messagesChanged.emit()
        self._flash_success()
        self._set_activity(None)
        self.replyReady.emit(final)

    def _on_error(self, message: str) -> None:
        if self._delta_timer.isActive():
            self._delta_timer.stop()
        self._flush_delta()
        self._messages.append(ChatMessage(
            role="assistant", text=message, meta="ERROR", error=True))
        self.messagesChanged.emit()
        self._set_activity(None)
        self.set_status(Status.ERROR)
        self._error_timer.start(6500)
        self.notify("Pochi gagal", message, "error")
        self.errorOccurred.emit(message)

    def _meta(self) -> str:
        b = self._connection.backend
        if b == "hermes":
            return "HERMES"
        if b == "9router":
            return "9ROUTER"
        if b == "demo":
            return "DEMO"
        return b.upper() if b else "POCHI"

    def cancel(self) -> None:
        if self._permission is not None:
            self.respond_permission(None)
        try:
            self.bridge.cancel()
        except Exception:
            pass
        self._set_activity(None)
        if self.busy:
            # beri ruang satu tick agar adapter mengirim status final
            self.set_status(Status.IDLE)
            self.notify("Dihentikan", "Permintaan dibatalkan.", "info")

    # ------------------------------------------------------------- aktivitas
    def _set_activity(self, activity: Activity | None) -> None:
        self._activity = activity
        self.activityChanged.emit(activity)

    def _on_activity(self, payload) -> None:
        if not payload:
            self._activity_timer.stop()
            self._set_activity(None)
            return
        if isinstance(payload, Activity):
            act = payload
        else:
            act = Activity(
                kind=str(payload.get("kind", "tool")),
                title=str(payload.get("title", "")),
                detail=str(payload.get("detail", "")),
                state=str(payload.get("state", "running")),
                progress=payload.get("progress"),
                id=str(payload.get("id", "")),
            )
        self._activity_timer.stop()
        self._set_activity(act)
        if act.state in ("done", "error"):
            self._activity_timer.start(2600)

    # ------------------------------------------------------------- koneksi
    def _on_connection(self, info) -> None:
        conn = Connection.from_dict(info) if isinstance(info, dict) else info
        if not isinstance(conn, Connection):
            return
        previous = self._connection
        self._connection = conn
        self.connectionChanged.emit(conn)
        if conn.ok and conn.backend == "demo" and previous.backend != "demo":
            self.notify(
                "Mode tiruan aktif",
                "Tidak ada backend nyata yang tersedia. Balasan tidak diproses "
                "model, hanya tiruan.",
                "warning")
        elif conn.ok and not previous.ok and previous.backend not in ("starting", ""):
            self.notify("Terhubung", f"{conn.label} siap digunakan.", "success")
        elif not conn.ok and previous.ok:
            self.notify("Koneksi terputus", conn.detail or conn.label, "error")

    def _on_usage(self, used: int, size: int) -> None:
        self._context_used, self._context_size = int(used), int(size)
        self.usageChanged.emit(self._context_used, self._context_size)

    def _on_permission(self, payload) -> None:
        self._permission = payload
        self.permissionRequested.emit(payload)
        if payload:
            self.notify("Izin dibutuhkan", str(payload.get("title", "Tool meminta izin")), "warning")

    def respond_permission(self, option_id: str | None) -> None:
        req = self._permission
        self._permission = None
        self.permissionRequested.emit(None)
        if req is None:
            return
        try:
            self.bridge.respond_permission(req.get("request_id"), option_id)
        except Exception as exc:
            self._on_error(f"Gagal menjawab izin: {exc}")
        if option_id is None:
            self.notify("Izin ditolak", "Tool tidak dijalankan.", "info")
        else:
            self.notify("Izin diberikan", "Tool dijalankan sekali.", "info")
        self.set_status(Status.WORKING)

    # ------------------------------------------------------------- timer/tugas
    def add_timer(self, seconds: int, label: str = "") -> Task:
        seconds = max(1, int(seconds))
        task = Task(id=uuid.uuid4().hex[:8],
                    label=label or f"Timer {_fmt_duration(seconds)}",
                    total=float(seconds),
                    ends_at=time.monotonic() + seconds)
        self._tasks.append(task)
        if not self._task_timer.isActive():
            self._task_timer.start()
        self.tasksChanged.emit()
        self.notify("Timer dimulai", f"{task.label} · {_fmt_duration(seconds)}", "info")
        return task

    def remove_task(self, task_id: str) -> None:
        self._tasks = [t for t in self._tasks if t.id != task_id]
        if not any(not t.done for t in self._tasks):
            self._task_timer.stop()
        self.tasksChanged.emit()

    def _tick_tasks(self) -> None:
        now = time.monotonic()
        changed = False
        finished = []
        for t in self._tasks:
            if not t.done and t.remaining(now) <= 0:
                t.done = True
                changed = True
                finished.append(t)
        # bersihkan tugas selesai setelah 10 menit
        before = len(self._tasks)
        self._tasks = [t for t in self._tasks
                       if not (t.done and now - t.ends_at > 600)]
        if len(self._tasks) != before:
            changed = True
        if changed:
            self.tasksChanged.emit()
        for t in finished:
            self.notify("Timer selesai", t.label, "success")
            self._flash_success()
        if not any(not t.done for t in self._tasks):
            self._task_timer.stop()

    @staticmethod
    def format_task(t: Task) -> str:
        return _fmt_duration(t.remaining())

    # ------------------------------------------------------------- notifikasi
    def notify(self, title: str, message: str, level: str = "info") -> None:
        self._notices.insert(0, Notice(title=title, message=message, level=level))
        del self._notices[40:]
        self.noticesChanged.emit()
        self.toast.emit(title, message, level)

    def clear_notices(self) -> None:
        if self._notices:
            self._notices.clear()
            self.noticesChanged.emit()

    # ------------------------------------------------------------- util
    def clear_conversation(self) -> None:
        self._messages.clear()
        self._set_activity(None)
        self._permission = None
        self.permissionRequested.emit(None)
        self.messagesChanged.emit()
        try:
            self.bridge.reset()
        except Exception:
            pass
        self.notify("Percakapan direset",
                    "Konteks UI dibersihkan; memori jangka panjang tetap disimpan.", "info")

    def forget_memory(self) -> None:
        """Hapus memori lintas sesi (dipakai tombol 'Hapus memori')."""
        self.memory.clear()
        self._memory_seed = []
        self.notify("Memori dihapus",
                    "Pochi tidak lagi mengingat percakapan dari sesi lama.", "info")

    @property
    def status_label(self) -> str:
        return Status.LABELS.get(self._status, self._status)

    def shutdown(self) -> None:
        self._task_timer.stop()
        try:
            self.bridge.stop()
        except Exception:
            pass
