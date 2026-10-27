"""Widget aktivitas kontekstual — hanya muncul saat relevan.

Tidak ada data palsu: kartu AI hanya saat ada tool/proses nyata, kartu media
hanya saat ada pemutar MPRIS, dan kartu timer hanya saat timer aktif.
"""
from __future__ import annotations

from PyQt6.QtCore import Qt, QTimer
from PyQt6.QtWidgets import (
    QFrame, QHBoxLayout, QLabel, QProgressBar, QSizePolicy, QVBoxLayout, QWidget,
)

from app import theme
from app.state import Activity, Task
from services.lyric_service import LyricsService
from ui.components import Dot, EllipsisLabel, IconButton, PulseDots, icon_label
from ui.icons import make_icon
from ui.lyrics import LyricsPanel


class _Strip(QFrame):
    def __init__(self, parent=None):
        super().__init__(parent)
        self.setObjectName("strip")
        self.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Fixed)


class AIActivityCard(_Strip):
    cancelled = None

    def __init__(self, controller, reduce_motion=False, parent=None):
        super().__init__(parent)
        self.controller = controller
        h = QHBoxLayout(self)
        h.setContentsMargins(10, 8, 9, 8)
        h.setSpacing(9)
        self.icon = icon_label("spark", theme.PURPLE, 16)
        h.addWidget(self.icon)
        v = QVBoxLayout()
        v.setSpacing(1)
        self.title = EllipsisLabel("Bekerja")
        self.title.setStyleSheet(
            f"color:{theme.TEXT.name()}; font-size:11px; font-weight:600;")
        self.detail = EllipsisLabel("")
        self.detail.setStyleSheet(
            f"color:{theme.MUTED.name()}; font-size:10px;")
        self.detail.setVisible(False)
        v.addWidget(self.title)
        v.addWidget(self.detail)
        h.addLayout(v, 1)
        self.dots = PulseDots(theme.PURPLE, 20, reduce_motion)
        h.addWidget(self.dots)
        self.progress = QProgressBar()
        self.progress.setRange(0, 100)
        self.progress.setTextVisible(False)
        self.progress.setFixedWidth(64)
        self.progress.setFixedHeight(4)
        h.addWidget(self.progress)
        self.stop_btn = IconButton("stop", "Hentikan", 26, 12)
        self.stop_btn.clicked.connect(self.controller.cancel)
        h.addWidget(self.stop_btn)
        self.setVisible(False)
        self._activity: Activity | None = None

    def set_activity(self, act: Activity | None) -> None:
        self._activity = act
        if act is None:
            self.setVisible(False)
            return
        color = theme.GREEN if act.state == "done" else (
            theme.RED if act.state == "error" else theme.PURPLE)
        icon = {"done": "check", "error": "alert"}.get(act.state, "terminal")
        if act.kind == "thinking":
            icon = "spark"
        self.icon.setPixmap(make_icon(icon, color, 16).pixmap(16, 16))
        self.title.setText(act.title or "Bekerja")
        self.detail.setText(act.detail or "")
        self.detail.setVisible(bool(act.detail))
        self.dots.set_color(color)
        self.dots.setVisible(act.state == "running")
        self.stop_btn.setVisible(act.state == "running")
        if act.progress is None:
            if act.state == "running":
                self.progress.setRange(0, 0)
            else:
                self.progress.setRange(0, 100)
                self.progress.setValue(100)
        else:
            self.progress.setRange(0, 100)
            self.progress.setValue(int(max(0, min(1, act.progress)) * 100))
        self.progress.setVisible(True)
        self.setVisible(True)


class MediaCard(_Strip):
    def __init__(self, media, on_lyrics=None, parent=None):
        super().__init__(parent)
        self.media = media
        h = QHBoxLayout(self)
        h.setContentsMargins(10, 7, 8, 7)
        h.setSpacing(9)
        self.icon = icon_label("music", theme.CYAN, 16)
        h.addWidget(self.icon)
        v = QVBoxLayout()
        v.setSpacing(1)
        self.title = EllipsisLabel("")
        self.title.setStyleSheet(
            f"color:{theme.TEXT.name()}; font-size:11px; font-weight:600;")
        self.artist = EllipsisLabel("")
        self.artist.setStyleSheet(
            f"color:{theme.MUTED.name()}; font-size:10px;")
        v.addWidget(self.title)
        v.addWidget(self.artist)
        h.addLayout(v, 1)
        self.prev = IconButton("prev", "Sebelumnya", 26, 12)
        self.play = IconButton("pause", "Putar / jeda", 28, 13, theme.TEXT)
        self.next = IconButton("next", "Berikutnya", 26, 12)
        self.lyrics_btn = IconButton(
            "lyrics", "Tampilkan / sembunyikan lirik", 26, 12)
        if on_lyrics is not None:
            self.lyrics_btn.clicked.connect(on_lyrics)
        else:
            self.lyrics_btn.setEnabled(False)
        for b in (self.prev, self.play, self.next, self.lyrics_btn):
            h.addWidget(b)
        self.prev.clicked.connect(media.previous)
        self.play.clicked.connect(media.play_pause)
        self.next.clicked.connect(media.next)
        self.setVisible(False)

    def set_media(self, data: dict) -> None:
        if not data or not data.get("available"):
            self.setVisible(False)
            return
        self.title.setText(str(data.get("title") or "Tanpa judul"))
        artist = str(data.get("artist") or "")
        album = str(data.get("album") or "")
        detail = " · ".join(x for x in (artist, album) if x)
        self.artist.setText(detail or "Media")
        self.play.set_icon_name("pause" if data.get("playing") else "play")
        self.setVisible(True)


