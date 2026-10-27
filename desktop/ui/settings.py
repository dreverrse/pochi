"""Dialog pengaturan Pochi — tampilan, posisi, interaksi, dan backend.

Nilai disimpan ke ``~/.config/pochi-desktop/config.json``. Dialog ini tidak
pernah menulis API key ke config: key OpenRouter disimpan terpisah di
``openrouter.key`` (izin 0600), sedangkan kredensial lain tetap dibaca runtime
dari environment / ``~/.hermes/.env``.
"""
from __future__ import annotations

from urllib.parse import urlsplit

from PyQt6.QtCore import Qt, pyqtSignal
from PyQt6.QtGui import QKeySequence
from PyQt6.QtWidgets import (
    QCheckBox, QComboBox, QDoubleSpinBox, QFormLayout, QFrame, QHBoxLayout,
    QKeySequenceEdit, QLabel, QLineEdit, QMessageBox, QPushButton, QScrollArea,
    QSpinBox, QVBoxLayout, QWidget,
)

from app import theme
from app.config import ANCHORS, BACKENDS
from services.probes import openrouter_key_hint, save_openrouter_key
from ui.components import DialogShell, Divider

ANCHOR_LABELS = {
    "top-center": "Tengah atas",
    "top-left": "Kiri atas",
    "top-right": "Kanan atas",
}
BACKEND_LABELS = {
    "auto": "Auto (Hermes → 9Router → OpenRouter → DEMO)",
    "hermes": "Hermes ACP saja",
    "9router": "9Router langsung",
    "openrouter": "OpenRouter (cloud)",
    "demo": "DEMO (tanpa backend nyata)",
}


