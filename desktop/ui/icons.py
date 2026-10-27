"""Pochi desktop — ikon vektor konsisten (digambar dengan QPainter).

Tidak bergantung pada emoji/font ikon eksternal atau QtSvg (tidak terpasang
di sistem target). Semua ikon memakai grid 24×24, goresan membulat, sehingga
tampil seragam di semua ukuran.
"""
from __future__ import annotations

import math

from PyQt6.QtCore import QPointF, QRectF, Qt
from PyQt6.QtGui import (
    QColor, QIcon, QPainter, QPainterPath, QPen, QPixmap, QPolygonF,
)

STROKE = 1.8


def _path(d: str) -> QPainterPath:
    """Parser path SVG sederhana (M, L, H, V, C, Q, A, Z)."""
    p = QPainterPath()
    i = 0
    n = len(d)
    cmd = ""
    cx = cy = 0.0
    start = (0.0, 0.0)
    ctrl = (0.0, 0.0)          # kontrol terakhir untuk C/S
    had_cubic = False

    def num() -> float:
        nonlocal i
        while i < n and d[i] in " ,\n\t":
            i += 1
        j = i
        if i < n and d[i] in "+-":
            i += 1
        while i < n and d[i] not in " ,\n\tMLHVCQASTZmlhvcqastz+-":
            i += 1
        return float(d[j:i])

    while i < n:
        while i < n and d[i] in " ,\n\t":
            i += 1
        if i < n and d[i].isalpha():
            cmd = d[i]
            i += 1
        if not cmd:
            break
        c = cmd.upper()
        rel = cmd.islower()
        if c == "M":
            x, y = num(), num()
            if rel:
                x, y = cx + x, cy + y
            if not p.isEmpty() and cmd == "M":
                p.closeSubpath()
            p.moveTo(x, y)
            cx, cy = x, y
            start = (x, y)
            had_cubic = False
            cmd = "l" if rel else "L"
        elif c == "L":
            x, y = num(), num()
            if rel:
                x, y = cx + x, cy + y
            p.lineTo(x, y)
            cx, cy = x, y
            had_cubic = False
        elif c == "H":
            x = num()
            if rel:
                x += cx
            p.lineTo(x, cy)
            cx = x
            had_cubic = False
        elif c == "V":
            y = num()
            if rel:
                y += cy
            p.lineTo(cx, y)
            cy = y
            had_cubic = False
        elif c == "C":
            x1, y1, x2, y2, x, y = (num() for _ in range(6))
            if rel:
                x1, y1, x2, y2, x, y = cx + x1, cy + y1, cx + x2, cy + y2, cx + x, cy + y
            p.cubicTo(x1, y1, x2, y2, x, y)
            ctrl = (x2, y2)
            had_cubic = True
            cx, cy = x, y
        elif c == "S":
            x2, y2, x, y = (num() for _ in range(4))
            if rel:
                x2, y2, x, y = cx + x2, cy + y2, cx + x, cy + y
            if had_cubic:
                x1, y1 = 2 * cx - ctrl[0], 2 * cy - ctrl[1]
            else:
                x1, y1 = cx, cy
            p.cubicTo(x1, y1, x2, y2, x, y)
            ctrl = (x2, y2)
            had_cubic = True
            cx, cy = x, y
        elif c == "Q":
            x1, y1, x, y = (num() for _ in range(4))
            if rel:
                x1, y1, x, y = cx + x1, cy + y1, cx + x, cy + y
            p.quadTo(x1, y1, x, y)
            had_cubic = False
            cx, cy = x, y
        elif c == "A":
            rx, ry, rot = num(), num(), num()
            large = int(num())
            sweep = int(num())
            x, y = num(), num()
            if rel:
                x, y = cx + x, cy + y
            # QPainterPath hanya punya arcTo elips; aproksimasi busur umum
            # dengan kurva kuadrat untuk busur 90° (cukup untuk ikon).
            p.quadTo((cx + x) / 2 + (y - cy) * 0.35,
                     (cy + y) / 2 - (x - cx) * 0.35, x, y)
            cx, cy = x, y
            had_cubic = False
        elif c == "Z":
            p.closeSubpath()
            cx, cy = start
            cmd = ""
    return p


