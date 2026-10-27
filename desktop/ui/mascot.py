"""Pochi Mascot — mesin 15 ekspresi berbasis QPainter.

Wajah digambar sebagai dua mata plus mulut/aksesori kecil. Status aplikasi
memilih ekspresi secara otomatis; interaksi (poke/pet) memakai ekspresi
eksplisit yang kembali ke status setelah beberapa saat. Animasi hanya
berjalan bila ``reduce_motion`` mati dan widget terlihat.
"""
from __future__ import annotations

import random
import time

from PyQt6.QtCore import QPointF, QRectF, Qt, QTimer
from PyQt6.QtGui import (
    QColor, QFont, QLinearGradient, QPainter, QPainterPath, QPen,
)
from PyQt6.QtWidgets import QWidget

from app import theme
from app.state import Status

EMOTES: tuple[str, ...] = (
    "wink", "angry", "neutral", "happy", "excited", "laughing", "sad",
    "crying", "annoyed", "surprised", "confused", "shy", "sleepy",
    "thinking", "love",
)

EMOTE_LABELS: dict[str, str] = {
    "wink": "Wink",
    "angry": "Marah",
    "neutral": "Santai",
    "happy": "Senang",
    "excited": "Semangat",
    "laughing": "Tertawa",
    "sad": "Sedih",
    "crying": "Menangis",
    "annoyed": "Kesal",
    "surprised": "Terkejut",
    "confused": "Bingung",
    "shy": "Malu",
    "sleepy": "Ngantuk",
    "thinking": "Berpikir",
    "love": "Sayang",
}

POKE_CYCLE = ("wink", "happy", "surprised", "shy")
IDLE_POOL = ("neutral", "wink", "confused", "thinking")
SLEEP_AFTER = 90.0

# Alias lama agar import lama tetap jalan.
NORMAL, HAPPY, WORKING, ERROR, SLEEPING = (
    "neutral", "happy", "thinking", "sad", "sleepy",
)

EMOTE_BY_STATUS = {
    Status.IDLE: "neutral",
    Status.LISTENING: "surprised",
    Status.THINKING: "thinking",
    Status.WORKING: "thinking",
    Status.SUCCESS: "happy",
    Status.ERROR: "sad",
}

WHITE = QColor("#F5F3FF")


def emote_for_status(status: str, error_count: int = 0) -> str:
    """Petakan status controller ke nama ekspresi yang stabil."""
    if status == Status.IDLE:
        return "neutral"
    if status == Status.LISTENING:
        return "surprised"
    if status in (Status.THINKING, Status.WORKING):
        return "thinking"
    if status == Status.SUCCESS:
        return "happy" if error_count == 0 else "excited"
    if status == Status.ERROR:
        return "annoyed" if error_count >= 3 else "sad"
    return "neutral"


def idle_emote(rng: random.Random | None = None) -> str:
    """Ekspresi acak untuk saat menganggur."""
    return (rng or random).choice(IDLE_POOL)


