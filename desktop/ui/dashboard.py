"""Dashboard opsional Pochi — ringkasan AI, tugas, sistem, dan koneksi.

Semua angka berasal dari layanan nyata (``/proc``, ``/sys``, probe port,
state gateway Hermes). Tidak ada kartu yang menampilkan data karangan: bila
sumber belum tersedia, kartu menunjukkan "—" / "Menunggu data".

Dashboard bersifat *non-modal* dan hanya melakukan polling selama terlihat.
"""
from __future__ import annotations

import time

from PyQt6.QtCore import QSize, Qt, QTimer, pyqtSignal
from PyQt6.QtGui import QColor
from PyQt6.QtWidgets import (
    QFrame, QGridLayout, QHBoxLayout, QLabel, QProgressBar, QPushButton,
    QScrollArea, QSizePolicy, QVBoxLayout, QWidget,
)

from app import theme
from app.state import Status
from platform_x11.x11 import work_area
from ui.components import (
    Card, DialogShell, Dot, EllipsisLabel, IconButton, MetricRow, icon_label,
)
from ui.icons import make_icon
from ui.mascot import PochiMascot


def _ago(ts: float) -> str:
    if not ts:
        return "—"
    delta = max(0, int(time.time() - ts))
    if delta < 5:
        return "baru saja"
    if delta < 60:
        return f"{delta} dtk lalu"
    if delta < 3600:
        return f"{delta // 60} mnt lalu"
    return time.strftime("%H:%M", time.localtime(ts))


def _clock(ts: float) -> str:
    try:
        return time.strftime("%H:%M:%S", time.localtime(ts))
    except Exception:
        return "—"


def _remaining(seconds: float) -> str:
    s = max(0, int(seconds + 0.5))
    if s >= 3600:
        return f"{s // 3600}:{(s % 3600) // 60:02d}:{s % 60:02d}"
    return f"{s // 60}:{s % 60:02d}"