def draw_icon(p: QPainter, name: str, rect: QRectF, color: QColor,
              width: float = STROKE) -> None:
    """Gambar ikon ``name`` di dalam ``rect`` (koordinat device)."""
    if name not in _ICONS:
        return
    p.save()
    p.setRenderHint(QPainter.RenderHint.Antialiasing, True)
    p.translate(rect.x() + (rect.width() - 24.0) / 2,
                rect.y() + (rect.height() - 24.0) / 2)
    scale = min(rect.width(), rect.height()) / 24.0
    p.scale(scale, scale)
    p.setPen(QPen(color, width, Qt.PenStyle.SolidLine,
                  Qt.PenCapStyle.RoundCap, Qt.PenJoinStyle.RoundJoin))
    p.setBrush(Qt.BrushStyle.NoBrush)
    kind, data = _ICONS[name]
    if kind == "paths":
        for d in data:
            p.drawPath(_path(d))
    elif kind == "fill":
        for d in data:
            p.setBrush(color)
            p.setPen(Qt.PenStyle.NoPen)
            p.drawPath(_path(d))
    elif kind == "mixed":
        for d, fill in data:
            if fill:
                p.setBrush(color)
                p.setPen(Qt.PenStyle.NoPen)
            else:
                p.setBrush(Qt.BrushStyle.NoBrush)
                p.setPen(QPen(color, width, Qt.PenStyle.SolidLine,
                              Qt.PenCapStyle.RoundCap, Qt.PenJoinStyle.RoundJoin))
            p.drawPath(_path(d))
    elif kind == "lines":
        for x1, y1, x2, y2 in data:
            p.drawLine(QPointF(x1, y1), QPointF(x2, y2))
    elif kind == "ellipses":
        for cx, cy, rx, ry, fill in data:
            if fill:
                p.setBrush(color)
                p.setPen(Qt.PenStyle.NoPen)
            else:
                p.setBrush(Qt.BrushStyle.NoBrush)
                p.setPen(QPen(color, width, Qt.PenStyle.SolidLine,
                              Qt.PenCapStyle.RoundCap, Qt.PenJoinStyle.RoundJoin))
            p.drawEllipse(QPointF(cx, cy), rx, ry)
    elif kind == "arcs":
        p.setBrush(Qt.BrushStyle.NoBrush)
        for x, y, w, h, start, span in data:
            p.drawArc(QRectF(x, y, w, h), int(start * 16), int(span * 16))
    elif kind == "polys":
        for pts, fill in data:
            path = QPainterPath()
            path.addPolygon(QPolygonF([QPointF(x, y) for x, y in pts]))
            path.closeSubpath()
            if fill:
                p.setBrush(color)
                p.setPen(Qt.PenStyle.NoPen)
            else:
                p.setBrush(Qt.BrushStyle.NoBrush)
                p.setPen(QPen(color, width, Qt.PenStyle.SolidLine,
                              Qt.PenCapStyle.RoundCap, Qt.PenJoinStyle.RoundJoin))
            p.drawPath(path)
    p.restore()