class SettingsDialog(DialogShell):
    saved = pyqtSignal()

    def __init__(self, cfg, parent=None, controller=None):
        super().__init__("Pengaturan", "Tampilan, posisi, dan koneksi Pochi",
                         parent, width=500, height=600)
        self.cfg = cfg
        self.controller = controller
        self._reset_position = False
        self._clear_or_key = False
        self.memory = getattr(controller, "memory", None)
        self.setWindowTitle("Pengaturan Pochi")

        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        scroll.setFrameShape(QFrame.Shape.NoFrame)
        scroll.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        page = QWidget()
        v = QVBoxLayout(page)
        v.setContentsMargins(0, 0, 6, 0)
        v.setSpacing(10)

        # ---------------------------------------------------------- tampilan
        v.addWidget(self._section("Tampilan & posisi"))
        form = QFormLayout()
        form.setHorizontalSpacing(14)
        form.setVerticalSpacing(9)
        form.setLabelAlignment(Qt.AlignmentFlag.AlignLeft
                               | Qt.AlignmentFlag.AlignVCenter)

        self.anchor = QComboBox()
        for key in ANCHORS:
            self.anchor.addItem(ANCHOR_LABELS.get(key, key), key)
        self._select_data(self.anchor, cfg.anchor)
        form.addRow("Jangkar layar", self.anchor)

        self.y_offset = QSpinBox()
        self.y_offset.setRange(0, 300)
        self.y_offset.setSuffix(" px")
        self.y_offset.setValue(cfg.y_offset)
        self.y_offset.setToolTip("Jarak dari tepi atas area kerja.")
        form.addRow("Jarak atas", self.y_offset)

        self.scale = QDoubleSpinBox()
        self.scale.setRange(0.75, 1.35)
        self.scale.setSingleStep(0.05)
        self.scale.setDecimals(2)
        self.scale.setSuffix("×")
        self.scale.setValue(cfg.scale)
        self.scale.setToolTip("Skala pil dan panel (0.75–1.35).")
        form.addRow("Skala", self.scale)

        pos_row = QWidget()
        ph = QHBoxLayout(pos_row)
        ph.setContentsMargins(0, 0, 0, 0)
        ph.setSpacing(8)
        self.pos_label = QLabel(
            f"{cfg.pos_x}, {cfg.pos_y}" if cfg.has_custom_position()
            else "Mengikuti jangkar")
        self.pos_label.setObjectName("dim")
        ph.addWidget(self.pos_label, 1)
        self.reset_pos = QPushButton("Reset")
        self.reset_pos.setObjectName("ghost")
        self.reset_pos.setToolTip("Kembalikan ke posisi jangkar")
        self.reset_pos.clicked.connect(self._reset_pos_clicked)
        ph.addWidget(self.reset_pos)
        form.addRow("Posisi bebas", pos_row)

        self.auto_collapse = QSpinBox()
        self.auto_collapse.setRange(0, 600)
        self.auto_collapse.setSuffix(" dtk")
        self.auto_collapse.setSpecialValueText("Nonaktif")
        self.auto_collapse.setValue(int(cfg.auto_collapse_seconds))
        self.auto_collapse.setToolTip(
            "Tutup panel otomatis setelah tidak ada aktivitas.")
        form.addRow("Tutup otomatis", self.auto_collapse)

        self.reduce_motion = QCheckBox("Kurangi animasi")
        self.reduce_motion.setChecked(cfg.reduce_motion)
        form.addRow("", self.reduce_motion)

        self.always_on_top = QCheckBox("Selalu di atas")
        self.always_on_top.setChecked(cfg.always_on_top)
        self.always_on_top.setToolTip(
            "Bergantung pada dukungan window manager (xfwm4).")
        form.addRow("", self.always_on_top)
        v.addLayout(form)
        v.addWidget(Divider())

        # -------------------------------------------------------- interaksi
        v.addWidget(self._section("Interaksi"))
        iform = QFormLayout()
        iform.setHorizontalSpacing(14)
        iform.setVerticalSpacing(9)
        self.toggle_key = QKeySequenceEdit()
        try:
            self.toggle_key.setMaximumSequenceLength(1)
        except Exception:
            pass
        seq = QKeySequence(cfg.toggle_key)
        if not seq.isEmpty():
            self.toggle_key.setKeySequence(seq)
        iform.addRow("Pintasan buka/tutup", self.toggle_key)
        v.addLayout(iform)
        note = QLabel(
            "Pintasan berlaku selama aplikasi berjalan (application shortcut). "
            "Hotkey global X11 belum diaktifkan pada versi ini.")
        note.setWordWrap(True)
        note.setObjectName("dim")
        v.addWidget(note)
        v.addWidget(Divider())

        # ----------------------------------------------------------- backend
        v.addWidget(self._section("Backend & model"))
        bform = QFormLayout()
        bform.setHorizontalSpacing(14)
        bform.setVerticalSpacing(9)
        self.backend = QComboBox()
        for key in BACKENDS:
            self.backend.addItem(BACKEND_LABELS.get(key, key), key)
        self._select_data(self.backend, cfg.backend)
        bform.addRow("Backend", self.backend)

        self.model = QLineEdit(cfg.model_name)
        self.model.setPlaceholderText("inc/mercury-3")
        bform.addRow("Nama model", self.model)

        self.base = QLineEdit(cfg.model_base)
        self.base.setPlaceholderText("http://127.0.0.1:20128/v1")
        bform.addRow("9Router base URL", self.base)

        self.or_model = QLineEdit(cfg.openrouter_model)
        self.or_model.setPlaceholderText("openrouter/auto")
        bform.addRow("Model OpenRouter", self.or_model)

        self.or_key = QLineEdit()
        self.or_key.setEchoMode(QLineEdit.EchoMode.Password)
        self.or_key.setPlaceholderText(
            openrouter_key_hint() or "Belum ada key — tempel di sini")
        self.or_key.textEdited.connect(self._on_or_key_edited)
        key_row = QHBoxLayout()
        key_row.setSpacing(6)
        key_row.addWidget(self.or_key, 1)
        self.clear_or_key = QPushButton("Hapus")
        self.clear_or_key.setObjectName("ghost")
        self.clear_or_key.setToolTip("Hapus API key OpenRouter yang tersimpan")
        self.clear_or_key.clicked.connect(self._clear_openrouter_key)
        key_row.addWidget(self.clear_or_key)
        bform.addRow("OpenRouter API key", key_row)

        self.timeout = QSpinBox()
        self.timeout.setRange(15, 300)
        self.timeout.setSuffix(" dtk")
        self.timeout.setValue(cfg.request_timeout)
        bform.addRow("Timeout permintaan", self.timeout)
        v.addLayout(bform)

        note2 = QLabel(
            "Kredensial tidak ditulis ke config.json. Key OpenRouter disimpan "
            "terpisah di ~/.config/pochi-desktop/openrouter.key (izin 0600); "
            "environment OPENROUTER_API_KEY tetap diprioritaskan. 9Router "
            "memakai key dari environment atau ~/.hermes/.env; Hermes membaca "
            "konfigurasinya sendiri. Perubahan backend diterapkan saat Simpan.")
        note2.setWordWrap(True)
        note2.setObjectName("dim")
        v.addWidget(note2)

        if self.memory is not None:
            v.addWidget(Divider())
            v.addWidget(self._section("Memori lintas backend"))
            mem_row = QHBoxLayout()
            mem_row.setSpacing(8)
            self.memory_label = QLabel(
                f"{self.memory.count} giliran tersimpan · dipakai OpenRouter, "
                "Hermes, dan 9Router")
            self.memory_label.setWordWrap(True)
            self.memory_label.setObjectName("dim")
            mem_row.addWidget(self.memory_label, 1)
            self.forget_btn = QPushButton("Hapus memori")
            self.forget_btn.setObjectName("ghost")
            self.forget_btn.setToolTip(
                "Hapus riwayat yang dipakai sebagai konteks sesi berikutnya")
            self.forget_btn.clicked.connect(self._forget_memory)
            mem_row.addWidget(self.forget_btn)
            v.addLayout(mem_row)

        self.error = QLabel("")
        self.error.setWordWrap(True)
        self.error.setStyleSheet(
            f"color:{theme.RED.name()}; font-size:11px;")
        self.error.setVisible(False)
        v.addWidget(self.error)
        v.addStretch(1)
        scroll.setWidget(page)
        self.add_widget(scroll)

        # ------------------------------------------------------------ footer
        foot = QHBoxLayout()
        foot.setSpacing(8)
        foot.addStretch(1)
        cancel = QPushButton("Batal")
        cancel.setObjectName("ghost")
        cancel.clicked.connect(self.reject)
        foot.addWidget(cancel)
        save = QPushButton("Simpan")
        save.setObjectName("primary")
        save.setDefault(True)
        save.clicked.connect(self.accept)
        foot.addWidget(save)
        self.body.addLayout(foot)

    # --------------------------------------------------------------- helpers
    @staticmethod
    def _section(text: str) -> QLabel:
        lab = QLabel(text.upper())
        lab.setObjectName("eyebrow")
        return lab

    @staticmethod
    def _select_data(combo: QComboBox, value: str) -> None:
        index = combo.findData(value)
        if index >= 0:
            combo.setCurrentIndex(index)

    def _reset_pos_clicked(self):
        self._reset_position = True
        self.pos_label.setText("Mengikuti jangkar (saat Simpan)")

    def _on_or_key_edited(self, _text: str):
        # Mengetik key baru membatalkan permintaan hapus.
        self._clear_or_key = False

    def _clear_openrouter_key(self):
        self.or_key.clear()
        self._clear_or_key = True
        self.or_key.setPlaceholderText("Akan dihapus saat Simpan")

    def _forget_memory(self):
        if self.memory is None:
            return
        answer = QMessageBox.question(
            self, "Hapus memori",
            "Pochi akan lupa percakapan dari sesi lama. Percakapan yang "
            "sedang tampil tidak berubah. Lanjutkan?",
            QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No,
            QMessageBox.StandardButton.No)
        if answer != QMessageBox.StandardButton.Yes:
            return
        if self.controller is not None:
            self.controller.forget_memory()
        else:
            self.memory.clear()
        self.memory_label.setText(
            f"{self.memory.count} giliran tersimpan · dipakai OpenRouter, "
            "Hermes, dan 9Router")

    def accept(self):
        key = self.toggle_key.keySequence().toString().strip()
        if not key:
            self._fail("Pintasan tidak boleh kosong.")
            return
        base = self.base.text().strip()
        model = self.model.text().strip()
        if not model:
            self._fail("Nama model tidak boleh kosong.")
            return
        or_model = self.or_model.text().strip()
        if not or_model:
            self._fail("Model OpenRouter tidak boleh kosong.")
            return
        new_key = self.or_key.text().strip()
        if any(ch.isspace() for ch in new_key):
            self._fail("API key OpenRouter tidak boleh mengandung spasi.")
            return
        if len(new_key) > 512:
            self._fail("API key OpenRouter terlalu panjang.")
            return
        parsed = urlsplit(base if "://" in base else "http://" + base)
        if not parsed.hostname:
            self._fail("Base URL 9Router tidak valid.")
            return
        try:
            if new_key:
                save_openrouter_key(new_key)
            elif self._clear_or_key:
                save_openrouter_key("")
        except OSError as exc:
            self._fail(f"Tidak bisa menyimpan API key OpenRouter: {exc}")
            return

        cfg = self.cfg
        cfg.anchor = str(self.anchor.currentData() or "top-center")
        cfg.y_offset = self.y_offset.value()
        cfg.scale = self.scale.value()
        cfg.auto_collapse_seconds = float(self.auto_collapse.value())
        cfg.reduce_motion = self.reduce_motion.isChecked()
        cfg.always_on_top = self.always_on_top.isChecked()
        cfg.toggle_key = key
        cfg.backend = str(self.backend.currentData() or "auto")
        cfg.model_name = model
        cfg.model_base = base
        cfg.openrouter_model = or_model
        cfg.request_timeout = self.timeout.value()
        if self._reset_position:
            cfg.reset_position()
        cfg.apply_clamps()
        try:
            cfg.save()
        except Exception as exc:
            self._fail(f"Tidak bisa menyimpan konfigurasi: {exc}")
            return
        self.or_key.clear()
        self._clear_or_key = False
        self.or_key.setPlaceholderText(
            openrouter_key_hint() or "Belum ada key — tempel di sini")
        self.error.setVisible(False)
        self.saved.emit()
        super().accept()

    def _fail(self, message: str):
        self.error.setText(message)
        self.error.setVisible(True)