# --------------------------------------------------------------------- cards
class PochiCard(Card):
    def __init__(self, controller, parent=None):
        super().__init__("Pochi AI", "spark", theme.PURPLE, parent)
        self.controller = controller

        row = QHBoxLayout()
        row.setSpacing(10)
        self.mascot = PochiMascot(
            size=42, status_provider=lambda: controller.status,
            reduce_motion=controller.cfg.reduce_motion, show_backing=True)
        row.addWidget(self.mascot, 0, Qt.AlignmentFlag.AlignTop)
        v = QVBoxLayout()
        v.setSpacing(2)
        self.status = QLabel("Idle")
        self.status.setStyleSheet(
            f"color:{theme.TEXT.name()}; font-size:15px; font-weight:600;")
        v.addWidget(self.status)
        self.detail = QLabel("Siap membantu")
        self.detail.setStyleSheet(
            f"color:{theme.MUTED.name()}; font-size:11px;")
        v.addWidget(self.detail)
        self.model = QLabel("—")
        self.model.setObjectName("pill")
        self.model.setSizePolicy(QSizePolicy.Policy.Maximum,
                                 QSizePolicy.Policy.Fixed)
        v.addWidget(self.model, 0, Qt.AlignmentFlag.AlignLeft)
        row.addLayout(v, 1)
        self.add_layout(row)

        self.conn_row = QHBoxLayout()
        self.conn_row.setSpacing(6)
        self.conn_dot = Dot(theme.AMBER, 7)
        self.conn_row.addWidget(self.conn_dot)
        self.conn_text = EllipsisLabel("Menghubungkan…")
        self.conn_text.setStyleSheet(
            f"color:{theme.MUTED.name()}; font-size:10px;")
        self.conn_row.addWidget(self.conn_text, 1)
        self.add_layout(self.conn_row)

        usage = QHBoxLayout()
        usage.setSpacing(8)
        self.usage_label = QLabel("Konteks")
        self.usage_label.setObjectName("dim")
        usage.addWidget(self.usage_label)
        self.usage_bar = QProgressBar()
        self.usage_bar.setRange(0, 100)
        self.usage_bar.setTextVisible(False)
        self.usage_bar.setFixedHeight(4)
        usage.addWidget(self.usage_bar, 1)
        self.usage_pct = QLabel("—")
        self.usage_pct.setStyleSheet(
            f"color:{theme.DIM.name()}; font-size:10px;")
        usage.addWidget(self.usage_pct)
        self.add_layout(usage)

        buttons = QHBoxLayout()
        buttons.setSpacing(6)
        self.chat_btn = QPushButton("Buka Chat")
        self.chat_btn.setObjectName("ghost")
        self.chat_btn.setCursor(Qt.CursorShape.PointingHandCursor)
        buttons.addWidget(self.chat_btn)
        buttons.addStretch(1)
        self.reset_btn = QPushButton("Reset")
        self.reset_btn.setObjectName("ghost")
        self.reset_btn.setToolTip("Hapus percakapan dan konteks UI")
        self.reset_btn.setCursor(Qt.CursorShape.PointingHandCursor)
        self.reset_btn.clicked.connect(controller.clear_conversation)
        buttons.addWidget(self.reset_btn)
        self.add_layout(buttons)

        controller.statusChanged.connect(self._on_status)
        controller.connectionChanged.connect(self._on_connection)
        controller.usageChanged.connect(self._on_usage)
        self._on_status(controller.status)
        self._on_connection(controller.connection)
        self._on_usage(*controller.context_usage)

    def _on_status(self, status: str):
        self.mascot.set_status(status)
        label = Status.LABELS.get(status, status)
        suffix = "…" if status in (Status.THINKING, Status.WORKING,
                                   Status.LISTENING) else ""
        self.status.setText(label + suffix)
        self.detail.setText(Status.DETAILS.get(status, ""))
        color = Status.color(status)
        if status == Status.IDLE:
            color = theme.MUTED
        self.status.setStyleSheet(
            f"color:{color.name()}; font-size:15px; font-weight:600;")

    def _on_connection(self, conn):
        if conn.ok and conn.backend in ("hermes", "9router"):
            color = theme.GREEN
        elif conn.backend == "demo":
            color = theme.AMBER
        elif conn.ok:
            color = theme.GREEN
        else:
            color = theme.RED
        self.conn_dot.set_color(color)
        text = conn.label or "Backend"
        if conn.backend == "demo":
            text = "Pochi"
        elif conn.model:
            text += f" · {conn.model}"
        self.conn_text.setText(text)
        self.conn_text.setToolTip(conn.detail or text)
        self.model.setText(conn.model or self.controller.cfg.model_name)
        self.model.setToolTip(conn.detail or text)

    def _on_usage(self, used: int, size: int):
        if size > 0:
            pct = min(999, int(used * 100 / size))
            self.usage_bar.setValue(min(100, pct))
            self.usage_pct.setText(f"{pct}%")
            self.usage_bar.setToolTip(f"{used:,} / {size:,} token")
        else:
            self.usage_bar.setValue(0)
            self.usage_pct.setText("—")
            self.usage_bar.setToolTip("Belum ada laporan pemakaian konteks.")


class ConnectionRow(QWidget):
    def __init__(self, icon_name: str, title: str, parent=None):
        super().__init__(parent)
        h = QHBoxLayout(self)
        h.setContentsMargins(0, 0, 0, 0)
        h.setSpacing(8)
        self.icon = icon_label(icon_name, theme.MUTED, 15)
        h.addWidget(self.icon)
        v = QVBoxLayout()
        v.setSpacing(0)
        self.title = QLabel(title)
        self.title.setStyleSheet(
            f"color:{theme.TEXT_SOFT.name()}; font-size:11px; font-weight:600;")
        v.addWidget(self.title)
        self.detail = EllipsisLabel("Memeriksa…")
        self.detail.setStyleSheet(
            f"color:{theme.DIM.name()}; font-size:10px;")
        v.addWidget(self.detail)
        h.addLayout(v, 1)
        self.dot = Dot(theme.AMBER, 7)
        h.addWidget(self.dot)

    def set_state(self, text: str, detail: str, color: QColor, tooltip: str = ""):
        self.title.setText(text)
        self.detail.setText(detail)
        self.dot.set_color(color)
        tip = tooltip or detail
        self.setToolTip(tip)
        self.detail.setToolTip(tip)


