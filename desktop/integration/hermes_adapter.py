"""Hermes Agent via ACP (stdio JSON-RPC) — adapter utama Pochi.

Kenapa ACP: ini jalur resmi Hermes untuk editor/agent, mendukung streaming,
tool call, rencana, usage, dan permintaan izin. Tidak butuh API key di UI;
Hermes membaca kredensialnya sendiri (di mesin ini: 9Router lokal).

Protokol yang dipakai (terverifikasi pada Hermes 0.22.5):
  initialize → session/new → session/prompt
  notifikasi: session/update (agent_message_chunk, tool_call, plan, usage_update)
  permintaan server: session/request_permission (dijawab UI, bukan auto-approve)

Semua I/O berjalan di thread terpisah supaya UI tetap responsif.
"""
from __future__ import annotations

import json
import os
import queue
import shlex
import subprocess
import threading
import time
from collections import deque

from PyQt6.QtCore import QThread, pyqtSignal

from integration.bridge import PochiBridge
from integration.text import extract_text, model_short, sanitize


def parse_update(update: dict) -> dict | None:
    """Ubah satu ``session/update`` ACP menjadi payload UI (atau None)."""
    if not update:
        return None
    kind = update.get("sessionUpdate")
    if kind == "agent_message_chunk":
        text = extract_text(update.get("content"))
        return {"_delta": text} if text else None
    if kind in ("agent_thought_chunk", "agent_thought"):
        return None
    if kind == "tool_call":
        return {
            "id": update.get("toolCallId", ""),
            "kind": "tool",
            "title": sanitize(update.get("title") or "Menjalankan tool", 90),
            "detail": sanitize(update.get("kind") or "tool", 80),
            "state": _map_tool_state(update.get("status")),
        }
    if kind == "tool_call_update":
        state = _map_tool_state(update.get("status"))
        return {
            "id": update.get("toolCallId", ""),
            "kind": "tool",
            "title": sanitize(update.get("title") or "Tool", 90),
            "detail": sanitize(update.get("status") or "", 60),
            "state": state,
        }
    if kind == "plan":
        entries = update.get("entries") or []
        active = next((e for e in entries
                       if str(e.get("status", "")).lower() in
                       ("in_progress", "in-progress", "running")), None)
        if not active and entries:
            active = entries[0]
        if active:
            return {
                "kind": "tool",
                "title": "Rencana",
                "detail": sanitize(str(active.get("content") or ""), 110),
                "state": "running",
            }
    return None


def _map_tool_state(status) -> str:
    s = str(status or "").lower()
    if s in ("completed", "complete", "success"):
        return "done"
    if s in ("failed", "error", "cancelled", "canceled"):
        return "error"
    return "running"


PREAMBLE_HEADER = ("[Memori Pochi dari sesi/backend sebelumnya — ini konteks, "
                   "bukan permintaan baru]")


def history_preamble(history: list[dict] | None,
                     max_chars: int = 1800) -> str:
    """Ringkas riwayat/memori menjadi preamble untuk sesi ACP baru.

    Hermes menyimpan konteksnya sendiri per sesi, jadi riwayat hanya dikirim
    sekali saat sesi baru dimulai (mis. setelah restart atau pindah backend).
    """
    lines: list[str] = []
    total = len(PREAMBLE_HEADER) + 1
    for msg in reversed(history or []):
        if not isinstance(msg, dict):
            continue
        role = msg.get("role")
        content = msg.get("content")
        if role not in ("user", "assistant") or not isinstance(content, str):
            continue
        text = content.strip()
        if not text:
            continue
        if len(text) > 600:
            text = text[:597] + "…"
        line = f"{'Pengguna' if role == 'user' else 'Pochi'}: {text}"
        if total + len(line) + 1 > max_chars:
            break
        lines.append(line)
        total += len(line) + 1
    if not lines:
        return ""
    lines.reverse()
    return PREAMBLE_HEADER + "\n" + "\n".join(lines)


