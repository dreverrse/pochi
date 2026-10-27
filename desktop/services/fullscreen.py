"""Deteksi layar penuh (X11/EWMH) untuk menyembunyikan notch (PRD 4.10).

Memakai `xprop` (tersedia di lingkungan X11). Saat jendela aktif milik aplikasi
lain beralih ke _NET_WM_STATE_FULLSCREEN, sinyal changed(True) dipancarkan.
"""
from __future__ import annotations

import re
import subprocess

from PyQt6.QtCore import QObject, QTimer, pyqtSignal

_ACTIVE_RE = re.compile(r"0x[0-9a-fA-F]+")
_FULLSCREEN = "_NET_WM_STATE_FULLSCREEN"


class FullscreenService(QObject):
    changed = pyqtSignal(bool)

    def __init__(self, interval_ms: int = 2000, parent=None):
        super().__init__(parent)
        self._last: bool | None = None
        self._timer = QTimer(self)
        self._timer.setInterval(interval_ms)
        self._timer.timeout.connect(self._poll)

    # ---------------------------------------------------------------- control
    def start(self) -> None:
        if not self._timer.isActive():
            self._poll()
            self._timer.start()

    def stop(self) -> None:
        self._timer.stop()

    # ---------------------------------------------------------------- poll
    def is_fullscreen_now(self) -> bool:
        try:
            out = subprocess.check_output(
                ["xprop", "-root", "_NET_ACTIVE_WINDOW"],
                stderr=subprocess.DEVNULL, text=True, timeout=3)
        except Exception:  # noqa: BLE001
            return False
        m = _ACTIVE_RE.search(out)
        if not m:
            return False
        win = m.group(0)
        try:
            state = subprocess.check_output(
                ["xprop", "-id", win, "_NET_WM_STATE"],
                stderr=subprocess.DEVNULL, text=True, timeout=3)
        except Exception:  # noqa: BLE001
            return False
        return _FULLSCREEN in state

    def _poll(self) -> None:
        fs = self.is_fullscreen_now()
        if fs != self._last:
            self._last = fs
            self.changed.emit(fs)
