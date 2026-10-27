"""Helper X11/desktop: area kerja layar dan penempatan jangkar.

Nama paket sengaja bukan ``platform`` agar tidak membayangi modul standar
Python. Semua pengukuran memakai QScreen (bukan xrandr) sehingga menghormati
panel XFCE dan skala DPI.
"""
from __future__ import annotations

import os

from PyQt6.QtCore import QRect
from PyQt6.QtGui import QGuiApplication

EDGE_MARGIN = 12


def session_kind() -> str:
    return os.environ.get("XDG_SESSION_TYPE", "x11")


def work_area() -> QRect:
    app = QGuiApplication.instance()
    if app is not None:
        screen = app.primaryScreen()
        if screen is not None:
            geo = screen.availableGeometry()
            if geo.isValid() and geo.width() > 200:
                return geo
    return QRect(0, 0, 1366, 768)


def screen_geometry() -> tuple[int, int]:
    r = work_area()
    return r.width(), r.height()


def anchor_origin(anchor: str, width: int, height: int, y_offset: int,
                  custom: tuple[int, int] | None = None) -> tuple[int, int]:
    """Hitung posisi kiri-atas agar panel menempel di area kerja."""
    area = work_area()
    if custom is not None:
        x, y = int(custom[0]), int(custom[1])
    else:
        if anchor == "top-left":
            x = area.left() + EDGE_MARGIN
        elif anchor == "top-right":
            x = area.right() - width - EDGE_MARGIN + 1
        else:
            x = area.left() + (area.width() - width) // 2
        y = area.top() + max(0, int(y_offset))
    x = min(max(area.left(), x), area.right() - width + 1)
    y = min(max(area.top(), y), area.bottom() - height + 1)
    return x, y
