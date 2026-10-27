"""Pochi Mascot — versi sederhana (simple icon).

Hanya dua mata putih polos yang berkedip. Tanpa halo glow, tanpa napas,
tanpa drift pandangan, tanpa ekspresi rumit — bersih dan minimal. Status
disampaikan lewat bentuk mata (pil / titik / caret / X) plus warna label.
"""
from __future__ import annotations

import random
import time

from PyQt6.QtCore import QPointF, QRectF, Qt, QTimer
from PyQt6.QtGui import QColor, QPainter, QPainterPath, QPen
from PyQt6.QtWidgets import QWidget

from app.state import Status

NORMAL, HAPPY, WORKING, ERROR = "normal", "happy", "working", "error"

EMOTE_BY_STATUS = {
    Status.IDLE: NORMAL,
    Status.LISTENING: NORMAL,
    Status.THINKING: WORKING,
    Status.WORKING: WORKING,
    Status.SUCCESS: HAPPY,
    Status.ERROR: ERROR,
}

_WHITE = QColor(255, 255, 255)


class PochiMascot(QWidget):
    def __init__(self, size: int = 44, status_provider=None,
                 reduce_motion: bool = False, show_backing: bool = True,
                 parent=None):
        super().__init__(parent)
        self._size = max(12, int(size))
        self._status_provider = status_provider
        self._reduce = bool(reduce_motion)
        self._backing = bool(show_backing)
        self._status = Status.IDLE
        self._blinking = False
        self._blink_until = 0.0
        self._next_blink = time.monotonic() + random.uniform(2.2, 4.0)

        w = int(self._size * 1.42) if self._backing else self._size
        self.setFixedSize(w, self._size)
        self.setAttribute(Qt.WidgetAttribute.WA_TransparentForMouseEvents, True)
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
        if s == self._status:
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

    def emote(self) -> str:
        return EMOTE_BY_STATUS.get(self.current_status(), NORMAL)

    def set_reduce_motion(self, value: bool) -> None:
        self._reduce = bool(value)
        self._refresh_timer()
        self.update()

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
        if self._blinking:
            if now >= self._blink_until:
                self._blinking = False
                self._next_blink = now + random.uniform(2.2, 4.0)
        else:
            if now >= self._next_blink:
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
            pad = s * 0.04
            corner = max(h * 0.22, 6.0)
            rect = QRectF(pad, pad, w - pad * 2, h - pad * 2)
            p.setPen(Qt.PenStyle.NoPen)
            p.setBrush(QColor("#0A0A0A"))
            p.drawRoundedRect(rect, corner, corner)
            p.setBrush(Qt.BrushStyle.NoBrush)
            p.setPen(QPen(QColor("#26262E"), max(1.0, s * 0.025)))
            p.drawRoundedRect(rect, corner, corner)

        emote = self.emote()
        lx = cx - s * 0.175
        rx = cx + s * 0.175
        ey = cy
        stroke = max(2.2, s * 0.08)

        if self._blinking and emote in (NORMAL, WORKING, HAPPY):
            self._dash(p, lx, ey, s, stroke)
            self._dash(p, rx, ey, s, stroke)
        else:
            self._draw_emote(p, emote, lx, rx, ey, s, stroke)

    def _draw_emote(self, p, emote, lx, rx, ey, s, stroke):
        if emote == HAPPY:
            self._caret(p, lx, ey, s, stroke)
            self._caret(p, rx, ey, s, stroke)
        elif emote == ERROR:
            self._cross(p, lx, ey, s, stroke)
            self._cross(p, rx, ey, s, stroke)
        elif emote == WORKING:
            r = s * 0.072
            p.setPen(Qt.PenStyle.NoPen)
            p.setBrush(_WHITE)
            p.drawEllipse(QRectF(lx - r, ey - r, r * 2, r * 2))
            p.drawEllipse(QRectF(rx - r, ey - r, r * 2, r * 2))
        else:
            ew = s * 0.15
            eh = s * 0.28
            p.setPen(Qt.PenStyle.NoPen)
            p.setBrush(_WHITE)
            p.drawRoundedRect(QRectF(lx - ew / 2, ey - eh / 2, ew, eh),
                              ew / 2, ew / 2)
            p.drawRoundedRect(QRectF(rx - ew / 2, ey - eh / 2, ew, eh),
                              ew / 2, ew / 2)

    def _caret(self, p, cx, cy, s, stroke):
        half_w = s * 0.09
        half_h = s * 0.065
        path = QPainterPath()
        path.moveTo(cx - half_w, cy + half_h * 0.55)
        path.quadTo(cx, cy - half_h * 1.25, cx + half_w, cy + half_h * 0.55)
        p.setPen(QPen(_WHITE, stroke, Qt.PenStyle.SolidLine,
                      Qt.PenCapStyle.RoundCap, Qt.PenJoinStyle.RoundJoin))
        p.setBrush(Qt.BrushStyle.NoBrush)
        p.drawPath(path)

    def _dash(self, p, cx, cy, s, stroke):
        half = s * 0.085
        p.setPen(QPen(_WHITE, stroke, Qt.PenStyle.SolidLine,
                      Qt.PenCapStyle.RoundCap))
        p.drawLine(QPointF(cx - half, cy), QPointF(cx + half, cy))

    def _cross(self, p, cx, cy, s, stroke):
        r = s * 0.07
        p.setPen(QPen(_WHITE, stroke * 0.85, Qt.PenStyle.SolidLine,
                      Qt.PenCapStyle.RoundCap))
        p.drawLine(QPointF(cx - r, cy - r), QPointF(cx + r, cy + r))
        p.drawLine(QPointF(cx + r, cy - r), QPointF(cx - r, cy + r))