# --------------------------------------------------------------------- set
# kind: paths | fill | mixed | lines | ellipses | arcs | polys
_ICONS: dict[str, tuple] = {
    "chat": ("paths", [
        "M4 5.5A2.5 2.5 0 0 1 6.5 3h11A2.5 2.5 0 0 1 20 5.5v8A2.5 2.5 0 0 1 17.5 16H11l-4.5 4v-4H6.5A2.5 2.5 0 0 1 4 13.5Z",
        "M8.5 8.5h7M8.5 11.5h4.5",
    ]),
    "send": ("mixed", [
        ("M3.4 20.4 21 12 3.4 3.6l0 6.6L14 12 3.4 13.8Z", True),
    ]),
    "stop": ("mixed", [
        ("M7 7h10a1.5 1.5 0 0 1 1.5 1.5v7A1.5 1.5 0 0 1 17 17H7a1.5 1.5 0 0 1-1.5-1.5v-7A1.5 1.5 0 0 1 7 7Z", True),
    ]),
    "mic": ("paths", [
        "M9 4.5a3 3 0 0 1 6 0v6a3 3 0 0 1-6 0Z",
        "M5.5 11.5a6.5 6.5 0 0 0 13 0",
        "M12 18v3M9 21h6",
    ]),
    "close": ("lines", [(6, 6, 18, 18), (18, 6, 6, 18)]),
    "settings": ("paths", [
        "M12 8.6a3.4 3.4 0 1 0 0 6.8 3.4 3.4 0 0 0 0-6.8Z",
        "M12 2.5v2.4M12 19.1v2.4M2.5 12h2.4M19.1 12h2.4"
        "M5.3 5.3l1.7 1.7M17 17l1.7 1.7M18.7 5.3 17 7M7 17l-1.7 1.7",
    ]),
    "dashboard": ("paths", [
        "M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h6v6h-6z",
    ]),
    "cpu": ("paths", [
        "M7 7h10v10H7zM10 10h4v4h-4z",
        "M9 3v4M15 3v4M9 17v4M15 17v4M3 9h4M3 15h4M17 9h4M17 15h4",
    ]),
    "memory": ("paths", [
        "M3 7h18v10H3zM7 10v4M12 10v4M17 10v4",
        "M7 3v4M12 3v4M17 3v4M7 17v4M12 17v4M17 17v4",
    ]),
    "battery": ("mixed", [
        ("M3 7h16a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H3Z", False),
        ("M19 10h2v4h-2Z", True),
        ("M5.5 9.5h9v5h-9Z", True),
    ]),
    "wifi": ("mixed", [
        ("M2.5 8.5a14 14 0 0 1 19 0M5.5 12a9.5 9.5 0 0 1 13 0M8.5 15.5a5 5 0 0 1 7 0", False),
        ("M12 19.4a1.2 1.2 0 1 0 0 .1", True),
    ]),
    "bell": ("paths", [
        "M18 8.5a6 6 0 0 0-12 0c0 6-2.5 6.5-2.5 8h17c0-1.5-2.5-2-2.5-8Z",
        "M10 20.2a2.2 2.2 0 0 0 4 0",
    ]),
    "play": ("fill", ["M8 5.2 19 12 8 18.8Z"]),
    "pause": ("fill", ["M8 5h3v14H8zM13 5h3v14h-3z"]),
    "next": ("mixed", [
        ("M6 5.2 16 12 6 18.8Z", True),
        ("M18 5h2v14h-2Z", True),
    ]),
    "prev": ("mixed", [
        ("M18 5.2 8 12l10 6.8Z", True),
        ("M4 5h2v14H4Z", True),
    ]),
    "telegram": ("paths", [
        "M21.5 2.5 14.8 21.3l-4.1-7.1L2.5 9.6Z",
        "M10.7 14.2 21.5 2.5",
    ]),
    "spark": ("paths", [
        "M12 2.8 14.2 9.8 21.2 12l-7 2.2L12 21.2l-2.2-7L2.8 12l7-2.2Z",
    ]),
    "check": ("paths", ["M4.5 12.5 9.6 17.6 19.6 6.8"]),
    "alert": ("paths", [
        "M12 3 22 20.5H2Z",
        "M12 9v5M12 17.2v.2",
    ]),
    "clock": ("paths", [
        "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18Z",
        "M12 7v5.4l3.4 2",
    ]),
    "timer": ("paths", [
        "M12 4a8 8 0 1 0 0 16 8 8 0 0 0 0-16Z",
        "M12 8v4.4l3 1.8M9 2h6M12 2v2",
    ]),
    "plus": ("lines", [(12, 5, 12, 19), (5, 12, 19, 12)]),
    "refresh": ("arcs", [
        (3.5, 3.5, 17, 17, 35, 250),
        (3.5, 3.5, 17, 17, 285, 50),
    ]),
    "chevron-down": ("paths", ["M6.5 9.5 12 15l5.5-5.5"]),
    "chevron-right": ("paths", ["M9.5 6.5 15 12l-5.5 5.5"]),
    "chevron-up": ("paths", ["M6.5 14.5 12 9l5.5 5.5"]),
    "more": ("ellipses", [(6, 12, 1.4, 1.4, True), (12, 12, 1.4, 1.4, True),
                          (18, 12, 1.4, 1.4, True)]),
    "terminal": ("paths", [
        "M4 4h16v16H4zM7.5 9l3 3-3 3M12.5 15h4",
    ]),
    "external": ("paths", [
        "M14 4h6v6M20 4l-8.5 8.5",
        "M18 13.5V19a1.5 1.5 0 0 1-1.5 1.5H5A1.5 1.5 0 0 1 3.5 19V7.5A1.5 1.5 0 0 1 5 6h5.5",
    ]),
    "shield": ("paths", [
        "M12 3 20 6v6c0 5-3.4 7.8-8 9-4.6-1.2-8-4-8-9V6Z",
        "M8.8 12.2l2.2 2.2 4.4-4.6",
    ]),
    "trash": ("paths", [
        "M4.5 6.5h15M9 6.5V4h6v2.5M6.5 6.5 7.5 21h9l1-14.5M10 10.5v7M14 10.5v7",
    ]),
    "arrow-left": ("paths", ["M19 12H5M11 6l-6 6 6 6"]),
    "volume": ("mixed", [
        ("M4 9.5h4L13 5v14L8 14.5H4Z", False),
        ("M16 9a4.5 4.5 0 0 1 0 6M18.5 6.5a8 8 0 0 1 0 11", False),
    ]),
    "music": ("paths", [
        "M9 18V6.5l10-2v11.5",
        "M9 18a2.5 2.5 0 1 1-5 0 2.5 2.5 0 0 1 5 0ZM19 16a2.5 2.5 0 1 1-5 0 2.5 2.5 0 0 1 5 0Z",
    ]),
    "power": ("paths", [
        "M12 3v9M6.3 6.3a8 8 0 1 0 11.4 0",
    ]),
    "globe": ("paths", [
        "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18ZM3.5 9h17M3.5 15h17",
        "M12 3c-2.4 2.4-3.6 5.4-3.6 9s1.2 6.6 3.6 9c2.4-2.4 3.6-5.4 3.6-9S14.4 5.4 12 3Z",
    ]),
    "up": ("paths", ["M12 19V5M6 11l6-6 6 6"]),
    "down": ("paths", ["M12 5v14M6 13l6 6 6-6"]),
}