class ConnectionsCard(Card):
    def __init__(self, cfg, controller, health, parent=None):
        super().__init__("Connections", "wifi", theme.CYAN, parent)
        self.cfg = cfg
        self.controller = controller
        self.health = health

        self.hermes = ConnectionRow("spark", "Hermes ACP")
        self.router = ConnectionRow("globe", "9Router")
        self.telegram = ConnectionRow("telegram", "Telegram")
        self.add(self.hermes)
        self.add(self.router)
        self.add(self.telegram)

        foot = QHBoxLayout()
        foot.setSpacing(6)
        self.checked = QLabel("Belum diperiksa")
        self.checked.setObjectName("dim")
        foot.addWidget(self.checked, 1)
        self.refresh_btn = IconButton("refresh", "Periksa koneksi sekarang", 26, 13)
        self.refresh_btn.clicked.connect(health.refresh)
        foot.addWidget(self.refresh_btn)
        self.add_layout(foot)

        self.telegram.setVisible(False)
        health.updated.connect(self.update_data)
        controller.connectionChanged.connect(lambda _c: self._from_controller())

    def update_data(self, data: dict):
        router = data.get("router") or {}
        gateway = data.get("gateway") or {}
        if router.get("ok"):
            self.router.set_state(
                "9Router · online",
                f"{router.get('endpoint', '')} · {router.get('latency_ms', 0)} ms",
                theme.GREEN, router.get("detail", ""))
        else:
            self.router.set_state(
                "9Router · offline",
                router.get("detail", "Tidak terjangkau"),
                theme.RED, router.get("detail", ""))

        tg_state = str(gateway.get("telegram") or "").lower()
        configured = bool(gateway.get("running")) and tg_state not in (
            "", "unknown", "not-configured", "not_configured")
        if configured:
            if tg_state in ("connected", "ready", "running"):
                color = theme.GREEN
            elif tg_state in ("error", "failed", "disconnected"):
                color = theme.RED
            else:
                color = theme.AMBER
            self.telegram.set_state(
                f"Telegram · {tg_state}",
                f"Gateway PID {gateway.get('pid') or '—'}",
                color, gateway.get("detail", ""))
        self.telegram.setVisible(configured)

        self._from_controller()
        self.checked.setText(
            f"Diperiksa {_clock(data.get('checked_at', 0))} · "
            f"CLI {'ada' if data.get('hermes_cli') else 'tidak ada'}")

    def _from_controller(self):
        conn = self.controller.connection
        if conn.backend == "hermes" and conn.ok:
            self.hermes.set_state(
                "Hermes ACP · terhubung",
                f"{conn.model or 'model aktif'} · {conn.detail}",
                theme.GREEN, conn.detail)
        elif conn.backend == "hermes":
            self.hermes.set_state(
                "Hermes ACP · gagal", conn.detail or "Tidak tersedia",
                theme.RED, conn.detail)
        else:
            self.hermes.set_state(
                "Hermes ACP", "Terpasang; dipakai saat mengirim pesan",
                theme.AMBER,
                "CLI Hermes terdeteksi. Backend aktif saat ini: "
                f"{conn.label or '—'}.")


class TaskLine(QWidget):
    def __init__(self, controller, parent=None):
        super().__init__(parent)
        self.controller = controller
        self.task = None
        h = QHBoxLayout(self)
        h.setContentsMargins(0, 0, 0, 0)
        h.setSpacing(7)
        self.dot = Dot(theme.AMBER, 6)
        h.addWidget(self.dot)
        v = QVBoxLayout()
        v.setSpacing(3)
        top = QHBoxLayout()
        top.setSpacing(6)
        self.label = EllipsisLabel("Timer")
        self.label.setStyleSheet(
            f"color:{theme.TEXT_SOFT.name()}; font-size:11px; font-weight:600;")
        top.addWidget(self.label, 1)
        self.time = QLabel("0:00")
        self.time.setStyleSheet(
            f"color:{theme.AMBER.name()}; font-size:11px; font-weight:600;")
        top.addWidget(self.time)
        v.addLayout(top)
        self.bar = QProgressBar()
        self.bar.setObjectName("amber")
        self.bar.setRange(0, 100)
        self.bar.setTextVisible(False)
        self.bar.setFixedHeight(3)
        v.addWidget(self.bar)
        h.addLayout(v, 1)
        self.cancel = IconButton("close", "Batalkan timer", 22, 10)
        self.cancel.clicked.connect(self._cancel)
        h.addWidget(self.cancel)
        self.setVisible(False)

    def set_task(self, task):
        self.task = task
        if task is None:
            self.setVisible(False)
            return
        self.label.setText(task.label)
        self.label.setToolTip(task.label)
        self.tick()
        self.setVisible(True)

    def tick(self):
        if self.task is None:
            return
        self.time.setText(_remaining(self.task.remaining()))
        self.bar.setValue(int(self.task.fraction * 100))

    def _cancel(self):
        if self.task is not None:
            self.controller.remove_task(self.task.id)


