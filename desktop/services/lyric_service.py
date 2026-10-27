"""Layanan lirik: menghubungkan MediaService (MPRIS) -> LRCLIB -> UI.

- Berjalan di thread latar (urllib diblokir di sana), sinyal dikirim
  kembali ke thread utama lewat Qt (queued).
- Pembatalan berbasis generasi: saat lagu berganti, fetch lama dibuang.
"""
from __future__ import annotations

import threading

from PyQt6.QtCore import QObject, pyqtSignal

from services.lyrics import LyricsCache, LyricsFetcher


class LyricsService(QObject):
    # (track_key, title, artist, lines[(ms, text)], synced: bool)
    lyrics_ready = pyqtSignal(str, str, str, list, bool)
    # (track_key, title, artist, reason)
    lyrics_error = pyqtSignal(str, str, str, str)

    def __init__(self, media, fetcher: LyricsFetcher | None = None, parent=None):
        super().__init__(parent)
        self.media = media
        self._fetcher = fetcher or LyricsFetcher()
        self._gen = 0
        self._current_key: str | None = None
        self._thread: threading.Thread | None = None
        media.changed.connect(self._on_media)

    # ---------------------------------------------------------------- API
    def track_key(self, data: dict) -> str:
        title = str(data.get("title") or "")
        artist = str(data.get("artist") or "")
        album = str(data.get("album") or "")
        length = float(data.get("length_us") or 0) / 1_000_000
        return LyricsCache.key(title, artist, album, length)

    def cancel(self) -> None:
        self._gen += 1

    def stop(self) -> None:
        self.cancel()
        self._thread = None

    # ---------------------------------------------------------------- logic
    def _on_media(self, data: dict) -> None:
        if not data or not data.get("available"):
            return
        key = self.track_key(data)
        if key == self._current_key:
            return
        self._current_key = key
        self.cancel()  # batalkan fetch lama
        gen = self._gen
        title = str(data.get("title") or "Tanpa judul")
        artist = str(data.get("artist") or "")
        length_s = float(data.get("length_us") or 0) / 1_000_000

        def work():
            try:
                lines = self._fetcher.get_synced(
                    title, artist, str(data.get("album") or ""), length_s)
            except Exception as exc:  # noqa: BLE001
                if gen == self._gen:
                    self.lyrics_error.emit(key, title, artist, str(exc))
                return
            if gen == self._gen:
                self.lyrics_ready.emit(key, title, artist, lines, bool(lines))

        thread = threading.Thread(target=work, daemon=True, name="lyrics")
        self._thread = thread
        thread.start()
