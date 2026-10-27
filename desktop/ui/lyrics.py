"""Panel lirik sinkron (PRD 4.3).

Menampilkan baris LRC; baris aktif (sedang dinyanyikan) tampil lebih
terang/tebal dan di-scroll agar selalu di tengah. Ambil data dari
LyricsService; posisi dibaca dari MediaService (MPRIS).
"""
from __future__ import annotations

from PyQt6.QtCore import Qt, QTimer
from PyQt6.QtWidgets import (
    QFrame, QHBoxLayout, QLabel, QScrollArea, QSizePolicy, QVBoxLayout, QWidget,
)

from app import theme
from ui.components import EllipsisLabel, IconButton, icon_label
from ui.icons import make_icon

_ROW_H = 24
_PAD = 6


class _Row(QLabel):
    def __init__(self, text: str, active: bool, parent=None):
        super().__init__(text, parent)
        self.setAttribute(Qt.WidgetAttribute.WA_TransparentForMouseEvents, True)
        self.setWordWrap(False)
        self.setAlignment(Qt.AlignmentFlag.AlignLeft | Qt.AlignmentFlag.AlignVCenter)
        self.setStyleSheet(self._css(active))
        self.setFixedHeight(_ROW_H)

    @staticmethod
    def _css(active: bool) -> str:
        if active:
            return (
                "color:%s; font-size:13px; font-weight:700;"
                % theme.CYAN.name())
        return (
            "color:%s; font-size:11px; font-weight:400;"
            % theme.MUTED.name())


class LyricsPanel(QFrame):
    def __init__(self, media, lyric_service, parent=None):
        super().__init__(parent)
        self.media = media
        self.lyrics = lyric_service
        self.setObjectName("lyrics")
        self.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Fixed)
        self._lines: list[tuple[int, str]] = []
        self._active = -1
        self._pos_anchor: tuple[float, int] | None = None
        self._playing = False
        # Status buka eksplisit: ``isVisible()`` selalu False saat halaman
        # Chat sedang tidak ditampilkan, jadi tidak boleh dipakai sebagai
        # penentu toggle.
        self._open = False

        v = QVBoxLayout(self)
        v.setContentsMargins(10, 6, 10, 8)
        v.setSpacing(4)

        # header
        top = QHBoxLayout()
        top.setSpacing(7)
        icon = icon_label("music", theme.CYAN, 15)
        top.addWidget(icon)
        self.title = EllipsisLabel("Lirik")
        self.title.setStyleSheet(
            f"color:{theme.TEXT.name()}; font-size:11px; font-weight:600;")
        top.addWidget(self.title, 1)
        self.sub = QLabel("")
        self.sub.setStyleSheet(f"color:{theme.DIM.name()}; font-size:9px;")
        top.addWidget(self.sub)
        self.close_btn = IconButton("chevron-up", "Tutup lirik", 22, 11)
        top.addWidget(self.close_btn)
        v.addLayout(top)

        # area scroll
        self._scroll = QScrollArea()
        self._scroll.setWidgetResizable(True)
        self._scroll.setHorizontalScrollBarPolicy(
            Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        self._scroll.setVerticalScrollBarPolicy(
            Qt.ScrollBarPolicy.ScrollBarAsNeeded)
        self._scroll.setStyleSheet("background:transparent; border:none;")
        self._inner = QWidget()
        self._inner.setStyleSheet("background:transparent;")
        self._col = QVBoxLayout(self._inner)
        self._col.setContentsMargins(2, _PAD, 6, _PAD)
        self._col.setSpacing(2)
        self._col.addStretch(1)
        self._scroll.setWidget(self._inner)
        self._scroll.setFixedHeight(120)
        v.addWidget(self._scroll)

        self._empty = QLabel("Lirik tidak ditemukan")
        self._empty.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self._empty.setStyleSheet(f"color:{theme.DIM.name()}; font-size:11px;")
        self._empty.setFixedHeight(120)
        v.addWidget(self._empty)
        # Hanya satu dari scroll/empty yang boleh tampil; menampilkan keduanya
        # membuat panel dua kali lebih tinggi dan mendesak isi Chat.
        self._empty.setVisible(False)

        self.setVisible(False)

        # posisi
        self._timer = QTimer(self)
        self._timer.setInterval(1000)
        self._timer.timeout.connect(self._tick)
        media.changed.connect(self._on_media)
        lyric_service.lyrics_ready.connect(self._on_lyrics)
        lyric_service.lyrics_error.connect(self._on_error)

    # ------------------------------------------------------------ public
    def open(self) -> None:
        self._open = True
        self.setVisible(True)
        if self._lines:
            self._timer.start()

    def close_panel(self) -> None:
        self._open = False
        self._timer.stop()
        self.setVisible(False)

    def is_open(self) -> bool:
        return self._open

    def toggle(self) -> bool:
        if self._open:
            self.close_panel()
            return False
        self.open()
        return True

    # ------------------------------------------------------------ slots
    def _on_media(self, data: dict) -> None:
        if not data or not data.get("available"):
            self._playing = False
            self._pos_anchor = None
            return
        self._playing = bool(data.get("playing"))
        pos = int(data.get("position_us") or 0)
        self._pos_anchor = (self._now(), pos)

    def _now(self) -> float:
        import time
        return time.monotonic()

    def _current_pos_us(self) -> int:
        if self._pos_anchor is None or not self._playing:
            return 0
        t0, p0 = self._pos_anchor
        dt = self._now() - t0
        if dt < 0:
            return p0
        return p0 + int(dt * 1_000_000)

    def _on_lyrics(self, key, title, artist, lines, synced):
        self._lines = list(lines or [])
        self._active = -1

        # bersihkan baris lama
        while self._col.count() > 1:  # sisakan stretch
            item = self._col.takeAt(0)
            w = item.widget()
            if w is not None:
                w.deleteLater()

        if not self._lines:
            self._empty.setVisible(True)
            self._scroll.setVisible(False)
            self.sub.setText("")
            self.title.setText("Lirik")
            return

        self._empty.setVisible(False)
        self._scroll.setVisible(True)
        self.title.setText(f"{title} — {artist}" if artist else title)
        self.sub.setText("sinkron" if synced else "statis")
        for _ms, text in self._lines:
            self._col.insertWidget(self._col.count() - 1, _Row(text, False))

        if self._open:
            self._timer.start()

    def _on_error(self, key, title, artist, reason):
        if not self._lines:
            self._empty.setText("Gagal mengambil lirik")
            self._empty.setVisible(True)
            self._scroll.setVisible(False)
            self.title.setText(title or "Lirik")

    def _tick(self) -> None:
        if not self._lines:
            return
        pos = self._current_pos_us()
        idx = -1
        for i, (ms, _text) in enumerate(self._lines):
            if ms * 1000 <= pos:
                idx = i
            else:
                break
        if idx == self._active:
            return
        self._active = idx
        for i in range(self._col.count() - 1):
            w = self._col.itemAt(i).widget()
            if isinstance(w, _Row):
                w.setStyleSheet(_Row._css(i == idx))
                if i == idx and idx >= 0:
                    self._scroll.ensureWidgetVisible(w, 0, int(_ROW_H / 2))