class TasksCard(Card):
    def __init__(self, controller, parent=None):
        super().__init__("Active Tasks", "timer", theme.AMBER, parent)
        self.controller = controller
        self.activity = QWidget()
        ah = QHBoxLayout(self.activity)
        ah.setContentsMargins(0, 0, 0, 0)
        ah.setSpacing(8)
        self.act_icon = icon_label("terminal", theme.PURPLE, 14)
        ah.addWidget(self.act_icon)
        v = QVBoxLayout()
        v.setSpacing(0)
        self.act_title = EllipsisLabel("")
        self.act_title.setStyleSheet(
            f"color:{theme.TEXT_SOFT.name()}; font-size:11px; font-weight:600;")
        v.addWidget(self.act_title)
        self.act_detail = EllipsisLabel("")
        self.act_detail.setStyleSheet(
            f"color:{theme.DIM.name()}; font-size:10px;")
        v.addWidget(self.act_detail)
        ah.addLayout(v, 1)
        self.activity.setVisible(False)
        self.add(self.activity)

        self.lines = [TaskLine(controller) for _ in range(4)]
        for line in self.lines:
            self.add(line)
        self.more = QLabel("")
        self.more.setObjectName("dim")
        self.more.setVisible(False)
        self.add(self.more)
        self.empty = QLabel("Tidak ada timer atau tool yang berjalan.")
        self.empty.setObjectName("dim")
        self.empty.setWordWrap(True)
        self.add(self.empty)

        self.new_btn = QPushButton("Timer baru")
        self.new_btn.setObjectName("ghost")
        self.new_btn.setCursor(Qt.CursorShape.PointingHandCursor)
        self.add(self.new_btn)

        controller.tasksChanged.connect(self.refresh)
        controller.activityChanged.connect(self._on_activity)
        self.refresh()
        self._on_activity(controller.activity)

    def _on_activity(self, act):
        if act is None:
            self.activity.setVisible(False)
        else:
            self.act_title.setText(act.title or "Bekerja")
            self.act_detail.setText(act.detail or act.state)
            color = theme.GREEN if act.state == "done" else (
                theme.RED if act.state == "error" else theme.PURPLE)
            self.act_icon.setPixmap(
                make_icon("check" if act.state == "done" else "terminal",
                          color, 14).pixmap(14, 14))
            self.activity.setVisible(True)
        self.empty.setVisible(
            not self._has_visible_tasks() and act is None)

    def _has_visible_tasks(self) -> bool:
        return any(line.isVisible() for line in self.lines)

    def refresh(self):
        tasks = sorted((t for t in self.controller.tasks if not t.done),
                       key=lambda t: t.remaining())
        for i, line in enumerate(self.lines):
            line.set_task(tasks[i] if i < len(tasks) else None)
        extra = max(0, len(tasks) - len(self.lines))
        self.more.setText(f"+{extra} timer lain" if extra else "")
        self.more.setVisible(bool(extra))
        self.empty.setVisible(not tasks and self.controller.activity is None)
        self.tick()

    def tick(self):
        for line in self.lines:
            if line.isVisible():
                line.tick()


class NotificationsCard(Card):
    def __init__(self, controller, parent=None):
        super().__init__("Notifications", "bell", theme.PURPLE, parent)
        self.controller = controller
        self.list = QVBoxLayout()
        self.list.setSpacing(7)
        self.add_layout(self.list)
        self.empty = QLabel("Belum ada notifikasi pada sesi ini.")
        self.empty.setObjectName("dim")
        self.empty.setWordWrap(True)
        self.add(self.empty)
        self.clear_btn = QPushButton("Bersihkan")
        self.clear_btn.setObjectName("ghost")
        self.clear_btn.setCursor(Qt.CursorShape.PointingHandCursor)
        self.clear_btn.clicked.connect(controller.clear_notices)
        self.add(self.clear_btn)
        controller.noticesChanged.connect(self.refresh)
        self.refresh()

    def refresh(self):
        while self.list.count():
            item = self.list.takeAt(0)
            if item.widget():
                item.widget().deleteLater()
        notices = self.controller.notices[:4]
        for notice in notices:
            row = QWidget()
            h = QHBoxLayout(row)
            h.setContentsMargins(0, 0, 0, 0)
            h.setSpacing(7)
            color = {"success": theme.GREEN, "error": theme.RED,
                     "warning": theme.AMBER}.get(notice.level, theme.CYAN)
            h.addWidget(Dot(color, 6), 0, Qt.AlignmentFlag.AlignTop)
            v = QVBoxLayout()
            v.setSpacing(1)
            title = QLabel(notice.title)
            title.setStyleSheet(
                f"color:{theme.TEXT_SOFT.name()}; font-size:11px; font-weight:600;")
            v.addWidget(title)
            message = QLabel(notice.message)
            message.setWordWrap(True)
            message.setStyleSheet(
                f"color:{theme.MUTED.name()}; font-size:10px;")
            v.addWidget(message)
            when = QLabel(_ago(notice.when))
            when.setStyleSheet(f"color:{theme.DIM.name()}; font-size:9px;")
            v.addWidget(when)
            h.addLayout(v, 1)
            self.list.addWidget(row)
        self.empty.setVisible(not notices)
        self.clear_btn.setVisible(bool(notices))