class HermesAcpAdapter(PochiBridge):
    name = "hermes"
    label = "Hermes ACP"

    def __init__(self, cfg, parent=None):
        super().__init__(parent)
        self.cfg = cfg
        self._worker: _AcpWorker | None = None
        self._primed = False

    def start(self) -> None:
        if self._worker is not None:
            return
        self._primed = False
        self._worker = _AcpWorker(self.cfg)
        w = self._worker
        w.sig_status.connect(self.on_status)
        w.sig_delta.connect(self.on_delta)
        w.sig_message.connect(self.on_message)
        w.sig_activity.connect(self.on_activity)
        w.sig_permission.connect(self.on_permission)
        w.sig_error.connect(self.on_error)
        w.sig_connection.connect(self.on_connection)
        w.sig_usage.connect(self.on_usage)
        w.start()

    def send(self, text: str, history: list[dict] | None = None,
             files: list | tuple = ()) -> None:
        if self._worker is None:
            self.start()
        preamble = ""
        if not self._primed:
            # Hermes punya sesi ACP sendiri: konteks lama hanya perlu dikirim
            # sekali di prompt pertama (termasuk memori dari backend lain).
            preamble = history_preamble(history or [])
            self._primed = True
        if self._worker is not None:
            self._worker.enqueue_prompt(text, preamble)

    def cancel(self) -> None:
        if self._worker is not None:
            self._worker.cancel_prompt()

    def respond_permission(self, request_id, option_id: str | None) -> None:
        if self._worker is not None:
            self._worker.answer_permission(request_id, option_id)

    def stop(self) -> None:
        w, self._worker = self._worker, None
        if w is not None:
            w.request_stop()
            if not w.wait(4000):
                w.terminate()
                w.wait(1500)


