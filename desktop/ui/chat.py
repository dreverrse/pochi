"""Panel percakapan Pochi — bubble, streaming, error, dan empty state."""
from __future__ import annotations

import time

from PyQt6.QtCore import QFileInfo, QPoint, Qt, QTimer, pyqtSignal
from PyQt6.QtGui import QGuiApplication, QImage, QPixmap
from PyQt6.QtWidgets import (
    QFrame, QHBoxLayout, QLabel, QMenu, QPushButton, QScrollArea,
    QSizePolicy, QStackedWidget, QVBoxLayout, QWidget,
)

from app import theme
from app.state import ChatMessage, Status
from ui.components import Divider, EllipsisLabel, IconButton
from ui.icons import pochi_face_pixmap
from ui.mascot import PochiMascot

_IMG = ("png", "jpg", "jpeg", "gif", "bmp", "webp", "svg", "ico")


def _thumb(path: str, size: int = 72) -> QPixmap | None:
    if QFileInfo(path).suffix().lower() in _IMG:
        img = QImage(path)
        if not img.isNull():
            return QPixmap.fromImage(img).scaled(
                size, size, Qt.AspectRatioMode.KeepAspectRatio,
                Qt.TransformationMode.SmoothTransformation)
    return None


def _time_label(ts: float) -> str:
    try:
        return time.strftime("%H:%M", time.localtime(ts))
    except Exception:
        return ""


class MessageBubble(QFrame):
    def __init__(self, msg: ChatMessage, parent=None):
        super().__init__(parent)
        self.msg = msg
        role = "error" if msg.error else ("user" if msg.role == "user" else "assistant")
        self.setObjectName({
            "error": "bubbleError",
            "user": "bubbleUser",
            "assistant": "bubbleAssistant",
        }[role])
        self.setMaximumWidth(316)
        v = QVBoxLayout(self)
        v.setContentsMargins(11, 8, 11, 7)
        v.setSpacing(3)
        self.text = QLabel(msg.text)
        self.text.setWordWrap(True)
        self.text.setTextInteractionFlags(
            Qt.TextInteractionFlag.TextSelectableByMouse
            | Qt.TextInteractionFlag.LinksAccessibleByMouse)
        self.text.setStyleSheet(
            f"color:{'#FDA4AF' if msg.error else theme.TEXT.name()};"
            "font-size:12px; line-height:1.35;")
        self.text.setSizePolicy(QSizePolicy.Policy.Preferred,
                                QSizePolicy.Policy.Minimum)
        v.addWidget(self.text)
        if msg.files:
            fl = QHBoxLayout()
            fl.setContentsMargins(0, 0, 0, 0)
            fl.setSpacing(6)
            for path in list(msg.files)[:4]:
                t = _thumb(path, 60)
                lbl = QLabel()
                if t is not None:
                    lbl.setPixmap(t)
                    lbl.setFixedSize(min(60, t.width()), min(60, t.height()))
                else:
                    lbl.setText(QFileInfo(path).fileName() or path)
                    lbl.setStyleSheet(
                        f"color:{theme.TEXT.name()}; background:{theme.PANEL_2.name()};"
                        "border-radius:6px; padding:5px 9px; font-size:10px;")
                fl.addWidget(lbl)
            if len(msg.files) > 4:
                more = QLabel(f"+{len(msg.files) - 4}")
                more.setStyleSheet(
                    f"color:{theme.DIM.name()}; font-size:10px;")
                fl.addWidget(more)
            v.insertLayout(0, fl)
        self.meta = QLabel(
            f"{'ERROR · ' if msg.error else ''}{_time_label(msg.when)}")
        self.meta.setStyleSheet(
            f"color:{theme.DIM.name()}; font-size:9px; font-weight:600;"
            "letter-spacing:0.4px;")
        v.addWidget(self.meta)
        self.setContextMenuPolicy(Qt.ContextMenuPolicy.CustomContextMenu)
        self.customContextMenuRequested.connect(self._menu)

    def set_text(self, text: str, streaming: bool) -> None:
        self.msg.text = text
        self.text.setText(text + ("  ▌" if streaming else ""))

    def _menu(self, pos: QPoint):
        menu = QMenu(self)
        act = menu.addAction("Salin teks")
        act.triggered.connect(
            lambda: QGuiApplication.clipboard().setText(self.msg.text))
        menu.exec(self.mapToGlobal(pos))


