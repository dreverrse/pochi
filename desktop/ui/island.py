"""Jendela Dynamic Island Pochi — compact pill dan panel expanded.

Frameless, selalu di atas (bila didukung xfwm4), translusen, dan tidak
memblokir desktop saat collapsed. Animasi menghormati ``reduce_motion``.
"""
from __future__ import annotations

import time

from PyQt6.QtCore import (
    QEasingCurve, QLineF, QPoint, QPropertyAnimation, QRect, QRectF, Qt,
    QTimer, pyqtSignal,
)
from PyQt6.QtGui import (
    QColor, QKeySequence, QLinearGradient, QPainter, QPainterPath,
    QPen, QRadialGradient, QShortcut,
)
from PyQt6.QtWidgets import (
    QApplication, QFrame, QGraphicsOpacityEffect, QHBoxLayout, QLabel,
    QLineEdit, QMenu, QPushButton, QVBoxLayout, QWidget,
)

from app import theme
from app.state import Status
from platform_x11.x11 import anchor_origin, work_area
from services.fullscreen import FullscreenService
from ui.activity import ActivityArea
from ui.chat import ChatPanel
from ui.components import Dot, EllipsisLabel, IconButton, icon_label
from ui.icons import make_icon
from ui.mascot import PochiMascot

SHADOW = 6


class DragHandle(QWidget):
    """Area header yang bisa digeser; klik tombol tetap diteruskan ke tombol."""

    dragFinished = pyqtSignal(int, int)

    def __init__(self, window, parent=None):
        super().__init__(parent)
        self._window = window
        self._offset: QPoint | None = None
        self._moved = False
        self.setCursor(Qt.CursorShape.SizeAllCursor)

    def mousePressEvent(self, e):
        if e.button() == Qt.MouseButton.LeftButton:
            self._offset = e.globalPosition().toPoint() - self._window.pos()
            self._moved = False
            e.accept()
            return
        super().mousePressEvent(e)

    def mouseMoveEvent(self, e):
        if self._offset is not None:
            delta = e.globalPosition().toPoint() - self._window.pos() - self._offset
            if abs(delta.x()) + abs(delta.y()) > 2:
                self._moved = True
            self._window.move(e.globalPosition().toPoint() - self._offset)
            e.accept()
            return
        super().mouseMoveEvent(e)

    def mouseReleaseEvent(self, e):
        if self._offset is not None and self._moved:
            self.dragFinished.emit(self._window.x(), self._window.y())
        self._offset = None
        super().mouseReleaseEvent(e)


