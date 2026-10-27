"""Dialog timer cepat untuk Pochi.

Timer adalah fitur lokal (bukan data AI): dihitung dari ``time.monotonic`` dan
ditampilkan sebagai widget kontekstual di island serta kartu Active Tasks.
"""
from __future__ import annotations

import time

from PyQt6.QtCore import Qt, pyqtSignal
from PyQt6.QtWidgets import (
    QButtonGroup, QHBoxLayout, QLabel, QLineEdit, QPushButton, QSpinBox,
)

from app import theme
from ui.components import DialogShell

PRESETS = (5, 10, 25, 45)


class TimerDialog(DialogShell):
    started = pyqtSignal()

    def __init__(self, controller, parent=None):
        super().__init__("Timer", "Pengingat lokal di dalam island", parent,
                         width=400, height=360)
        self.controller = controller
        self.setWindowTitle("Timer Pochi")

        label = QLabel("Durasi cepat")
        label.setObjectName("dim")
        self.add_widget(label)

        row = QHBoxLayout()
        row.setSpacing(6)
        self.group = QButtonGroup(self)
        self.group.setExclusive(True)
        self._preset_buttons = []
        for minutes in PRESETS:
            b = QPushButton(f"{minutes} mnt")
            b.setObjectName("segment")
            b.setCheckable(True)
            b.setCursor(Qt.CursorShape.PointingHandCursor)
            b.setProperty("minutes", minutes)
            b.clicked.connect(
                lambda _=False, m=minutes: self.minutes.setValue(m))
            self.group.addButton(b)
            self._preset_buttons.append(b)
            row.addWidget(b)
        self.add_layout(row)

        form = QHBoxLayout()
        form.setSpacing(8)
        lab = QLabel("Menit")
        lab.setObjectName("dim")
        form.addWidget(lab)
        self.minutes = QSpinBox()
        self.minutes.setRange(1, 180)
        self.minutes.setValue(10)
        self.minutes.setSuffix(" mnt")
        self.minutes.setMinimumWidth(110)
        self.minutes.valueChanged.connect(self._sync_presets)
        form.addWidget(self.minutes)
        form.addStretch(1)
        self.add_layout(form)

        lab2 = QLabel("Label (opsional)")
        lab2.setObjectName("dim")
        self.add_widget(lab2)
        self.label = QLineEdit()
        self.label.setPlaceholderText("Mis. istirahat, cek build…")
        self.label.returnPressed.connect(self._start)
        self.add_widget(self.label)

        self.preview = QLabel("")
        self.preview.setStyleSheet(
            f"color:{theme.AMBER.name()}; font-size:12px; font-weight:600;")
        self.add_widget(self.preview)

        active = [t for t in controller.tasks if not t.done]
        if active:
            self.add_widget(self._divider_text(
                f"{len(active)} timer aktif akan tetap berjalan."))
        self.add_widget(QLabel(""))

        foot = QHBoxLayout()
        foot.setSpacing(8)
        foot.addStretch(1)
        cancel = QPushButton("Batal")
        cancel.setObjectName("ghost")
        cancel.clicked.connect(self.reject)
        foot.addWidget(cancel)
        start = QPushButton("Mulai timer")
        start.setObjectName("primary")
        start.setDefault(True)
        start.clicked.connect(self._start)
        foot.addWidget(start)
        self.body.addLayout(foot)

        self._sync_presets()
        self.minutes.valueChanged.connect(self._update_preview)
        self._update_preview()

    # --------------------------------------------------------------- helpers
    @staticmethod
    def _divider_text(text: str) -> QLabel:
        lab = QLabel(text)
        lab.setObjectName("dim")
        lab.setWordWrap(True)
        return lab

    def _sync_presets(self):
        value = self.minutes.value()
        for b in self._preset_buttons:
            b.setChecked(int(b.property("minutes")) == value)
        self._update_preview()

    def _update_preview(self):
        seconds = self.minutes.value() * 60
        finish = time.localtime(time.time() + seconds)
        self.preview.setText(
            f"Selesai sekitar {time.strftime('%H:%M', finish)} "
            f"· {self.minutes.value()} menit")

    def _start(self):
        self.controller.add_timer(self.minutes.value() * 60,
                                  self.label.text().strip())
        self.started.emit()
        self.accept()