class QuickActionsCard(Card):
    def __init__(self, parent=None):
        super().__init__("Quick Actions", "spark", theme.PURPLE, parent)
        grid = QGridLayout()
        grid.setHorizontalSpacing(6)
        grid.setVerticalSpacing(6)
        self.chat_btn = self._action("Buka Chat", "chat", grid, 0, 0)
        self.timer_btn = self._action("Timer", "timer", grid, 0, 1)
        self.settings_btn = self._action("Pengaturan", "settings", grid, 1, 0)
        self.hide_btn = self._action("Sembunyikan", "chevron-up", grid, 1, 1)
        self.add_layout(grid)
        note = QLabel("Dashboard ini opsional; island tetap bisa dipakai "
                      "tanpa membukanya.")
        note.setWordWrap(True)
        note.setObjectName("dim")
        self.add(note)

    @staticmethod
    def _action(text: str, icon: str, grid: QGridLayout,
                row: int, col: int) -> QPushButton:
        b = QPushButton(text)
        b.setIcon(make_icon(icon, theme.MUTED, 16))
        b.setIconSize(QSize(16, 16))
        b.setCursor(Qt.CursorShape.PointingHandCursor)
        b.setMinimumHeight(38)
        grid.addWidget(b, row, col)
        return b


# ------------------------------------------------------------------ dashboard
class DashboardWindow(DialogShell):
    """Panel dashboard non-modal dengan enam kartu ringkasan."""

    chatRequested = pyqtSignal()
    settingsRequested = pyqtSignal()
    timerRequested = pyqtSignal()
    hideRequested = pyqtSignal()

    def __init__(self, cfg, controller, monitor, health, parent=None):
        super().__init__("Dashboard", "Ringkasan Pochi dan mesin ini", parent,
                         width=792, height=590)
        self.cfg = cfg
        self.controller = controller
        self.monitor = monitor
        self.health = health
        self.setModal(False)
        self.setAttribute(Qt.WidgetAttribute.WA_QuitOnClose, False)
        self.setWindowTitle("Pochi Dashboard")

        self._tick = QTimer(self)
        self._tick.setInterval(1000)
        self._tick.timeout.connect(self._on_tick)

        body = QScrollArea()
        body.setWidgetResizable(True)
        body.setFrameShape(QFrame.Shape.NoFrame)
        body.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        inner = QWidget()
        grid = QGridLayout(inner)
        grid.setContentsMargins(0, 0, 4, 0)
        grid.setHorizontalSpacing(11)
        grid.setVerticalSpacing(11)
        grid.setColumnStretch(0, 1)
        grid.setColumnStretch(1, 1)

        self.pochi = PochiCard(controller)
        self.system = SystemCard(monitor)
        self.connections = ConnectionsCard(cfg, controller, health)
        self.tasks = TasksCard(controller)
        self.notices = NotificationsCard(controller)
        self.actions = QuickActionsCard()

        grid.addWidget(self.pochi, 0, 0)
        grid.addWidget(self.system, 0, 1)
        grid.addWidget(self.connections, 1, 0)
        grid.addWidget(self.tasks, 1, 1)
        grid.addWidget(self.notices, 2, 0)
        grid.addWidget(self.actions, 2, 1)
        body.setWidget(inner)
        self.add_widget(body)

        self.pochi.chat_btn.clicked.connect(lambda: self.chatRequested.emit())
        self.actions.chat_btn.clicked.connect(lambda: self.chatRequested.emit())
        self.actions.timer_btn.clicked.connect(lambda: self.timerRequested.emit())
        self.tasks.new_btn.clicked.connect(lambda: self.timerRequested.emit())
        self.actions.settings_btn.clicked.connect(
            lambda: self.settingsRequested.emit())
        self.actions.hide_btn.clicked.connect(lambda: self.hideRequested.emit())

        controller.statusChanged.connect(self._update_subtitle)
        controller.connectionChanged.connect(lambda _c: self._update_subtitle())
        controller.noticesChanged.connect(self._update_subtitle)
        self._update_subtitle()

    # ------------------------------------------------------------- lifecycle
    def present(self, island=None):
        if island is not None and island.isVisible():
            g = island.geometry()
            x = g.center().x() - self.width() // 2
            y = g.bottom() + 12
            area = work_area()
            x = min(max(area.left(), x), area.right() - self.width() + 1)
            if y + self.height() > area.bottom():
                y = max(area.top(), g.top() - self.height() - 8)
            self.move(x, y)
        self.show()
        self.raise_()
        self.activateWindow()

    def showEvent(self, e):
        super().showEvent(e)
        self.monitor.acquire("dashboard")
        self.health.acquire("dashboard")
        self.health.refresh()
        self.connections.update_data({})
        self._tick.start()
        self._update_subtitle()

    def hideEvent(self, e):
        self._tick.stop()
        self.monitor.release("dashboard")
        self.health.release("dashboard")
        super().hideEvent(e)

    def _on_tick(self):
        self.tasks.tick()
        self._update_subtitle()

    def _update_subtitle(self):
        status = Status.LABELS.get(self.controller.status, self.controller.status)
        conn = self.controller.connection
        backend = conn.label or "backend"
        if conn.ok:
            backend += f" · {conn.model}" if conn.model else ""
        self.subtitle_label.setText(f"{status} · {backend}")
        notices = len(self.controller.notices)
        self.setWindowTitle(
            "Pochi Dashboard" + (f" · {notices} notifikasi" if notices else ""))

    def apply_config(self):
        self.pochi.mascot.set_reduce_motion(self.cfg.reduce_motion)