def icon_pixmap(name: str, size: int = 18, color: str | QColor = "#94A3B8",
                dpr: float = 2.0) -> QPixmap:
    col = QColor(color) if isinstance(color, str) else QColor(color)
    px = QPixmap(int(size * dpr), int(size * dpr))
    px.fill(Qt.GlobalColor.transparent)
    px.setDevicePixelRatio(dpr)
    p = QPainter(px)
    draw_icon(p, name, QRectF(0, 0, size, size), col)
    p.end()
    return px


def make_icon(name: str, color: str | QColor = "#94A3B8", size: int = 18) -> QIcon:
    return QIcon(icon_pixmap(name, size, color))


def pochi_face_pixmap(size: int = 64) -> QPixmap:
    """Pixmap muka Pochi (mata putih di atas persegi matte-hitam)."""
    px = QPixmap(size, size)
    px.fill(Qt.GlobalColor.transparent)
    p = QPainter(px)
    p.setRenderHint(QPainter.RenderHint.Antialiasing, True)
    s = float(size)
    pad = s * 0.07
    corner = s * 0.24
    body = QRectF(pad, pad, s - pad * 2, s - pad * 2)
    p.setPen(Qt.PenStyle.NoPen)
    p.setBrush(QColor("#0A0A0A"))
    p.drawRoundedRect(body, corner, corner)
    p.setBrush(Qt.BrushStyle.NoBrush)
    p.setPen(QPen(QColor("#26262E"), max(1.0, s * 0.03)))
    p.drawRoundedRect(body, corner, corner)
    gw = s * 0.14
    gh = s * 0.28
    cy = s * 0.50
    p.setPen(Qt.PenStyle.NoPen)
    p.setBrush(QColor(255, 255, 255))
    for x in (s * 0.36, s * 0.64):
        p.drawRoundedRect(QRectF(x - gw / 2, cy - gh / 2, gw, gh),
                          gw / 2, gw / 2)
    p.end()
    return px


def _pochi_face_icon(size: int = 64) -> QIcon:
    """Ikon aplikasi: muka Pochi (mata putih di atas persegi matte-hitam)."""
    return QIcon(pochi_face_pixmap(size))


def app_icon() -> QIcon:
    icon = QIcon()
    for s in (16, 22, 32, 48, 64, 128, 256):
        icon.addPixmap(_pochi_face_icon(s).pixmap(s, s))
    return icon


def with_icon(name: str, label: str, gap: str = "  ") -> str:
    """Fallback teks (mis. untuk menu tanpa QIcon)."""
    return label