class _AcpWorker(QThread):
    sig_status = pyqtSignal(str)
    sig_delta = pyqtSignal(str)
    sig_message = pyqtSignal(str, str)
    sig_activity = pyqtSignal(object)
    sig_permission = pyqtSignal(object)
    sig_error = pyqtSignal(str)
    sig_connection = pyqtSignal(object)
    sig_usage = pyqtSignal(int, int)

    def __init__(self, cfg):
        super().__init__()
        self.cfg = cfg
        self._outbox: queue.Queue = queue.Queue()
        self._inbox: queue.Queue = queue.Queue()
        self._stop_evt = threading.Event()
        self._proc: subprocess.Popen | None = None
        self._write_lock = threading.Lock()
        self._reader = None
        self._stderr_lines = deque(maxlen=12)
        self._next_id = 3
        self._session_id: str | None = None
        self._ready = False
        self._prompt_id: int | None = None
        self._accum = ""
        self._cancelled_prompt = False
        self._queued: list[tuple[str, str]] = []
        self._permission_id = None
        self._pending_permission = None
        self._model = cfg.model_name
        self._handshake_deadline = 0.0

    # -------------------------------------------------------------- public
    def enqueue_prompt(self, text: str, preamble: str = "") -> None:
        self._outbox.put(("prompt", (text, preamble)))

    def cancel_prompt(self) -> None:
        self._outbox.put(("cancel", None))

    def answer_permission(self, request_id, option_id) -> None:
        self._outbox.put(("permission", (request_id, option_id)))

    def request_stop(self) -> None:
        self._stop_evt.set()
        self._outbox.put(("stop", None))

    # ----------------------------------------------------------------- run
    def run(self) -> None:
        try:
            self._spawn()
        except FileNotFoundError:
            self._fail("Hermes CLI tidak ditemukan. Pasang Hermes atau ubah backend di Pengaturan.")
            return
        except Exception as exc:
            self._fail(f"Tidak bisa menjalankan Hermes ACP: {exc}")
            return

        self._handshake_deadline = time.monotonic() + 30.0
        self._send({"jsonrpc": "2.0", "id": 1, "method": "initialize",
                    "params": {
                        "protocolVersion": 1,
                        "clientCapabilities": {
                            "fs": {"readTextFile": False, "writeTextFile": False},
                            "terminal": False,
                        },
                        "clientInfo": {"name": "pochi-desktop",
                                       "title": "Pochi Dynamic Island",
                                       "version": "2.0.0"},
                    }})

        while not self._stop_evt.is_set():
            try:
                raw = self._inbox.get(timeout=0.06)
            except queue.Empty:
                raw = ""
            if raw is None:
                self._on_closed()
                break
            if raw:
                self._handle_line(raw)
            self._drain_outbox()
            if not self._ready and self._handshake_deadline and \
                    time.monotonic() > self._handshake_deadline:
                self._fail("Hermes ACP tidak merespons (timeout 30 detik).")
                break

        self._shutdown()

    # ------------------------------------------------------------- proses
    def _spawn(self) -> None:
        cmd = shlex.split(self.cfg.hermes_command or "hermes") + ["acp"]
        env = os.environ.copy()
        self._proc = subprocess.Popen(
            cmd, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
            stderr=subprocess.PIPE, text=True, bufsize=1, env=env,
            cwd=str(os.path.expanduser("~")),
        )
        self._reader = threading.Thread(target=self._read_stdout, daemon=True)
        self._reader.start()
        threading.Thread(target=self._read_stderr, daemon=True).start()

    def _read_stdout(self) -> None:
        proc = self._proc
        try:
            for line in proc.stdout:  # type: ignore[union-attr]
                if self._stop_evt.is_set():
                    break
                self._inbox.put(line)
        except Exception:
            pass
        finally:
            self._inbox.put(None)

    def _read_stderr(self) -> None:
        proc = self._proc
        try:
            for line in proc.stderr:  # type: ignore[union-attr]
                line = line.strip()
                if line:
                    self._stderr_lines.append(line)
        except Exception:
            pass

    def _send(self, obj: dict) -> None:
        if self._proc is None or self._proc.poll() is not None:
            return
        data = json.dumps(obj, ensure_ascii=False) + "\n"
        try:
            with self._write_lock:
                self._proc.stdin.write(data)  # type: ignore[union-attr]
                self._proc.stdin.flush()      # type: ignore[union-attr]
        except Exception:
            pass

    # ------------------------------------------------------------- outbox
    def _drain_outbox(self) -> None:
        while True:
            try:
                kind, payload = self._outbox.get_nowait()
            except queue.Empty:
                return
            if kind == "prompt":
                text, preamble = payload
                if not self._ready:
                    self._queued.append((str(text), str(preamble)))
                else:
                    self._start_prompt(str(text), str(preamble))
            elif kind == "cancel":
                self._send_cancel()
            elif kind == "permission":
                req_id, option_id = payload
                self._answer_permission(req_id, option_id)
            elif kind == "stop":
                return

    def _start_prompt(self, text: str, preamble: str = "") -> None:
        if self._prompt_id is not None:
            return
        self._prompt_id = self._next_id
        self._next_id += 1
        self._accum = ""
        self._cancelled_prompt = False
        self.sig_status.emit("thinking")
        payload = f"{preamble}\n\n{text}" if preamble else text
        self._send({
            "jsonrpc": "2.0", "id": self._prompt_id, "method": "session/prompt",
            "params": {
                "sessionId": self._session_id,
                "prompt": [{"type": "text", "text": payload}],
            },
        })

    def _send_cancel(self) -> None:
        if self._session_id and self._prompt_id is not None:
            self._cancelled_prompt = True
            self._send({"jsonrpc": "2.0", "method": "session/cancel",
                        "params": {"sessionId": self._session_id}})

    def _answer_permission(self, req_id, option_id) -> None:
        if req_id is None:
            return
        if option_id is None:
            outcome = {"outcome": "cancelled"}
        else:
            outcome = {"outcome": "selected", "optionId": str(option_id)}
        self._send({"jsonrpc": "2.0", "id": req_id, "result": {"outcome": outcome}})
        self._permission_id = None
        self._pending_permission = None

    # ------------------------------------------------------------- inbound
    def _handle_line(self, line: str) -> None:
        try:
            msg = json.loads(line)
        except Exception:
            return
        if not isinstance(msg, dict):
            return
        method = msg.get("method")
        if method == "session/request_permission":
            self._handle_permission(msg)
            return
        if method == "session/update":
            self._handle_update(msg.get("params") or {})
            return
        if method and "id" in msg:
            # permintaan server lain: tolak dengan sopan agar tidak menggantung
            self._send({"jsonrpc": "2.0", "id": msg["id"],
                        "error": {"code": -32601, "message": "Not supported"}})
            return
        if "id" not in msg:
            return
        mid = msg.get("id")
        if "error" in msg:
            self._handle_rpc_error(mid, msg["error"])
            return
        result = msg.get("result") or {}
        if mid == 1:
            self._send({
                "jsonrpc": "2.0", "id": 2, "method": "session/new",
                "params": {"cwd": os.path.expanduser("~"), "mcpServers": []},
            })
        elif mid == 2:
            self._on_session(result)
        elif mid == self._prompt_id:
            self._on_prompt_done(result)

    def _on_session(self, result: dict) -> None:
        self._session_id = result.get("sessionId")
        models = result.get("models") or {}
        current = models.get("currentModelId") or self.cfg.model_name
        self._model = model_short(current)
        self._ready = True
        self.sig_connection.emit({
            "backend": "hermes", "label": "Hermes ACP", "ok": True,
            "model": self._model,
            "detail": "ACP stdio · sesi aktif",
        })
        self.sig_status.emit("idle")
        queued, self._queued = self._queued, []
        for text, preamble in queued:
            self._start_prompt(text, preamble)

    def _on_prompt_done(self, result: dict) -> None:
        self._prompt_id = None
        stop = str(result.get("stopReason") or "")
        if stop == "cancelled" or self._cancelled_prompt:
            self._cancelled_prompt = False
            self._accum = ""
            self.sig_status.emit("idle")
            return
        text = self._accum.strip()
        if text:
            self.sig_message.emit(text, "HERMES")
        else:
            self.sig_error.emit(
                "Model tidak mengembalikan teks. Coba lagi atau periksa model di Hermes."
            )
        usage = result.get("usage") or {}
        total = int(usage.get("totalTokens") or 0)
        if total:
            self.sig_usage.emit(total, 0)
        self._accum = ""

    def _handle_rpc_error(self, mid, err: dict) -> None:
        message = sanitize(str(err.get("message") or "Kesalahan JSON-RPC"), 200)
        if mid == self._prompt_id:
            self._prompt_id = None
            self.sig_error.emit(f"Hermes: {message}")
            self.sig_status.emit("idle")
        elif mid in (1, 2):
            self._fail(f"Gagal memulai sesi Hermes: {message}")
        else:
            self.sig_error.emit(f"Hermes: {message}")

    def _handle_update(self, params: dict) -> None:
        update = params.get("update") or {}
        kind = update.get("sessionUpdate")
        if kind == "usage_update":
            try:
                self.sig_usage.emit(int(update.get("used") or 0),
                                    int(update.get("size") or 0))
            except Exception:
                pass
            return
        if kind == "agent_message_chunk":
            text = extract_text(update.get("content"))
            if text:
                self._accum += text
                self.sig_delta.emit(text)
            return
        payload = parse_update(update)
        if payload:
            self.sig_activity.emit(payload)

    def _handle_permission(self, msg: dict) -> None:
        params = msg.get("params") or {}
        tool = params.get("toolCall") or {}
        options = []
        for opt in params.get("options") or []:
            if not isinstance(opt, dict):
                continue
            options.append({
                "id": opt.get("optionId") or opt.get("id") or "",
                "label": opt.get("name") or opt.get("label") or "Pilih",
                "kind": opt.get("kind") or "",
            })
        payload = {
            "request_id": msg.get("id"),
            "title": sanitize(tool.get("title") or "Tool meminta izin", 90),
            "detail": sanitize(
                str(tool.get("kind") or tool.get("rawInput") or "Aksi tool"), 180),
            "options": [o for o in options if o["id"]],
        }
        if self._permission_id is not None:
            # jangan biarkan dua izin menggantung
            self._answer_permission(msg.get("id"), None)
            return
        self._permission_id = msg.get("id")
        self._pending_permission = payload
        self.sig_permission.emit(payload)

    # ------------------------------------------------------------- failure
    def _fail(self, message: str) -> None:
        detail = ""
        if self._stderr_lines:
            detail = sanitize(self._stderr_lines[-1], 120)
        full = message if not detail else f"{message} ({detail})"
        # Error lebih dulu supaya AutoAdapter bisa menyimpannya sebelum
        # memutuskan fallback (menghindari toast ganda saat mode auto).
        self.sig_error.emit(full)
        self.sig_connection.emit({
            "backend": "hermes", "label": "Hermes ACP", "ok": False,
            "model": "", "detail": full,
        })

    def _on_closed(self) -> None:
        if self._stop_evt.is_set():
            return
        code = self._proc.returncode if self._proc else None
        if self._prompt_id is not None:
            self._prompt_id = None
            self.sig_error.emit("Hermes ACP berhenti saat memproses permintaan.")
        self.sig_connection.emit({
            "backend": "hermes", "label": "Hermes ACP", "ok": False,
            "model": "", "detail": f"Proses berhenti (exit {code}).",
        })

    def _shutdown(self) -> None:
        if self._permission_id is not None:
            try:
                self._answer_permission(self._permission_id, None)
            except Exception:
                pass
        proc = self._proc
        if proc is not None and proc.poll() is None:
            try:
                proc.terminate()
                proc.wait(timeout=2.0)
            except Exception:
                try:
                    proc.kill()
                except Exception:
                    pass