class SystemCard(Card):
    def __init__(self, monitor, parent=None):
        super().__init__("System Monitor", "cpu", theme.CYAN, parent)
        self.monitor = monitor
        self.cpu = MetricRow("CPU", "cyan")
        self.ram = MetricRow("RAM", "green")
        self.disk = MetricRow("Disk /", "amber")
        self.add(self.cpu)
        self.add(self.ram)
        self.add(self.disk)

        foot = QHBoxLayout()
        foot.setSpacing(6)
        self.temp = QLabel("Suhu —")
        self.temp.setObjectName("dim")
        foot.addWidget(self.temp)
        foot.addStretch(1)
        self.uptime = QLabel("Uptime —")
        self.uptime.setObjectName("dim")
        foot.addWidget(self.uptime)
        self.add_layout(foot)
        self.extra = QLabel("Menunggu data /proc…")
        self.extra.setObjectName("dim")
        self.add(self.extra)
        monitor.updated.connect(self.update_data)

    def update_data(self, data: dict):
        cpu = data.get("cpu_percent")
        self.cpu.set_value(cpu, f"{cpu:.0f}%" if cpu is not None else "—")
        mem = data.get("mem_percent")
        mem_text = "—"
        if mem is not None:
            mem_text = (f"{mem:.0f}% · {data.get('mem_used_gb', 0):.1f}"
                        f"/{data.get('mem_total_gb', 0):.1f} GB")
        self.ram.set_value(mem, mem_text)
        disk = data.get("disk_percent")
        disk_text = "—"
        if disk is not None:
            disk_text = (f"{disk:.0f}% · {data.get('disk_used_gb', 0):.0f}"
                         f"/{data.get('disk_total_gb', 0):.0f} GB")
        self.disk.set_value(disk, disk_text)

        temp = data.get("temp_c")
        self.temp.setText(f"Suhu {temp:.0f}°C" if temp is not None else "Suhu —")
        uptime = data.get("uptime_s") or 0
        self.uptime.setText(
            f"Uptime {int(uptime // 3600)}j {int(uptime % 3600 // 60)}m")
        bits = [f"{data.get('cores', 0)} core",
                f"load {data.get('load1', 0):.2f}"]
        bat = data.get("battery_percent")
        if bat is not None:
            bits.append(f"baterai {bat}%")
        elif data.get("plugged") is not None:
            bits.append("AC" if data.get("plugged") else "baterai")
        self.extra.setText(" · ".join(bits))
