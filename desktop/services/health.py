"""Status koneksi nyata untuk dashboard (9Router, Hermes, Telegram).

Polling hanya saat dashboard terlihat. Hasil probe diberi stempel waktu agar
UI bisa membedakan "connected" dari "data lama".
"""
from __future__ import annotations

import time

from PyQt6.QtCore import QObject, QTimer, pyqtSignal

from services.probes import gateway_status, has_command, router_status


class HealthService(QObject):
    updated = pyqtSignal(dict)

    def __init__(self, cfg, parent=None):
        super().__init__(parent)
        self.cfg = cfg
        self._consumers: set[str] = set()
        self._timer = QTimer(self)
        self._timer.setInterval(12_000)
        self._timer.timeout.connect(self.refresh)
        self._last: dict = {}

    @property
    def active(self) -> bool:
        return self._timer.isActive()

    def acquire(self, key: str) -> None:
        self._consumers.add(key)
        if not self._timer.isActive():
            self.refresh()
            self._timer.start()

    def release(self, key: str) -> None:
        self._consumers.discard(key)
        if not self._consumers:
            self._timer.stop()

    def refresh(self) -> None:
        router = router_status(self.cfg.model_base)
        gw = gateway_status()
        now = time.time()
        data = {
            "router": router,
            "gateway": gw,
            "hermes_cli": has_command(self.cfg.hermes_command),
            "model": self.cfg.model_name,
            "checked_at": now,
        }
        self._last = data
        self.updated.emit(dict(data))
