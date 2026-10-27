"""Media aktivitas nyata via MPRIS (QtDBus).

Tidak ada pemutar palsu. Jika tidak ada pemutar MPRIS, widget media tidak
ditampilkan sama sekali. Kontrol memakai method MPRIS standar.
"""
from __future__ import annotations

from PyQt6.QtCore import QObject, QTimer, pyqtSignal
from PyQt6.QtDBus import QDBusConnection, QDBusInterface, QDBusVariant

MPRIS_PREFIX = "org.mpris.MediaPlayer2"
MPRIS_PATH = "/org/mpris/MediaPlayer2"
PLAYER_IFACE = "org.mpris.MediaPlayer2.Player"


def _unwrap(value):
    if isinstance(value, QDBusVariant):
        return _unwrap(value.variant())
    return value


def _first(value) -> str:
    value = _unwrap(value)
    if isinstance(value, (list, tuple)):
        return str(value[0]) if value else ""
    return str(value or "")


class MediaService(QObject):
    changed = pyqtSignal(dict)

    def __init__(self, parent=None):
        super().__init__(parent)
        self._bus = QDBusConnection.sessionBus()
        self._consumers: set[str] = set()
        self._timer = QTimer(self)
        self._timer.setInterval(2000)
        self._timer.timeout.connect(self.refresh)
        self._last: dict = {}
        self._player: str | None = None

    # ------------------------------------------------------------ gating
    def acquire(self, key: str) -> None:
        self._consumers.add(key)
        if not self._timer.isActive():
            self.refresh()
            self._timer.start()

    def release(self, key: str) -> None:
        self._consumers.discard(key)
        if not self._consumers:
            self._timer.stop()

    @property
    def active(self) -> bool:
        return self._timer.isActive()

    def snapshot(self) -> dict:
        return dict(self._last) if self._last else {"available": False}

    def stop(self) -> None:
        self._consumers.clear()
        self._timer.stop()

    # ------------------------------------------------------------ reading
    def _players(self) -> list[str]:
        if not self._bus.isConnected():
            return []
        reply = self._bus.interface().registeredServiceNames()
        if not reply.isValid():
            return []
        names = [str(n) for n in reply.value()]
        return sorted(n for n in names if n.startswith(MPRIS_PREFIX))

    def _iface(self, service: str) -> QDBusInterface:
        return QDBusInterface(service, MPRIS_PATH, PLAYER_IFACE, self._bus)

    def _read(self, service: str) -> dict:
        iface = self._iface(service)
        if not iface.isValid():
            return {}
        status = str(_unwrap(iface.property("PlaybackStatus")) or "Stopped")
        meta = _unwrap(iface.property("Metadata"))
        if not isinstance(meta, dict):
            meta = {}
        length = _unwrap(meta.get("mpris:length")) or 0
        try:
            length_us = int(length)
        except Exception:
            length_us = 0
        try:
            pos_us = int(_unwrap(iface.property("Position")) or 0)
        except Exception:
            pos_us = 0
        return {
            "available": True,
            "player": service.split(".", 3)[-1].split(".")[0] if service else "",
            "service": service,
            "title": str(_unwrap(meta.get("xesam:title")) or "Tanpa judul"),
            "artist": _first(meta.get("xesam:artist")) or "Artis tidak dikenal",
            "album": str(_unwrap(meta.get("xesam:album")) or ""),
            "playing": status == "Playing",
            "status": status,
            "length_us": max(0, length_us),
            "position_us": max(0, pos_us),
        }

    def refresh(self) -> None:
        players = self._players()
        chosen = None
        chosen_data = {}
        for name in players:
            data = self._read(name)
            if not data:
                continue
            if chosen is None:
                chosen, chosen_data = name, data
            if data.get("playing"):
                chosen, chosen_data = name, data
                break
        out = chosen_data if chosen else {"available": False}
        if out != self._last:
            self._last = out
            self._player = chosen
            self.changed.emit(dict(out))

    # ------------------------------------------------------------ control
    def _call(self, method: str) -> None:
        if not self._player:
            return
        iface = self._iface(self._player)
        if iface.isValid():
            iface.call(method)
            QTimer.singleShot(180, self.refresh)

    def play_pause(self) -> None:
        self._call("PlayPause")

    def next(self) -> None:
        self._call("Next")

    def previous(self) -> None:
        self._call("Previous")