class TimerCard(_Strip):
    def __init__(self, controller, parent=None):
        super().__init__(parent)
        self.controller = controller
        self._task: Task | None = None
        h = QHBoxLayout(self)
        h.setContentsMargins(10, 8, 9, 8)
        h.setSpacing(9)
        h.addWidget(icon_label("timer", theme.AMBER, 16))
        v = QVBoxLayout()
        v.setSpacing(3)
        self.title = EllipsisLabel("Timer")
        self.title.setStyleSheet(
            f"color:{theme.TEXT.name()}; font-size:11px; font-weight:600;")
        v.addWidget(self.title)
        self.progress = QProgressBar()
        self.progress.setObjectName("amber")
        self.progress.setRange(0, 100)
        self.progress.setTextVisible(False)
        self.progress.setFixedHeight(4)
        v.addWidget(self.progress)
        h.addLayout(v, 1)
        self.time = QLabel("0:00")
        self.time.setStyleSheet(
            f"color:{theme.AMBER.name()}; font-size:12px; font-weight:600;")
        h.addWidget(self.time)
        self.cancel = IconButton("close", "Batalkan timer", 24, 11)
        self.cancel.clicked.connect(self._cancel)
        h.addWidget(self.cancel)
        self.setVisible(False)

    def _cancel(self):
        if self._task is not None:
            self.controller.remove_task(self._task.id)

    def set_task(self, task: Task | None, extra: int = 0) -> None:
        self._task = task
        if task is None:
            self.setVisible(False)
            return
        label = task.label if not extra else f"{task.label}  +{extra}"
        self.title.setText(label)
        self.progress.setValue(int((1.0 - task.fraction) * 100))
        self.time.setText(self.controller.format_task(task))
        self.setVisible(True)

    def tick(self) -> None:
        if self._task is None:
            return
        task = self._task
        if task.done:
            self.time.setText("Selesai")
            self.progress.setValue(100)
            return
        self.time.setText(self.controller.format_task(task))
        self.progress.setValue(int((1.0 - task.fraction) * 100))


class ActivityArea(QWidget):
    """Tumpukan kartu aktivitas; menyembunyikan dirinya saat kosong."""

    def __init__(self, controller, media, reduce_motion=False, parent=None):
        super().__init__(parent)
        self.controller = controller
        self.media = media
        v = QVBoxLayout(self)
        v.setContentsMargins(0, 0, 0, 0)
        v.setSpacing(6)
        self.ai = AIActivityCard(controller, reduce_motion)
        self.lyrics_service = LyricsService(media, parent=self)
        self.lyrics_panel = LyricsPanel(media, self.lyrics_service)
        self.media_card = MediaCard(media, on_lyrics=self._toggle_lyrics)
        self.timer = TimerCard(controller)
        v.addWidget(self.ai)
        v.addWidget(self.media_card)
        v.addWidget(self.lyrics_panel)
        v.addWidget(self.timer)
        self.setVisible(False)

        self._tick = QTimer(self)
        self._tick.setInterval(1000)
        self._tick.timeout.connect(self._on_tick)

        controller.activityChanged.connect(self._on_activity)
        controller.tasksChanged.connect(self._on_tasks)
        media.changed.connect(self._on_media)
        self.lyrics_panel.close_btn.clicked.connect(self.lyrics_panel.close_panel)

    def _toggle_lyrics(self) -> None:
        self.lyrics_panel.toggle()
        self._update_visible()

    def start_polling(self) -> None:
        self.media.acquire("island")
        self._on_media(self.media.snapshot())

    def stop_polling(self) -> None:
        self.media.release("island")

    def _update_visible(self):
        self.setVisible(any(not w.isHidden() for w in
                            (self.ai, self.media_card, self.timer,
                             self.lyrics_panel)))

    def _on_activity(self, act):
        self.ai.set_activity(act)
        self._update_visible()

    def _on_media(self, data):
        self.media_card.set_media(data)
        self._update_visible()

    def _on_tasks(self):
        tasks = [t for t in self.controller.tasks if not t.done]
        if tasks:
            tasks.sort(key=lambda t: t.remaining())
            self.timer.set_task(tasks[0], max(0, len(tasks) - 1))
            if not self._tick.isActive():
                self._tick.start()
        else:
            self.timer.set_task(None)
            self._tick.stop()
        self._update_visible()

    def _on_tick(self):
        self._on_tasks()