class IslandWindow(QWidget):
    expandedChanged = pyqtSignal(bool)
    dashboardRequested = pyqtSignal()
    settingsRequested = pyqtSignal()
    timerRequested = pyqtSignal()
    quitRequested = pyqtSignal()

    def __init__(self, cfg, controller, media, parent=None):
        super().__init__(parent, Qt.WindowType.FramelessWindowHint
                         | Qt.WindowType.WindowStaysOnTopHint
                         | Qt.WindowType.Tool)
        self.cfg = cfg
        self.controller = controller
        self.media = media
        self.setAttribute(Qt.WidgetAttribute.WA_TranslucentBackground, True)
        self.setAttribute(Qt.WidgetAttribute.WA_ShowWithoutActivating, True)
        self.setWindowTitle("Pochi")
        self.setAccessibleName("Pochi Dynamic Island")
        self.setAcceptDrops(True)

        self._mode = "collapsed"
        self._drag: tuple[QPoint, QPoint] | None = None
        self._moved = False
        self._expanded_at = 0.0
        self._anim: QPropertyAnimation | None = None
        self._fade: QPropertyAnimation | None = None
        self._pending = ""
        self._status = Status.IDLE
        self._connection_ok = False
        self._permission = None
        self._shortcut = None

        self._compute_sizes()
        self._build_compact()
        self._build_expanded()
        self._position_contents()
        self.setGeometry(self._compact_rect())
        self._compact.setVisible(True)
        self._expanded.setVisible(False)

        self._idle = QTimer(self)
        self._idle.setSingleShot(True)
        self._idle.timeout.connect(self._auto_collapse)
        self._sleep_timer = QTimer(self)
        self._sleep_timer.setSingleShot(True)
        self._sleep_timer.timeout.connect(self._go_sleep)

        self._hidden_by_fullscreen = False
        self._fullscreen = FullscreenService(2000, parent=self)
        self._fullscreen.changed.connect(self._on_fullscreen)
        self._fullscreen.start()

        self._anim = QPropertyAnimation(self, b"geometry", self)
        self._anim.setEasingCurve(QEasingCurve.Type.OutCubic)
        self._anim.finished.connect(self._on_anim_done)
        self._setup_shortcut()

        controller.statusChanged.connect(self._on_status)
        controller.connectionChanged.connect(self._on_connection)
        controller.messagesChanged.connect(self._reset_idle)
        controller.permissionRequested.connect(self._on_permission)
        self._on_status(controller.status)
        self._on_connection(controller.connection)
        app = QApplication.instance()
        if app is not None:
            app.focusChanged.connect(self._on_focus_changed)

    # ---------------------------------------------------------------- sizes
    def _compute_sizes(self):
        s = self.cfg.scale
        self._compact_w = int(self.cfg.compact_width * s) + SHADOW * 2
        self._compact_h = int(46 * s) + SHADOW * 2
        area = work_area()
        self._expanded_w = min(int(self.cfg.expanded_width * s) + SHADOW * 2,
                               area.width() - 16)
        self._expanded_h = min(int(self.cfg.expanded_height * s) + SHADOW * 2,
                               area.height() - 16)

    def _custom_pos(self):
        return (self.cfg.pos_x, self.cfg.pos_y) if self.cfg.has_custom_position() else None

    def _compact_rect(self) -> QRect:
        x, y = anchor_origin(self.cfg.anchor, self._compact_w, self._compact_h,
                             self.cfg.y_offset, self._custom_pos())
        return QRect(x, y, self._compact_w, self._compact_h)

    def _expanded_rect(self) -> QRect:
        x, y = anchor_origin(self.cfg.anchor, self._expanded_w, self._expanded_h,
                             self.cfg.y_offset, self._custom_pos())
        return QRect(x, y, self._expanded_w, self._expanded_h)

    def _panel_rect(self) -> QRect:
        return self.rect().adjusted(SHADOW, SHADOW, -SHADOW, -SHADOW)

    def _position_contents(self):
        r = self._panel_rect()
        self._compact.setGeometry(r)
        self._expanded.setGeometry(r)

    # --------------------------------------------------------------- build
    def _build_compact(self):
        # Posisi "collapsed" meniru Dynamic Island versi Android: kapsul hitam
        # dengan mata putih Pochi di kiri + teks status berwarna di kanan.
        self._compact = QWidget(self)
        h = QHBoxLayout(self._compact)
        h.setContentsMargins(12, 0, 10, 0)
        h.setSpacing(6)

        self.mascot = PochiMascot(
            size=max(24, int(28 * self.cfg.scale)),
            status_provider=lambda: self.controller.status,
            reduce_motion=self.cfg.reduce_motion,
            show_backing=False)
        h.addWidget(self.mascot)
        h.addStretch(1)

        self.compact_label = QLabel("Pochi")
        self.compact_label.setStyleSheet(
            f"color:{theme.TEXT.name()}; font-size:11px; font-weight:600;")
        self.compact_label.setAttribute(
            Qt.WidgetAttribute.WA_TransparentForMouseEvents, True)
        h.addWidget(self.compact_label)

        self._compact.setCursor(Qt.CursorShape.SizeAllCursor)
        self.setCursor(Qt.CursorShape.SizeAllCursor)

    def _build_expanded(self):
        self._expanded = QWidget(self)
        v = QVBoxLayout(self._expanded)
        v.setContentsMargins(14, 12, 14, 12)
        v.setSpacing(8)

        # ---- header (drag handle)
        self.header = DragHandle(self)
        self.header.dragFinished.connect(self._on_header_dragged)
        h = QHBoxLayout(self.header)
        h.setContentsMargins(0, 0, 0, 0)
        h.setSpacing(9)
        self.status_dot_big = Dot(theme.MUTED, 9)
        h.addWidget(self.status_dot_big)
        titles = QVBoxLayout()
        titles.setSpacing(1)
        top = QHBoxLayout()
        top.setSpacing(7)
        name = QLabel("Pochi")
        name.setStyleSheet(
            f"color:{theme.TEXT.name()}; font-size:14px; font-weight:700;")
        name.setAttribute(Qt.WidgetAttribute.WA_TransparentForMouseEvents, True)
        top.addWidget(name)
        self.model_chip = QLabel("—")
        self.model_chip.setObjectName("pill")
        self.model_chip.setAttribute(Qt.WidgetAttribute.WA_TransparentForMouseEvents, True)
        top.addWidget(self.model_chip)
        top.addStretch(1)
        titles.addLayout(top)
        status_row = QHBoxLayout()
        status_row.setSpacing(5)
        self.status_dot = Dot(theme.MUTED, 6)
        status_row.addWidget(self.status_dot)
        self.status_label = QLabel("Idle")
        self.status_label.setStyleSheet(
            f"color:{theme.MUTED.name()}; font-size:10px; font-weight:500;")
        self.status_label.setAttribute(Qt.WidgetAttribute.WA_TransparentForMouseEvents, True)
        status_row.addWidget(self.status_label)
        self.status_detail = QLabel("Siap membantu")
        self.status_detail.setStyleSheet(
            f"color:{theme.DIM.name()}; font-size:10px;")
        self.status_detail.setAttribute(Qt.WidgetAttribute.WA_TransparentForMouseEvents, True)
        status_row.addWidget(self.status_detail)
        status_row.addStretch(1)
        titles.addLayout(status_row)
        h.addLayout(titles, 1)
        self.btn_dashboard = IconButton("dashboard", "Dashboard", 30, 16)
        self.btn_settings = IconButton("settings", "Pengaturan", 30, 16)
        self.btn_close = IconButton("chevron-up", "Tutup (Esc)", 30, 16)
        for b in (self.btn_dashboard, self.btn_settings, self.btn_close):
            h.addWidget(b)
        self.btn_dashboard.clicked.connect(self.dashboardRequested)
        self.btn_settings.clicked.connect(self.settingsRequested)
        self.btn_close.clicked.connect(lambda: self.collapse())
        v.addWidget(self.header)

        # ---- aktivitas kontekstual
        self.activity = ActivityArea(self.controller, self.media,
                                     self.cfg.reduce_motion)
        v.addWidget(self.activity)

        # ---- percakapan
        self.chat = ChatPanel(self.controller)
        self.chat.setMinimumHeight(150)
        self.chat.sendRequested.connect(self._send_text)
        v.addWidget(self.chat, 1)

        # ---- izin tool
        self.permission = QFrame()
        self.permission.setObjectName("banner")
        self.permission.setVisible(False)
        pv = QVBoxLayout(self.permission)
        pv.setContentsMargins(11, 8, 11, 8)
        pv.setSpacing(5)
        prow = QHBoxLayout()
        prow.setSpacing(7)
        prow.addWidget(icon_label("shield", theme.PURPLE, 14))
        self.perm_title = EllipsisLabel("Tool meminta izin")
        self.perm_title.setStyleSheet(
            f"color:{theme.TEXT.name()}; font-size:11px; font-weight:600;")
        prow.addWidget(self.perm_title, 1)
        pv.addLayout(prow)
        self.perm_detail = QLabel("")
        self.perm_detail.setWordWrap(True)
        self.perm_detail.setStyleSheet(
            f"color:{theme.MUTED.name()}; font-size:10px;")
        pv.addWidget(self.perm_detail)
        self.perm_buttons = QHBoxLayout()
        self.perm_buttons.setSpacing(6)
        pv.addLayout(self.perm_buttons)
        v.addWidget(self.permission)

        # ---- composer
        row = QHBoxLayout()
        row.setSpacing(7)
        self.input = QLineEdit()
        self.input.setObjectName("chatInput")
        self.input.setPlaceholderText("Tanya Pochi…")
        self.input.setClearButtonEnabled(False)
        self.input.returnPressed.connect(self._send)
        self.input.textChanged.connect(self._reset_idle)
        row.addWidget(self.input, 1)
        self.btn_mic = IconButton("mic", "Input suara butuh backend STT (belum tersedia)",
                                  32, 15)
        self.btn_mic.setEnabled(False)
        row.addWidget(self.btn_mic)
        self.btn_send = IconButton("send", "Kirim (Enter)", 38, 18,
                                   theme.BG, hover_color=theme.BG)
        self.btn_send.setObjectName("send")
        self.btn_send.clicked.connect(self._send)
        row.addWidget(self.btn_send)
        v.addLayout(row)

        # ---- footer
        foot = QHBoxLayout()
        foot.setSpacing(6)
        self.foot_dot = Dot(theme.AMBER, 6)
        foot.addWidget(self.foot_dot)
        self.foot_conn = EllipsisLabel("Menghubungkan…")
        self.foot_conn.setStyleSheet(
            f"color:{theme.MUTED.name()}; font-size:9px;")
        foot.addWidget(self.foot_conn, 1)
        self.foot_usage = QLabel("")
        self.foot_usage.setStyleSheet(
            f"color:{theme.DIM.name()}; font-size:9px;")
        foot.addWidget(self.foot_usage)
        hint = QLabel("Enter kirim · Esc tutup")
        hint.setStyleSheet(f"color:{theme.DIM.name()}; font-size:9px;")
        foot.addWidget(hint)
        v.addLayout(foot)

        self.controller.usageChanged.connect(self._on_usage)

    # ------------------------------------------------------------- shortcut
    def _setup_shortcut(self):
        try:
            if self._shortcut is not None:
                self._shortcut.setEnabled(False)
                self._shortcut.deleteLater()
            self._shortcut = QShortcut(QKeySequence(self.cfg.toggle_key), self)
            self._shortcut.setContext(Qt.ShortcutContext.ApplicationShortcut)
            self._shortcut.activated.connect(self.toggle)
        except Exception:
            self._shortcut = None
        try:
            if getattr(self, "_esc", None) is None:
                self._esc = QShortcut(QKeySequence("Esc"), self)
                self._esc.setContext(
                    Qt.ShortcutContext.WidgetWithChildrenShortcut)
                self._esc.activated.connect(self._escape)
        except Exception:
            self._esc = None

    def _escape(self):
        if self._mode == "expanded":
            self.collapse()

    # ---------------------------------------------------------------- mode
    @property
    def mode(self) -> str:
        return self._mode

    def toggle(self):
        if self._mode == "collapsed":
            self.expand()
        else:
            self.collapse()

    def expand(self, animate: bool = True):
        if self._mode == "expanded":
            return
        self._wake()
        self._mode = "expanded"
        self._compute_sizes()
        self._position_contents()
        self.setCursor(Qt.CursorShape.ArrowCursor)
        self._compact.setVisible(False)
        self._expanded.setVisible(True)
        self._expanded.raise_()
        self._expanded.setFocusPolicy(Qt.FocusPolicy.NoFocus)
        eff = QGraphicsOpacityEffect(self._expanded)
        self._expanded.setGraphicsEffect(eff)
        eff.setOpacity(0.0)
        self.raise_()
        try:
            self.setAttribute(Qt.WidgetAttribute.WA_ShowWithoutActivating, False)
            self.activateWindow()
        except Exception:
            pass
        self._expanded_at = time.monotonic()
        self._start_geo(self._expanded_rect(), animate)
        self._fade_anim(eff, 0.0, 1.0, 150, keep=False)
        QTimer.singleShot(self._duration(animate) + 40, self._focus_input)
        self.activity.start_polling()
        self.expandedChanged.emit(True)
        self._reset_idle()

    def collapse(self, animate: bool = True):
        if self._mode == "collapsed":
            return
        self._wake()
        self._mode = "collapsed"
        self._idle.stop()
        self.activity.stop_polling()
        # jadwalkan tidur (30 dtk idle, hanya saat collapsed & tidak sibuk)
        if not self.controller.busy and self._permission is None:
            secs = getattr(self.cfg, "sleep_after_seconds", 30)
            self._sleep_timer.start(int(max(5, secs) * 1000))
        self.setCursor(Qt.CursorShape.PointingHandCursor)
        eff = self._expanded.graphicsEffect()
        if eff is not None:
            self._fade_anim(eff, eff.opacity(), 0.0, 100, keep=True)
        self._start_geo(self._compact_rect(), animate)
        try:
            self.setAttribute(Qt.WidgetAttribute.WA_ShowWithoutActivating, True)
        except Exception:
            pass
        self.expandedChanged.emit(False)

    def _duration(self, animate: bool) -> int:
        if not animate or self.cfg.reduce_motion:
            return 0
        return 240

    def _start_geo(self, rect: QRect, animate: bool):
        if self._anim is not None:
            self._anim.stop()
        dur = self._duration(animate)
        if dur == 0:
            self.setGeometry(rect)
            self._pending = "collapse" if self._mode == "collapsed" else "expand"
            self._on_anim_done()
            return
        self._pending = "collapse" if self._mode == "collapsed" else "expand"
        self._anim.setDuration(dur)
        self._anim.setStartValue(self.geometry())
        self._anim.setEndValue(rect)
        self._anim.start()

    def _fade_anim(self, eff, start: float, end: float, dur: int, keep: bool):
        if not self.cfg.reduce_motion:
            anim = QPropertyAnimation(eff, b"opacity", self)
            anim.setDuration(dur)
            anim.setStartValue(start)
            anim.setEndValue(end)
            anim.start()
            self._fade = anim
        else:
            eff.setOpacity(end)

    def _on_anim_done(self):
        # Pastikan geometri akhir persis (animasi QRect bisa menyisakan 1 px).
        self.setGeometry(self._expanded_rect() if self._mode == "expanded"
                         else self._compact_rect())
        self._position_contents()
        if self._pending == "collapse":
            self._expanded.setGraphicsEffect(None)
            self._expanded.setVisible(False)
            self._compact.setVisible(True)
            self._compact.raise_()
            self.setCursor(Qt.CursorShape.PointingHandCursor)
        else:
            if self._expanded.graphicsEffect() is not None:
                self._expanded.graphicsEffect().setOpacity(1.0)
            self._expanded.setVisible(True)
            self._compact.setVisible(False)
        self._pending = ""

    def _focus_input(self):
        if self._mode == "expanded":
            self.input.setFocus(Qt.FocusReason.OtherFocusReason)

    # --------------------------------------------------------------- events
    def resizeEvent(self, e):
        super().resizeEvent(e)
        # Selama animasi geometri, anak-anak harus ikut melebar/menyempit
        # setiap frame agar panel tidak tampak kosong.
        self._position_contents()

    def keyPressEvent(self, e):
        if e.key() == Qt.Key.Key_Escape and self._mode == "expanded":
            self.collapse()
            return
        super().keyPressEvent(e)

    def mousePressEvent(self, e):
        if self._mode == "collapsed" and e.button() == Qt.MouseButton.LeftButton:
            self._drag = (e.globalPosition().toPoint(), self.pos())
            self._moved = False
            e.accept()
            return
        super().mousePressEvent(e)

    def mouseMoveEvent(self, e):
        if self._mode == "collapsed" and self._drag is not None:
            press, origin = self._drag
            delta = e.globalPosition().toPoint() - press
            if abs(delta.x()) + abs(delta.y()) > 4:
                self._moved = True
                area = work_area()
                nx = min(max(area.left(), origin.x() + delta.x()),
                         area.right() - self.width() + 1)
                ny = min(max(area.top(), origin.y() + delta.y()),
                         area.bottom() - self.height() + 1)
                self.move(nx, ny)
            e.accept()
            return
        super().mouseMoveEvent(e)

    def mouseReleaseEvent(self, e):
        if self._mode == "collapsed" and self._drag is not None:
            press, origin = self._drag
            delta = e.globalPosition().toPoint() - press
            self._drag = None
            if self._moved:
                self._save_position()
            elif abs(delta.x()) + abs(delta.y()) < 6:
                self.expand()
            e.accept()
            return
        super().mouseReleaseEvent(e)

    def contextMenuEvent(self, e):
        menu = QMenu(self)
        act = menu.addAction(make_icon("spark", theme.PURPLE, 15), "Buka Pochi")
        act.triggered.connect(self.toggle)
        d = menu.addAction(make_icon("dashboard", theme.MUTED, 15), "Dashboard")
        d.triggered.connect(self.dashboardRequested)
        menu.addSeparator()
        t = menu.addAction(make_icon("timer", theme.MUTED, 15), "Timer…")
        t.triggered.connect(self.timerRequested)
        s = menu.addAction(make_icon("settings", theme.MUTED, 15), "Pengaturan")
        s.triggered.connect(self.settingsRequested)
        menu.addSeparator()
        q = menu.addAction(make_icon("power", theme.RED, 15), "Keluar")
        q.triggered.connect(self.quitRequested)
        menu.exec(e.globalPos())

    def _on_header_dragged(self, x: int, y: int):
        self.cfg.set_position(x, y)
        self.cfg.save()

    def _save_position(self):
        self.cfg.set_position(self.x(), self.y())
        self.cfg.save()

    def _on_focus_changed(self, _old, new):
        if self._mode != "expanded":
            return
        if time.monotonic() - self._expanded_at < 0.5:
            return
        if not self._inside(new):
            self.collapse()

    def _inside(self, w) -> bool:
        while w is not None:
            if w is self:
                return True
            w = w.parentWidget()
        return False

    # --------------------------------------------------------------- status
    def _on_status(self, status: str):
        self._status = status
        color = Status.color(status)
        label = Status.LABELS.get(status, status)
        suffix = "…" if status in (Status.THINKING, Status.WORKING,
                                   Status.LISTENING) else ""
        self.status_label.setText(label + suffix)
        self.status_label.setStyleSheet(
            f"color:{color.name() if status != Status.IDLE else theme.MUTED.name()};"
            "font-size:10px; font-weight:500;")
        self.status_dot.set_color(color)
        self.status_dot_big.set_color(color)
        self.status_detail.setText(Status.DETAILS.get(status, ""))
        # label kapsul (gaya Android): "Pochi" saat idle, status saat sibuk
        self.compact_label.setText(
            "Pochi" if status == Status.IDLE else label + suffix)
        self.compact_label.setStyleSheet(
            f"color:{color.name()}; font-size:11px; font-weight:600;")
        busy = self.controller.busy
        self._set_busy(busy)
        if busy or self._permission is not None:
            self._idle.stop()
        else:
            self._reset_idle()
        if self._mode == "collapsed" or status in (Status.SUCCESS, Status.ERROR):
            self.update()

    def _set_busy(self, busy: bool):
        self.btn_send.set_icon_name("stop" if busy else "send",
                                    theme.TEXT if busy else theme.BG)
        self.btn_send.setToolTip("Hentikan" if busy else "Kirim (Enter)")
        self.btn_send.setProperty("busy", "true" if busy else "false")
        self.btn_send.style().unpolish(self.btn_send)
        self.btn_send.style().polish(self.btn_send)
        self.input.setPlaceholderText(
            "Pochi sedang bekerja…" if busy else "Tanya Pochi…")

    def _on_connection(self, conn):
        self._connection_ok = bool(conn.ok)
        detail = conn.detail or ""
        if conn.ok and conn.backend in ("hermes", "9router", "gateway"):
            color = theme.GREEN
        elif conn.backend == "demo":
            color = theme.AMBER
        elif conn.ok:
            color = theme.GREEN
        elif (conn.backend in ("starting", "auto")
              or "Mencoba" in detail or "beralih" in detail):
            color = theme.AMBER
        else:
            color = theme.RED
        self.foot_dot.set_color(color)
        text = conn.label or "Backend"
        if conn.backend == "demo":
            text = "Pochi"
        elif conn.model:
            text += f" · {conn.model}"
        self.foot_conn.setText(text)
        self.foot_conn.setToolTip(conn.detail or text)
        self.model_chip.setText(conn.model or self.cfg.model_name)
        self.model_chip.setToolTip(
            f"{conn.label}\n{conn.detail}\n{conn.model}".strip())
        self.update()

    def _on_usage(self, used: int, size: int):
        if size > 0:
            pct = min(999, int(used * 100 / size))
            self.foot_usage.setText(f"konteks {pct}%")
            self.foot_usage.setToolTip(f"{used:,} / {size:,} token")
        else:
            self.foot_usage.setText("")

    # ------------------------------------------------------------- permission
    def _on_permission(self, payload):
        self._permission = payload
        while self.perm_buttons.count():
            item = self.perm_buttons.takeAt(0)
            if item.widget():
                item.widget().deleteLater()
        if not payload:
            self.permission.setVisible(False)
            self._reset_idle()
            return
        self.perm_title.setText(str(payload.get("title") or "Tool meminta izin"))
        self.perm_detail.setText(str(payload.get("detail") or ""))
        options = list(payload.get("options") or [])
        has_reject = any("reject" in str(o.get("kind", "")) for o in options)
        for opt in options[:3]:
            label = str(opt.get("label") or "Izinkan")
            b = QPushButton(label)
            kind = str(opt.get("kind") or "")
            if "reject" in kind or "deny" in kind:
                b.setObjectName("danger")
            elif "allow" in kind:
                b.setObjectName("primary")
            b.clicked.connect(
                lambda _=False, oid=opt.get("id"): self.controller.respond_permission(oid))
            self.perm_buttons.addWidget(b)
        if not has_reject:
            b = QPushButton("Tolak")
            b.setObjectName("danger")
            b.clicked.connect(lambda: self.controller.respond_permission(None))
            self.perm_buttons.addWidget(b)
        self.perm_buttons.addStretch(1)
        self.permission.setVisible(True)
        self._idle.stop()

    # ------------------------------------------------------------------ chat
    def _send(self):
        if self.controller.busy:
            return
        text = self.input.text().strip()
        files = self.chat.take_draft()
        if not text and not files:
            return
        if self.controller.send(text, files):
            self.input.clear()

    def _send_text(self, text: str, files: list | tuple = ()):
        if self.controller.send(text, files):
            self._reset_idle()

    # -------------------------------------------------------------- lampiran
    def dragEnterEvent(self, e):
        if self._mode == "expanded" and e.mimeData().hasUrls():
            e.acceptProposedAction()

    def dragMoveEvent(self, e):
        if self._mode == "expanded" and e.mimeData().hasUrls():
            e.acceptProposedAction()

    def dropEvent(self, e):
        if self._mode != "expanded":
            return
        paths = [u.toLocalFile() for u in e.mimeData().urls()
                 if u.isLocalFile()]
        if paths:
            self.chat.add_files(paths)
            e.acceptProposedAction()

    # ---------------------------------------------------------------- idle
    def _go_sleep(self):
        if not self.mascot.sleeping():
            self.mascot.set_sleeping(True)

    def _wake(self):
        self._sleep_timer.stop()
        if self.mascot.sleeping():
            self.mascot.set_sleeping(False)

    def _reset_idle(self):
        self._wake()
        if (self._mode != "expanded" or self._permission is not None
                or self.controller.busy):
            self._idle.stop()
            return
        secs = self.cfg.auto_collapse_seconds
        if secs > 0:
            self._idle.start(int(secs * 1000))
        else:
            self._idle.stop()

    def _auto_collapse(self):
        if self._mode == "expanded" and not self.controller.busy \
                and self._permission is None:
            self.collapse()

    def _on_fullscreen(self, fs: bool) -> None:
        if fs == self._hidden_by_fullscreen:
            return
        self._hidden_by_fullscreen = fs
        if fs:
            self.hide()
            self._sleep_timer.stop()
        else:
            self.setVisible(True)
            self.show_without_activating()
            self.raise_()

    # -------------------------------------------------------------- config
    def apply_config(self):
        self.cfg.apply_clamps()
        self._compute_sizes()
        self._position_contents()
        self.activity.ai.dots.set_reduce_motion(self.cfg.reduce_motion)
        self.mascot.set_reduce_motion(self.cfg.reduce_motion)
        self._setup_shortcut()
        on_top = bool(self.cfg.always_on_top)
        if bool(self.windowFlags() & Qt.WindowType.WindowStaysOnTopHint) != on_top:
            self.setWindowFlag(Qt.WindowType.WindowStaysOnTopHint, on_top)
            self.show_without_activating()
        if self._mode == "expanded":
            self.setGeometry(self._expanded_rect())
        else:
            self.setGeometry(self._compact_rect())
        self._reset_idle()
        self.update()

    def present(self):
        self.show_without_activating()
        self.raise_()
        if self._mode != "expanded":
            self.expand()
        else:
            self._focus_input()

    def show_without_activating(self):
        self.setAttribute(Qt.WidgetAttribute.WA_ShowWithoutActivating, True)
        self.show()

    # --------------------------------------------------------------- paint
    def paintEvent(self, _):
        p = QPainter(self)
        p.setRenderHint(QPainter.RenderHint.Antialiasing, True)
        panel = QRectF(self.rect().adjusted(SHADOW, SHADOW, -SHADOW, -SHADOW))
        expanded = self._mode == "expanded"
        radius = 32.0 if expanded else panel.height() / 2.0

        # bayangan lembut agar island tampak mengambang
        p.setPen(Qt.PenStyle.NoPen)
        for i in range(3, 0, -1):
            p.setBrush(QColor(0, 0, 0, 8 + i * 4))
            r = panel.adjusted(-i, -i + 1.5, i, i + 1.5)
            p.drawRoundedRect(r, radius + i, radius + i)

        # badan island: hitam murni (ala iOS/Android)
        p.setBrush(QColor("#000000"))
        p.setPen(Qt.PenStyle.NoPen)
        p.drawRoundedRect(panel, radius, radius)

        # hairline border
        border = QColor("#26262B")
        if self._status in (Status.SUCCESS, Status.ERROR):
            border = Status.color(self._status)
            border.setAlpha(150)
        p.setBrush(Qt.BrushStyle.NoBrush)
        p.setPen(QPen(border, 1))
        p.drawRoundedRect(panel.adjusted(0.5, 0.5, -0.5, -0.5), radius, radius)

        # Kilau kaca + pantulan lensa (hanya kapsul, bukan panel expanded)
        if not expanded:
            path = QPainterPath()
            path.addRoundedRect(panel, radius, radius)
            p.save()
            p.setClipPath(path)
            gloss = QLinearGradient(0, panel.top(), 0,
                                    panel.top() + panel.height() * 0.58)
            gloss.setColorAt(0.0, QColor(255, 255, 255, 26))
            gloss.setColorAt(1.0, QColor(255, 255, 255, 0))
            p.fillRect(panel, gloss)
            lx = panel.left() + panel.width() * 0.16
            ly = panel.center().y()
            rr = min(panel.height() * 0.17, 5.0)
            spec = QRadialGradient(lx, ly - rr * 0.4, rr * 2.4)
            spec.setColorAt(0.0, QColor(255, 255, 255, 42))
            spec.setColorAt(0.55, QColor(255, 255, 255, 0))
            p.setBrush(spec)
            p.setPen(Qt.PenStyle.NoPen)
            p.drawEllipse(QRectF(lx - rr * 2.4, ly - rr * 2.4,
                                  rr * 4.8, rr * 4.8))
            p.restore()

        p.end()