class AttachmentChip(QFrame):
    removed = pyqtSignal(str)

    def __init__(self, path: str, thumb: QPixmap | None = None, parent=None):
        super().__init__(parent)
        self._path = path
        self.setObjectName("chip")
        h = QHBoxLayout(self)
        h.setContentsMargins(6, 4, 4, 4)
        h.setSpacing(5)
        if thumb is not None:
            ic = QLabel()
            ic.setPixmap(thumb.scaled(
                26, 26, Qt.AspectRatioMode.KeepAspectRatio,
                Qt.TransformationMode.SmoothTransformation))
            ic.setFixedSize(26, 26)
        else:
            ic = IconButton("file", "File", 24, 12)
            ic.setEnabled(False)
        h.addWidget(ic)
        name = QLabel(QFileInfo(path).fileName() or path)
        name.setStyleSheet(
            f"color:{theme.TEXT.name()}; font-size:10px;")
        name.setMaximumWidth(130)
        name.setToolTip(path)
        h.addWidget(name)
        close = IconButton("close", "Buang", 18, 9)
        close.clicked.connect(lambda: self.removed.emit(self._path))
        h.addWidget(close)
        self.setMaximumHeight(34)


class ChatPanel(QWidget):
    sendRequested = pyqtSignal(str, list)

    def __init__(self, controller, parent=None):
        super().__init__(parent)
        self.controller = controller
        self._bubbles: dict[int, MessageBubble] = {}
        self._ids: list[int] = []
        self._pending_scroll = False
        self._draft: list[tuple[str, QPixmap | None]] = []
        self._draft_chips: dict[str, AttachmentChip] = {}
        self.setAcceptDrops(True)
        self._build()
        controller.messagesChanged.connect(self._rebuild)
        controller.streamChanged.connect(self._on_stream)
        self._rebuild()

    # -------------------------------------------------------------- build
    def _build(self):
        v = QVBoxLayout(self)
        v.setContentsMargins(0, 0, 0, 0)
        v.setSpacing(0)
        self.stack = QStackedWidget()
        v.addWidget(self.stack)

        # --- baris lampiran (drag & drop)
        self.draft_row = QWidget()
        self.draft_row.setVisible(False)
        self.draft_layout = QHBoxLayout(self.draft_row)
        self.draft_layout.setContentsMargins(6, 2, 6, 0)
        self.draft_layout.setSpacing(6)
        self.draft_layout.addStretch(1)
        v.addWidget(self.draft_row)

        # --- empty state
        welcome = QWidget()
        wl = QVBoxLayout(welcome)
        wl.setContentsMargins(18, 18, 18, 18)
        wl.setSpacing(8)
        wl.addStretch(1)
        self.welcome_mascot = PochiMascot(size=86, show_backing=True,
                                          interactive=True)
        wl.addWidget(self.welcome_mascot, 0, Qt.AlignmentFlag.AlignHCenter)
        self.welcome_title = QLabel("Halo, aku Pochi.")
        self.welcome_title.setStyleSheet(
            f"color:{theme.TEXT.name()}; font-size:18px; font-weight:700;")
        self.welcome_title.setAlignment(Qt.AlignmentFlag.AlignHCenter)
        wl.addWidget(self.welcome_title)
        self.welcome_sub = QLabel(
            "Asisten desktop pribadi. Tanya apa saja, atau minta aku "
            "mengerjakan sesuatu.")
        self.welcome_sub.setWordWrap(True)
        self.welcome_sub.setAlignment(Qt.AlignmentFlag.AlignHCenter)
        self.welcome_sub.setStyleSheet(
            f"color:{theme.MUTED.name()}; font-size:10px; line-height:1.5;")
        wl.addWidget(self.welcome_sub)
        chips = QHBoxLayout()
        chips.setSpacing(6)
        chips.addStretch(1)
        for text, prompt in (
            ("Bisa apa saja?", "Apa yang bisa kamu bantu di desktop ini?"),
            ("Cek sistem", "Ringkas kondisi sistem komputer ini."),
        ):
            b = QPushButton(text)
            b.setObjectName("chip")
            b.setCursor(Qt.CursorShape.PointingHandCursor)
            b.clicked.connect(
                lambda _=False, p=prompt: self.sendRequested.emit(p, []))
            chips.addWidget(b)
        chips.addStretch(1)
        wl.addLayout(chips)
        wl.addStretch(1)
        self.stack.addWidget(welcome)

        # --- message list
        self.scroll = QScrollArea()
        self.scroll.setWidgetResizable(True)
        self.scroll.setHorizontalScrollBarPolicy(
            Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        self.scroll.setFrameShape(QFrame.Shape.NoFrame)
        self.list = QWidget()
        self.list_layout = QVBoxLayout(self.list)
        self.list_layout.setContentsMargins(2, 2, 4, 2)
        self.list_layout.setSpacing(8)
        self.list_layout.addStretch(1)
        self.scroll.setWidget(self.list)
        self.stack.addWidget(self.scroll)
        self.stack.setCurrentIndex(0)

    # -------------------------------------------------------------- render
    def _rebuild(self):
        msgs = self.controller.messages
        self.stack.setCurrentIndex(1 if msgs else 0)
        new_ids = [id(m) for m in msgs]
        if new_ids != self._ids:
            self._ids = new_ids
            self._bubbles.clear()
            while self.list_layout.count() > 1:
                item = self.list_layout.takeAt(0)
                if item.widget():
                    item.widget().deleteLater()
                elif item.layout():
                    # hapus layout pembungkus
                    while item.layout().count():
                        c = item.layout().takeAt(0)
                        if c.widget():
                            c.widget().deleteLater()
            for m in msgs[-80:]:
                self._add_bubble(m)
        else:
            # perbarui bubble yang sudah ada (streaming selesai, meta final)
            for m in msgs[-80:]:
                b = self._bubbles.get(id(m))
                if b is not None:
                    b.set_text(m.text, m.streaming)
                    b.meta.setText(
                        f"{'ERROR · ' if m.error else ''}{_time_label(m.when)}")
        self.scroll_to_bottom()

    def _add_bubble(self, msg: ChatMessage):
        bubble = MessageBubble(msg)
        row = QHBoxLayout()
        row.setContentsMargins(0, 0, 0, 0)
        if msg.role == "user":
            row.addStretch(1)
            row.addWidget(bubble)
        else:
            avatar = QLabel()
            avatar.setPixmap(pochi_face_pixmap(22))
            avatar.setFixedSize(22, 22)
            avatar.setStyleSheet("background:transparent;")
            row.addWidget(avatar, 0, Qt.AlignmentFlag.AlignTop)
            row.addSpacing(6)
            row.addWidget(bubble)
            row.addStretch(1)
        holder = QWidget()
        holder.setLayout(row)
        self.list_layout.insertWidget(self.list_layout.count() - 1, holder)
        self._bubbles[id(msg)] = bubble

    def _on_stream(self):
        msgs = self.controller.messages
        if not msgs:
            return
        last = msgs[-1]
        bubble = self._bubbles.get(id(last))
        if bubble is None or bubble.msg is not last:
            self._rebuild()
        else:
            bubble.set_text(last.text, last.streaming)
        self._schedule_scroll()

    def _at_bottom(self) -> bool:
        bar = self.scroll.verticalScrollBar()
        return bar.value() >= bar.maximum() - 28

    def scroll_to_bottom(self):
        QTimer.singleShot(20, self._do_scroll)

    def _schedule_scroll(self):
        if self._at_bottom() and not self._pending_scroll:
            self._pending_scroll = True
            QTimer.singleShot(30, self._do_scroll)

    def _do_scroll(self):
        self._pending_scroll = False
        bar = self.scroll.verticalScrollBar()
        bar.setValue(bar.maximum())

    # -------------------------------------------------------------- lampiran
    def add_files(self, paths) -> None:
        for p in paths:
            if not p or p in self._draft_chips:
                continue
            thumb = _thumb(p)
            self._draft.append((p, thumb))
            chip = AttachmentChip(p, thumb)
            chip.removed.connect(self._remove_file)
            self._draft_chips[p] = chip
            self.draft_layout.insertWidget(self.draft_layout.count() - 1, chip)
        self.draft_row.setVisible(bool(self._draft))

    def _remove_file(self, path: str) -> None:
        chip = self._draft_chips.pop(path, None)
        if chip is not None:
            chip.deleteLater()
        self._draft = [(p, t) for p, t in self._draft if p != path]
        self.draft_row.setVisible(bool(self._draft))

    def take_draft(self) -> list[str]:
        files = [p for p, _ in self._draft]
        for chip in list(self._draft_chips.values()):
            chip.deleteLater()
        self._draft_chips.clear()
        self._draft.clear()
        self.draft_row.setVisible(False)
        return files

    def _urls(self, e) -> list[str]:
        md = e.mimeData()
        if md is None or not md.hasUrls():
            return []
        return [u.toLocalFile() for u in md.urls() if u.isLocalFile()]

    def dragEnterEvent(self, e):
        if self._urls(e):
            e.acceptProposedAction()

    def dragMoveEvent(self, e):
        if self._urls(e):
            e.acceptProposedAction()

    def dropEvent(self, e):
        paths = self._urls(e)
        if paths:
            self.add_files(paths)
            e.acceptProposedAction()
