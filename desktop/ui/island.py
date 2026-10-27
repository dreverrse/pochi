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
    QLineEdit, QMenu, QPushButton, QStackedWidget, QVBoxLayout, QWidget,
)

from app import theme
from app.state import Status
from platform_x11.x11 import anchor_origin, work_area
from services.fullscreen import FullscreenService
from ui.activity import ActivityArea, AgendaCard
from ui.chat import ChatPanel
from ui.components import Dot, EllipsisLabel, IconButton, Pill, icon_label
from ui.icons import make_icon
from ui.mascot import SLEEP_AFTER, PochiMascot

SHADOW = 6
COMPACT_SIZE = (236, 42)
RINGKAS_SIZE = (560, 300)
CHAT_SIZE = (430, 620)
# Ukuran minimum isi panel (skala 0.75 dulu membuat layout terhimpit).
MIN_EXPANDED = (440, 300)


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
        self._page = "ringkas"
        self._draft_text = ""
        self._drag: tuple[QPoint, QPoint] | None = None
        self._moved = False
        self._expanded_at = 0.0
        self._anim: QPropertyAnimation | None = None
        self._fade: QPropertyAnimation | None = None
        self._pending = ""
        self._status = Status.IDLE
        self._error_count = 0
        self._connection_ok = False
        self._permission = None
        self._shortcut = None

        self._compute_sizes()
        self._build_compact()
        self._build_expanded()
        self._position_contents()
        # Overhead tetap di luar halaman (header, footer, margin, spacing).
        # Dipakai untuk menghitung minimum per halaman, bukan gabungan stack.
        lay = self._expanded.layout()
        if lay is not None:
            lay.activate()
        expanded_min = self._expanded.minimumSizeHint()
        stack_min = self.stack.minimumSizeHint()
        self._chrome_w = max(0, expanded_min.width() - stack_min.width())
        self._chrome_h = max(0, expanded_min.height() - stack_min.height())
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
        self._compact_w = int(max(COMPACT_SIZE[0], self.cfg.compact_width) * s) + SHADOW * 2
        self._compact_h = int(COMPACT_SIZE[1] * s) + SHADOW * 2
        area = work_area()
        min_rw, min_rh = MIN_EXPANDED
        min_cw, min_ch = MIN_EXPANDED
        if hasattr(self, "_chrome_h"):
            page = self.chat_page if self._page == "chat" else self.ringkas_page
            hint = page.minimumSizeHint()
            need_w = hint.width() + self._chrome_w + SHADOW * 2 + 8
            need_h = hint.height() + self._chrome_h + SHADOW * 2 + 8
            # Hint per halaman: panel lirik hanya menambah tinggi Chat.
            if self._page == "chat":
                min_cw, min_ch = max(min_cw, need_w), max(min_ch, need_h)
            else:
                min_rw, min_rh = max(min_rw, need_w), max(min_rh, need_h)
        limit_w = max(min_rw, min_cw, area.width() - 16)
        limit_h = max(min_rh, min_ch, area.height() - 16)
        self._ringkas_w = min(max(int(RINGKAS_SIZE[0] * s) + SHADOW * 2, min_rw),
                              limit_w)
        self._ringkas_h = min(max(int(RINGKAS_SIZE[1] * s) + SHADOW * 2, min_rh),
                              limit_h)
        self._chat_w = min(max(int(CHAT_SIZE[0] * s) + SHADOW * 2, min_cw),
                           limit_w)
        self._chat_h = min(max(int(CHAT_SIZE[1] * s) + SHADOW * 2, min_ch),
                           limit_h)
        # Alias lama untuk smoke test/dashboard.
        self._expanded_w = self._ringkas_w
        self._expanded_h = self._ringkas_h

    def _custom_pos(self):
        return (self.cfg.pos_x, self.cfg.pos_y) if self.cfg.has_custom_position() else None

    def _compact_rect(self) -> QRect:
        x, y = anchor_origin(self.cfg.anchor, self._compact_w, self._compact_h,
                             self.cfg.y_offset, self._custom_pos())
        return QRect(x, y, self._compact_w, self._compact_h)

    def _ringkas_rect(self) -> QRect:
        x, y = anchor_origin(self.cfg.anchor, self._ringkas_w, self._ringkas_h,
                             self.cfg.y_offset, self._custom_pos())
        return QRect(x, y, self._ringkas_w, self._ringkas_h)

    def _chat_rect(self) -> QRect:
        x, y = anchor_origin(self.cfg.anchor, self._chat_w, self._chat_h,
                             self.cfg.y_offset, self._custom_pos())
        return QRect(x, y, self._chat_w, self._chat_h)

    def _expanded_rect(self) -> QRect:
        return self._chat_rect() if self._page == "chat" else self._ringkas_rect()

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
            status_provider=lambda: self._status,
            reduce_motion=self.cfg.reduce_motion,
            show_backing=False)
        self.compact_mascot = self.mascot
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
        v.setContentsMargins(12, 10, 12, 10)
        v.setSpacing(7)

        # ---- header (drag handle)
        self.header = DragHandle(self)
        self.header.dragFinished.connect(self._on_header_dragged)
        h = QHBoxLayout(self.header)
        h.setContentsMargins(0, 0, 0, 0)
        h.setSpacing(7)

        self.header_mascot = PochiMascot(
            size=max(20, int(26 * self.cfg.scale)),
            status_provider=lambda: self._status,
            reduce_motion=self.cfg.reduce_motion,
            show_backing=True, interactive=True)
        h.addWidget(self.header_mascot)

        titles = QVBoxLayout()
        titles.setSpacing(0)
        name_row = QHBoxLayout()
        name_row.setSpacing(6)
        name = QLabel("Pochi")
        name.setStyleSheet(
            f"color:{theme.TEXT.name()}; font-size:13px; font-weight:700;")
        name.setAttribute(Qt.WidgetAttribute.WA_TransparentForMouseEvents, True)
        name_row.addWidget(name)
        self.model_chip = Pill("—", "violet")
        self.model_chip.setMaximumWidth(140)
        self.model_chip.setAttribute(
            Qt.WidgetAttribute.WA_TransparentForMouseEvents, True)
        name_row.addWidget(self.model_chip)
        name_row.addStretch(1)
        titles.addLayout(name_row)
        status_row = QHBoxLayout()
        status_row.setSpacing(5)
        self.status_dot = Dot(theme.MUTED, 6)
        status_row.addWidget(self.status_dot)
        self.status_label = QLabel("Idle")
        self.status_label.setStyleSheet(
            f"color:{theme.MUTED.name()}; font-size:9px; font-weight:500;")
        self.status_label.setAttribute(
            Qt.WidgetAttribute.WA_TransparentForMouseEvents, True)
        status_row.addWidget(self.status_label)
        self.status_detail = QLabel("Siap membantu")
        self.status_detail.setStyleSheet(
            f"color:{theme.DIM.name()}; font-size:9px;")
        self.status_detail.setAttribute(
            Qt.WidgetAttribute.WA_TransparentForMouseEvents, True)
        status_row.addWidget(self.status_detail)
        status_row.addStretch(1)
        titles.addLayout(status_row)
        h.addLayout(titles, 1)

        self.ringkas_tab = QPushButton("Ringkas")
        self.chat_tab = QPushButton("Chat")
        for b in (self.ringkas_tab, self.chat_tab):
            b.setObjectName("segment")
            b.setCheckable(True)
            b.setCursor(Qt.CursorShape.PointingHandCursor)
            h.addWidget(b)
        self.ringkas_tab.setChecked(True)
        self.ringkas_tab.clicked.connect(lambda: self.show_ringkas())
        self.chat_tab.clicked.connect(lambda: self.show_chat(focus=True))

        self.btn_dashboard = IconButton("dashboard", "Dashboard", 28, 15)
        self.btn_settings = IconButton("settings", "Pengaturan", 28, 15)
        self.btn_close = IconButton("chevron-up", "Tutup (Esc)", 28, 15)
        for b in (self.btn_dashboard, self.btn_settings, self.btn_close):
            h.addWidget(b)
        self.btn_dashboard.clicked.connect(self.dashboardRequested)
        self.btn_settings.clicked.connect(self.settingsRequested)
        self.btn_close.clicked.connect(lambda: self.collapse())
        v.addWidget(self.header)

        self.stack = QStackedWidget()
        v.addWidget(self.stack, 1)

        # ---- aktivitas (satu polling untuk media, lirik, AI, timer)
        # Parent eksplisit: anak-anaknya dipindah ke layout halaman, jadi
        # ActivityArea sendiri tidak boleh menjadi top-level window yatim.
        self.activity = ActivityArea(
            self.controller, self.media, self.cfg.reduce_motion,
            on_needs_chat=lambda: self.show_chat(focus=False),
            on_layout_changed=self._refit,
            parent=self._expanded)
        self.music_card = self.activity.media_card
        self.lyrics_panel = self.activity.lyrics_panel
        self.ai_card = self.activity.ai
        self.timer_card = self.activity.timer

        # ---- halaman Ringkas
        self.ringkas_page = QWidget()
        rl = QVBoxLayout(self.ringkas_page)
        rl.setContentsMargins(0, 0, 0, 0)
        rl.setSpacing(8)
        cards = QHBoxLayout()
        cards.setSpacing(8)
        self.agenda_card = AgendaCard(self.controller)
        cards.addWidget(self.music_card, 3)
        cards.addWidget(self.agenda_card, 2)
        rl.addLayout(cards, 1)

        self.ask_bar = QPushButton("Tanya Pochi…")
        self.ask_bar.setObjectName("askBar")
        self.ask_bar.setCursor(Qt.CursorShape.PointingHandCursor)
        self.ask_bar.setFixedHeight(38)
        self.ask_bar.setIcon(make_icon("spark", theme.VIOLET_LIGHT, 15))
        self.ask_bar.setStyleSheet(
            f"QPushButton#askBar {{"
            f"background:{theme.CARD_TOP.name()};"
            f"border:1px solid {theme.BORDER.name()};"
            "border-radius:13px;"
            f"color:{theme.MUTED.name()};"
            "text-align:left; padding:0 14px; font-size:12px;"
            "}"
            f"QPushButton#askBar:hover {{"
            f"border-color:{theme.VIOLET.name()};"
            f"color:{theme.TEXT.name()};"
            "}")
        self.ask_bar.clicked.connect(lambda: self.show_chat(focus=True))
        rl.addWidget(self.ask_bar)
        self.stack.addWidget(self.ringkas_page)

        # ---- halaman Chat
        self.chat_page = QWidget()
        cl = QVBoxLayout(self.chat_page)
        cl.setContentsMargins(0, 0, 0, 0)
        cl.setSpacing(7)
        cl.addWidget(self.ai_card)
        cl.addWidget(self.timer_card)
        cl.addWidget(self.lyrics_panel)

        self.chat = ChatPanel(self.controller)
        # Jangan paksa tinggi lebih kecil dari minimum asli panel: empty state
        # (maskot + judul + chip) akan saling tumpang tindih saat ruang sempit.
        self.chat.setMinimumHeight(self.chat.minimumSizeHint().height())
        self.chat.sendRequested.connect(self._send_text)
        self.chat_mascot = self.chat.welcome_mascot
        # ChatPanel dibuat setelah cfg dibaca; samakan preferensi gerak.
        self.chat_mascot.set_reduce_motion(self.cfg.reduce_motion)
        cl.addWidget(self.chat, 1)

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
        cl.addWidget(self.permission)

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
        cl.addLayout(row)
        self.stack.addWidget(self.chat_page)
        self.stack.setCurrentIndex(0)

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
        hint = QLabel("Esc · tutup")
        hint.setStyleSheet(f"color:{theme.DIM.name()}; font-size:9px;")
        self.footer_hint = hint
        foot.addWidget(hint)
        v.addLayout(foot)

        self.status_dot_big = self.status_dot
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
        if self._mode != "expanded":
            return
        if self._permission is not None:
            # Jangan pernah menyembunyikan permintaan izin yang belum dijawab.
            self.show_chat(focus=False)
            return
        if self._page == "chat":
            self.show_ringkas()
        else:
            self.collapse()

    # ---------------------------------------------------------------- mode
    @property
    def mode(self) -> str:
        return self._mode

    @property
    def page(self) -> str:
        return self._page

    def _sync_tabs(self):
        self.ringkas_tab.setChecked(self._page == "ringkas")
        self.chat_tab.setChecked(self._page == "chat")
        if hasattr(self, "footer_hint"):
            self.footer_hint.setText(
                "Esc · Ringkas" if self._page == "chat" else "Esc · tutup")

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
        self._page = "ringkas"
        self.stack.setCurrentIndex(0)
        self._sync_tabs()
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
            # Ringkas tidak merebut fokus keyboard aplikasi lain.
            self.setAttribute(Qt.WidgetAttribute.WA_ShowWithoutActivating, True)
        except Exception:
            pass
        self._expanded_at = time.monotonic()
        self._start_geo(self._ringkas_rect(), animate)
        self._fade_anim(eff, 0.0, 1.0, 150, keep=False)
        self.activity.start_polling()
        self.expandedChanged.emit(True)
        self._reset_idle()

    def _refit(self) -> None:
        """Sesuaikan ukuran panel setelah isi berubah (mis. panel lirik)."""
        if self._mode != "expanded":
            return
        lay = self._expanded.layout()
        if lay is not None:
            lay.activate()
        self._compute_sizes()
        self._position_contents()
        if self._anim is not None:
            self._anim.stop()
        self.setGeometry(self._expanded_rect())
        self._reset_idle()

    def show_ringkas(self, animate: bool = True):
        if self._permission is not None:
            # Banner izin hanya ada di halaman Chat.
            self.show_chat(focus=False, animate=animate)
            return
        if self._mode != "expanded":
            self.expand(animate)
            return
        self._draft_text = self.input.text()
        self._page = "ringkas"
        self.stack.setCurrentIndex(0)
        self._sync_tabs()
        self._compute_sizes()
        self._position_contents()
        self._start_geo(self._ringkas_rect(), animate)
        self._reset_idle()

    def show_chat(self, focus: bool = True, animate: bool = True):
        if self._mode != "expanded":
            self.expand(animate)
        self._page = "chat"
        self.stack.setCurrentIndex(1)
        self._sync_tabs()
        if not self.input.text() and self._draft_text:
            self.input.setText(self._draft_text)
        self._compute_sizes()
        self._position_contents()
        self._start_geo(self._chat_rect(), animate)
        if focus:
            delay = 0 if self._duration(animate) == 0 else self._duration(animate) + 40
            QTimer.singleShot(delay, self._focus_input)
        self._reset_idle()

    def collapse(self, animate: bool = True):
        if self._permission is not None:
            self.show_chat(focus=False, animate=animate)
            return
        if self._mode == "collapsed":
            return
        self._wake()
        self._draft_text = self.input.text()
        self._mode = "collapsed"
        self._idle.stop()
        self.activity.stop_polling()
        # maskot tidur setelah 90 dtk idle (hanya saat collapsed & tidak sibuk)
        if not self.controller.busy and self._permission is None:
            self._sleep_timer.start(int(SLEEP_AFTER * 1000))
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
        if self._mode == "expanded" and self._page == "chat":
            self.input.setFocus(Qt.FocusReason.OtherFocusReason)

    # --------------------------------------------------------------- events
    def resizeEvent(self, e):
        super().resizeEvent(e)
        # Selama animasi geometri, anak-anak harus ikut melebar/menyempit
        # setiap frame agar panel tidak tampak kosong.
        self._position_contents()

    def keyPressEvent(self, e):
        if e.key() == Qt.Key.Key_Escape and self._mode == "expanded":
            self._escape()
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
        if self._permission is not None:
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
        if status == Status.ERROR:
            self._error_count += 1
        elif status in (Status.IDLE, Status.SUCCESS):
            self._error_count = 0
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
        for mascot in (self.compact_mascot, self.header_mascot,
                       self.chat_mascot):
            mascot.set_status(status)
            if status == Status.ERROR:
                if self._error_count >= 3:
                    mascot.set_emote("annoyed")
                elif self._error_count == 2:
                    mascot.set_emote("crying")
        busy = self.controller.busy
        self._set_busy(busy)
        if busy or self._permission is not None:
            self._idle.stop()
        else:
            self._reset_idle()
        if self._mode == "collapsed" or status in (Status.SUCCESS, Status.ERROR):
            self.update()

    def _set_busy(self, busy: bool):
        if not hasattr(self, "btn_send"):
            return
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
        if conn.ok and conn.backend in ("hermes", "9router"):
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
        model = conn.model or self.cfg.model_name
        short = model.split("/")[-1]
        if len(short) > 18:
            short = short[:17] + "…"
        self.model_chip.setText(short)
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
    def _set_permission_lock(self, locked: bool) -> None:
        self.ringkas_tab.setEnabled(not locked)
        self.btn_close.setEnabled(not locked)
        self.btn_close.setToolTip(
            "Selesaikan izin dulu" if locked else "Tutup (Esc)")

    def _on_permission(self, payload):
        self._permission = payload
        while self.perm_buttons.count():
            item = self.perm_buttons.takeAt(0)
            if item.widget():
                item.widget().deleteLater()
        if not payload:
            self._permission = None
            self.permission.setVisible(False)
            self._set_permission_lock(False)
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
        self._set_permission_lock(True)
        self._idle.stop()
        self.show_chat(focus=False)

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
            # Jangan biarkan draft lama muncul lagi saat Chat dibuka ulang.
            self._draft_text = ""

    def _send_text(self, text: str, files: list | tuple = ()):
        if self.controller.send(text, files):
            self._draft_text = ""
            self._reset_idle()

    # -------------------------------------------------------------- lampiran
    def dragEnterEvent(self, e):
        if self._mode == "expanded" and e.mimeData().hasUrls():
            e.acceptProposedAction()

    def dragMoveEvent(self, e):
        if self._mode == "expanded" and e.mimeData().hasUrls():
            e.acceptProposedAction()

    def _stage_files(self, paths) -> None:
        if not paths:
            return
        self.chat.add_files(paths)
        if self._page != "chat":
            # Lampiran selalu masuk ke composer Chat, bukan halaman Ringkas.
            self.show_chat(focus=False)

    def dropEvent(self, e):
        if self._mode != "expanded":
            return
        paths = [u.toLocalFile() for u in e.mimeData().urls()
                 if u.isLocalFile()]
        if paths:
            self._stage_files(paths)
            e.acceptProposedAction()

    # ---------------------------------------------------------------- idle
    def _go_sleep(self):
        for mascot in (self.mascot, self.header_mascot):
            if not mascot.sleeping():
                mascot.set_sleeping(True)

    def _wake(self):
        self._sleep_timer.stop()
        for mascot in (self.mascot, self.header_mascot):
            if mascot.sleeping():
                mascot.set_sleeping(False)

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
        for mascot in (self.mascot, self.header_mascot, self.chat_mascot):
            mascot.set_reduce_motion(self.cfg.reduce_motion)
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

    def show_without_activating(self):
        self.setAttribute(Qt.WidgetAttribute.WA_ShowWithoutActivating, True)
        self.show()

    # --------------------------------------------------------------- paint
    def paintEvent(self, _):
        p = QPainter(self)
        p.setRenderHint(QPainter.RenderHint.Antialiasing, True)
        panel = QRectF(self.rect().adjusted(SHADOW, SHADOW, -SHADOW, -SHADOW))
        expanded = self._mode == "expanded"
        radius = 30.0 if expanded else panel.height() / 2.0

        # bayangan lembut agar island tampak mengambang
        p.setPen(Qt.PenStyle.NoPen)
        for i in range(3, 0, -1):
            p.setBrush(QColor(0, 0, 0, 10 + i * 5))
            r = panel.adjusted(-i, -i + 1.5, i, i + 1.5)
            p.drawRoundedRect(r, radius + i, radius + i)

        # badan island: hitam keunguan (identitas baru)
        p.setBrush(QColor(theme.PANEL))
        p.setPen(Qt.PenStyle.NoPen)
        p.drawRoundedRect(panel, radius, radius)

        # rim ungu tipis agar terbaca di wallpaper terang maupun gelap
        if expanded:
            rim = QColor(theme.VIOLET)
            rim.setAlpha(70)
            p.setBrush(Qt.BrushStyle.NoBrush)
            p.setPen(QPen(rim, 1.6))
            p.drawRoundedRect(panel.adjusted(0.8, 0.8, -0.8, -0.8),
                              radius - 0.8, radius - 0.8)

        # hairline border
        border = QColor(theme.BORDER)
        if self._status in (Status.SUCCESS, Status.ERROR):
            border = Status.color(self._status)
            border.setAlpha(170)
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