class PochiMascot(QWidget):
    def __init__(self, size: int = 44, status_provider=None,
                 reduce_motion: bool = False, show_backing: bool = True,
                 interactive: bool = False, parent=None):
        super().__init__(parent)
        self._size = max(12, int(size))
        self._status_provider = status_provider
        self._reduce = bool(reduce_motion)
        self._backing = bool(show_backing)
        self._interactive = bool(interactive)
        self._status = Status.IDLE
        self._blinking = False
        self._blink_until = 0.0
        self._next_blink = time.monotonic() + random.uniform(2.2, 4.0)

        now = time.monotonic()
        self._explicit_emote: str | None = None
        self._idle_name: str | None = None
        self._poke_index = 0
        self._idle_since = now
        self._next_idle_emote = now + random.uniform(20.0, 40.0)
        self._sleepy = False

        w = int(self._size * 1.42) if self._backing else self._size
        self.setFixedSize(w, self._size)
        if not self._interactive:
            self.setAttribute(
                Qt.WidgetAttribute.WA_TransparentForMouseEvents, True)
        else:
            self.setCursor(Qt.CursorShape.PointingHandCursor)
            self.setToolTip("Klik: poke · Klik kanan: pet")
        self.setAccessibleName("Pochi mascot")

        self._timer = QTimer(self)
        self._timer.setInterval(50)
        self._timer.timeout.connect(self._tick)
        self._refresh_timer()

    # ------------------------------------------------------------- state
    @property
    def status(self) -> str:
        return self._status

    def set_status(self, s: str) -> None:
        self._idle_since = time.monotonic()
        self._sleepy = False
        self._explicit_emote = None
        if s == self._status:
            self.update()
            return
        self._status = s
        self._refresh_timer()
        self.update()

    def current_status(self) -> str:
        if self._status_provider is not None:
            try:
                return self._status_provider()
            except Exception:
                pass
        return self._status

    def accent_color(self) -> QColor:
        return QColor(Status.color(self.current_status()))

    # ---------------------------------------------------------- ekspresi
    def set_emote(self, name: str) -> None:
        self._explicit_emote = name if name in EMOTES else "neutral"
        self._sleepy = False
        self._idle_since = time.monotonic()
        self._next_idle_emote = self._idle_since + random.uniform(20.0, 40.0)
        self._refresh_timer()
        self.update()

    def current_emote(self) -> str:
        if self._explicit_emote:
            return self._explicit_emote
        if self._sleepy:
            return "sleepy"
        status = self.current_status()
        if status == Status.IDLE and self._idle_name:
            return self._idle_name
        return emote_for_status(status)

    # Alias lama.
    def emote(self) -> str:
        return self.current_emote()

    def poke(self) -> str:
        name = POKE_CYCLE[self._poke_index % len(POKE_CYCLE)]
        self._poke_index += 1
        self.set_emote(name)
        return name

    def pet(self) -> str:
        self.set_emote("love")
        return "love"

    def mousePressEvent(self, e):
        if self._interactive:
            if e.button() == Qt.MouseButton.RightButton:
                self.pet()
            elif e.button() == Qt.MouseButton.LeftButton:
                self.poke()
            e.accept()
            return
        super().mousePressEvent(e)

    def set_reduce_motion(self, value: bool) -> None:
        self._reduce = bool(value)
        self._refresh_timer()
        self.update()

    def set_sleeping(self, value: bool) -> None:
        value = bool(value)
        if value == self._sleepy:
            return
        self._sleepy = value
        if value:
            self._explicit_emote = None
        else:
            self._idle_since = time.monotonic()
            self._next_idle_emote = self._idle_since + random.uniform(20.0, 40.0)
        self._refresh_timer()
        self.update()

    def sleeping(self) -> bool:
        return self._sleepy

    def _needs_motion(self) -> bool:
        return not self._reduce and self.isVisible()

    def _refresh_timer(self) -> None:
        if self._needs_motion():
            if not self._timer.isActive():
                self._timer.start()
        else:
            self._timer.stop()

    def showEvent(self, e):
        super().showEvent(e)
        self._refresh_timer()

    def hideEvent(self, e):
        super().hideEvent(e)
        self._timer.stop()

    def _tick(self):
        now = time.monotonic()
        idle = self.current_status() == Status.IDLE

        if idle and not self._explicit_emote and not self._sleepy:
            if now - self._idle_since >= SLEEP_AFTER:
                self._sleepy = True
                self._blinking = False
            elif now >= self._next_idle_emote:
                self._idle_name = idle_emote()
                self._next_idle_emote = now + random.uniform(20.0, 40.0)

        if self._blinking:
            if now >= self._blink_until:
                self._blinking = False
                self._next_blink = now + random.uniform(2.2, 4.0)
        elif now >= self._next_blink and not self._sleepy:
            self._blinking = True
            self._blink_until = now + 0.12
        self.update()

    # ------------------------------------------------------------- paint
    def paintEvent(self, _):
        p = QPainter(self)
        p.setRenderHint(QPainter.RenderHint.Antialiasing, True)
        w, h = float(self.width()), float(self.height())
        s = min(w, h)
        cx, cy = w * 0.5, h * 0.5

        if self._backing:
            self._paint_backing(p, w, h, s)

        emote = self.current_emote()
        ex = s * 0.165
        lx, rx = cx - ex, cx + ex
        ey = cy - s * 0.01
        stroke = max(2.0, s * 0.07)

        if self._blinking and emote in ("neutral", "thinking"):
            emote = "blink"

        self._draw_emote(p, emote, lx, rx, ey, s, stroke, cx, cy)

    def _paint_backing(self, p, w, h, s):
        pad = s * 0.04
        corner = max(h * 0.28, 7.0)
        rect = QRectF(pad, pad, w - pad * 2, h - pad * 2)
        grad = QLinearGradient(0, rect.top(), 0, rect.bottom())
        grad.setColorAt(0.0, theme.CARD_TOP)
        grad.setColorAt(1.0, theme.CARD_BOTTOM)
        p.setPen(Qt.PenStyle.NoPen)
        p.setBrush(grad)
        p.drawRoundedRect(rect, corner, corner)

        rim = QColor(theme.VIOLET_LIGHT)
        rim.setAlpha(120)
        p.setBrush(Qt.BrushStyle.NoBrush)
        p.setPen(QPen(rim, max(1.0, s * 0.022)))
        p.drawRoundedRect(rect, corner, corner)

    def _draw_emote(self, p, emote, lx, rx, ey, s, stroke, cx, cy):
        if emote == "blink":
            self._dash(p, lx, ey, s, stroke)
            self._dash(p, rx, ey, s, stroke)
        elif emote == "wink":
            self._sleep_lid(p, lx, ey, s, stroke)
            self._eye(p, rx, ey, s)
            self._mouth(p, cx, cy + s * 0.24, s, "smile")
        elif emote == "angry":
            self._brow(p, lx, ey, s, stroke, left=True)
            self._brow(p, rx, ey, s, stroke, left=False)
            self._eye(p, lx, ey + s * 0.06, s, wf=0.10, hf=0.15)
            self._eye(p, rx, ey + s * 0.06, s, wf=0.10, hf=0.15)
            self._mouth(p, cx, cy + s * 0.26, s, "frown")
        elif emote == "happy":
            self._caret(p, lx, ey, s, stroke)
            self._caret(p, rx, ey, s, stroke)
            self._mouth(p, cx, cy + s * 0.22, s, "smile")
        elif emote == "excited":
            self._caret(p, lx, ey, s, stroke, lift=1.5)
            self._caret(p, rx, ey, s, stroke, lift=1.5)
            self._mouth(p, cx, cy + s * 0.24, s, "open")
            self._sparkle(p, cx - s * 0.42, cy - s * 0.34, s, stroke * 0.6)
            self._sparkle(p, cx + s * 0.44, cy - s * 0.26, s, stroke * 0.5)
        elif emote == "laughing":
            self._caret(p, lx, ey, s, stroke, lift=1.7)
            self._caret(p, rx, ey, s, stroke, lift=1.7)
            self._mouth(p, cx, cy + s * 0.26, s, "laugh")
        elif emote == "sad":
            self._eye(p, lx, ey, s, wf=0.12, hf=0.22)
            self._eye(p, rx, ey, s, wf=0.12, hf=0.22)
            self._mouth(p, cx, cy + s * 0.26, s, "frown")
        elif emote == "crying":
            self._sleep_lid(p, lx, ey + s * 0.02, s, stroke)
            self._sleep_lid(p, rx, ey + s * 0.02, s, stroke)
            self._tear(p, lx, ey + s * 0.16, s)
            self._tear(p, rx, ey + s * 0.16, s)
            self._mouth(p, cx, cy + s * 0.28, s, "frown")
        elif emote == "annoyed":
            self._dash(p, lx, ey, s, stroke)
            self._dash(p, rx, ey, s, stroke)
            self._mouth(p, cx, cy + s * 0.24, s, "flat")
        elif emote == "surprised":
            self._eye(p, lx, ey, s, wf=0.15, hf=0.28)
            self._eye(p, rx, ey, s, wf=0.15, hf=0.28)
            self._mouth(p, cx, cy + s * 0.28, s, "small_o")
        elif emote == "confused":
            self._eye(p, lx, ey, s, wf=0.12, hf=0.22)
            self._eye(p, rx, ey + s * 0.04, s, wf=0.08, hf=0.13)
            self._question(p, rx + s * 0.14, ey - s * 0.26, s)
            self._mouth(p, cx, cy + s * 0.24, s, "flat")
        elif emote == "shy":
            self._dash(p, lx, ey, s, stroke)
            self._dash(p, rx, ey, s, stroke)
            self._blush(p, lx, rx, cy, s)
        elif emote == "sleepy":
            self._sleep_lid(p, lx, ey, s, stroke)
            self._sleep_lid(p, rx, ey, s, stroke)
            self._draw_z(p, rx + s * 0.16, ey - s * 0.26, s, stroke)
            self._draw_z(p, rx + s * 0.30, ey - s * 0.46, s * 0.7, stroke)
        elif emote == "thinking":
            self._eye(p, lx - s * 0.02, ey - s * 0.04, s, wf=0.12, hf=0.22)
            self._eye(p, rx - s * 0.02, ey - s * 0.04, s, wf=0.12, hf=0.22)
            for i in range(3):
                r = s * 0.026
                p.setPen(Qt.PenStyle.NoPen)
                p.setBrush(WHITE)
                p.drawEllipse(QRectF(cx + s * (0.16 + i * 0.09),
                                     cy + s * 0.26, r * 2, r * 2))
        elif emote == "love":
            self._heart(p, lx, ey, s)
            self._heart(p, rx, ey, s)
            self._mouth(p, cx, cy + s * 0.24, s, "smile")
        else:  # neutral
            self._eye(p, lx, ey, s)
            self._eye(p, rx, ey, s)

    # ------------------------------------------------------------ bentuk
    def _eye(self, p, cx, cy, s, wf=0.13, hf=0.24):
        ew = s * wf
        eh = s * hf
        p.setPen(Qt.PenStyle.NoPen)
        p.setBrush(WHITE)
        p.drawEllipse(QRectF(cx - ew / 2, cy - eh / 2, ew, eh))

    def _brow(self, p, cx, cy, s, stroke, left=True):
        dy = s * 0.20
        dx = s * 0.10
        if left:
            a = QPointF(cx - dx, cy - dy - s * 0.02)
            b = QPointF(cx + dx, cy - dy + s * 0.05)
        else:
            a = QPointF(cx - dx, cy - dy + s * 0.05)
            b = QPointF(cx + dx, cy - dy - s * 0.02)
        p.setPen(QPen(WHITE, stroke * 0.9, Qt.PenStyle.SolidLine,
                      Qt.PenCapStyle.RoundCap))
        p.drawLine(a, b)

    def _caret(self, p, cx, cy, s, stroke, lift=1.0):
        half_w = s * 0.085
        half_h = s * 0.055 * lift
        path = QPainterPath()
        path.moveTo(cx - half_w, cy + half_h * 0.55)
        path.quadTo(cx, cy - half_h * 1.25, cx + half_w, cy + half_h * 0.55)
        p.setPen(QPen(WHITE, stroke, Qt.PenStyle.SolidLine,
                      Qt.PenCapStyle.RoundCap, Qt.PenJoinStyle.RoundJoin))
        p.setBrush(Qt.BrushStyle.NoBrush)
        p.drawPath(path)

    def _dash(self, p, cx, cy, s, stroke):
        half = s * 0.08
        p.setPen(QPen(WHITE, stroke, Qt.PenStyle.SolidLine,
                      Qt.PenCapStyle.RoundCap))
        p.drawLine(QPointF(cx - half, cy), QPointF(cx + half, cy))

    def _sleep_lid(self, p, cx, cy, s, stroke):
        half_w = s * 0.09
        half_h = s * 0.05
        path = QPainterPath()
        path.moveTo(cx - half_w, cy)
        path.quadTo(cx, cy - half_h, cx + half_w, cy)
        p.setPen(QPen(WHITE, stroke, Qt.PenStyle.SolidLine,
                      Qt.PenCapStyle.RoundCap, Qt.PenJoinStyle.RoundJoin))
        p.setBrush(Qt.BrushStyle.NoBrush)
        p.drawPath(path)

    def _mouth(self, p, cx, cy, s, kind: str):
        pen = QPen(WHITE, max(1.8, s * 0.055), Qt.PenStyle.SolidLine,
                   Qt.PenCapStyle.RoundCap, Qt.PenJoinStyle.RoundJoin)
        p.setPen(pen)
        p.setBrush(Qt.BrushStyle.NoBrush)
        w = s * 0.16

        if kind == "smile":
            path = QPainterPath(QPointF(cx - w, cy - s * 0.03))
            path.quadTo(cx, cy + s * 0.10, cx + w, cy - s * 0.03)
            p.drawPath(path)
        elif kind == "frown":
            path = QPainterPath(QPointF(cx - w, cy + s * 0.06))
            path.quadTo(cx, cy - s * 0.06, cx + w, cy + s * 0.06)
            p.drawPath(path)
        elif kind == "flat":
            p.drawLine(QPointF(cx - w * 0.65, cy), QPointF(cx + w * 0.65, cy))
        elif kind == "open":
            p.setBrush(WHITE)
            p.setPen(Qt.PenStyle.NoPen)
            p.drawEllipse(QRectF(cx - w * 0.42, cy - s * 0.05,
                                 w * 0.84, s * 0.13))
        elif kind == "laugh":
            p.setBrush(WHITE)
            p.setPen(Qt.PenStyle.NoPen)
            p.drawEllipse(QRectF(cx - w * 0.55, cy - s * 0.06,
                                 w * 1.10, s * 0.18))
        elif kind == "small_o":
            p.setBrush(Qt.BrushStyle.NoBrush)
            p.setPen(QPen(WHITE, max(1.6, s * 0.045),
                          Qt.PenStyle.SolidLine, Qt.PenCapStyle.RoundCap))
            r = s * 0.055
            p.drawEllipse(QRectF(cx - r, cy - r * 0.8, r * 2, r * 1.6))

    def _tear(self, p, cx, cy, s):
        drop = QColor(theme.CYAN)
        p.setPen(Qt.PenStyle.NoPen)
        p.setBrush(drop)
        p.drawEllipse(QRectF(cx - s * 0.035, cy, s * 0.07, s * 0.11))

    def _blush(self, p, lx, rx, cy, s):
        c = QColor(theme.VIOLET_LIGHT)
        c.setAlpha(190)
        p.setPen(Qt.PenStyle.NoPen)
        p.setBrush(c)
        r = s * 0.085
        y = cy + s * 0.13
        p.drawEllipse(QRectF(lx - r, y - r * 0.55, r * 2, r * 1.1))
        p.drawEllipse(QRectF(rx - r, y - r * 0.55, r * 2, r * 1.1))

    def _question(self, p, cx, cy, s):
        p.setPen(QPen(QColor(theme.VIOLET_LIGHT), max(2.0, s * 0.07)))
        f = QFont()
        f.setPointSizeF(max(7.0, s * 0.24))
        f.setBold(True)
        p.setFont(f)
        p.drawText(QRectF(cx - s * 0.12, cy - s * 0.14, s * 0.24, s * 0.28),
                   int(Qt.AlignmentFlag.AlignCenter), "?")

    def _sparkle(self, p, cx, cy, s, stroke):
        r = s * 0.06
        p.setPen(QPen(QColor(theme.VIOLET_LIGHT), stroke,
                      Qt.PenStyle.SolidLine, Qt.PenCapStyle.RoundCap))
        p.drawLine(QPointF(cx - r, cy), QPointF(cx + r, cy))
        p.drawLine(QPointF(cx, cy - r), QPointF(cx, cy + r))

    def _heart(self, p, cx, cy, s):
        path = QPainterPath()
        r = s * 0.075
        path.moveTo(cx, cy + r * 1.1)
        path.cubicTo(cx - r * 2.0, cy - r * 0.3, cx - r * 0.9, cy - r * 1.8,
                     cx, cy - r * 0.6)
        path.cubicTo(cx + r * 0.9, cy - r * 1.8, cx + r * 2.0, cy - r * 0.3,
                     cx, cy + r * 1.1)
        p.setPen(Qt.PenStyle.NoPen)
        p.setBrush(QColor(theme.VIOLET_LIGHT))
        p.drawPath(path)

    def _draw_z(self, p, cx, cy, s, stroke):
        w = s * 0.10
        h = s * 0.16
        p.setPen(QPen(WHITE, stroke * 0.7, Qt.PenStyle.SolidLine,
                      Qt.PenCapStyle.RoundCap))
        p.drawLine(QPointF(cx - w / 2, cy - h / 2),
                   QPointF(cx + w / 2, cy - h / 2))
        p.drawLine(QPointF(cx + w / 2, cy - h / 2),
                   QPointF(cx - w / 2, cy + h / 2))
        p.drawLine(QPointF(cx - w / 2, cy + h / 2),
                   QPointF(cx + w / 2, cy + h / 2))

    def _cross(self, p, cx, cy, s, stroke):
        r = s * 0.07
        p.setPen(QPen(WHITE, stroke * 0.85, Qt.PenStyle.SolidLine,
                      Qt.PenCapStyle.RoundCap))
        p.drawLine(QPointF(cx - r, cy - r), QPointF(cx + r, cy + r))
        p.drawLine(QPointF(cx + r, cy - r), QPointF(cx - r, cy + r))
