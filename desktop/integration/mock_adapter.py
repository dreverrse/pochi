"""MockAdapter — mode DEMO yang jujur.

Tidak menyentuh jaringan dan tidak mengarang data sistem. Semua balasan
diberi label DEMO; dipakai hanya jika Hermes dan 9Router tidak tersedia.
"""
from __future__ import annotations

import random
from datetime import datetime

from PyQt6.QtCore import QTimer

from integration.bridge import PochiBridge





class MockAdapter(PochiBridge):
    name = "demo"
    label = "DEMO"

    def __init__(self, cfg=None, parent=None):
        super().__init__(parent)
        self.cfg = cfg
        self._timer: QTimer | None = None
        self._text = ""
        self._shown = 0
        self._reply = ""
        self._started = False

    def start(self) -> None:
        if self._started:
            return
        self._started = True
        self.on_connection.emit({
            "backend": "demo", "label": "DEMO", "ok": True,
            "model": "mock",
            "detail": "Tidak ada backend nyata — balasan tiruan.",
        })

    def stop(self) -> None:
        self.cancel()
        self._started = False

    def send(self, text: str, history: list[dict] | None = None,
             files: list | tuple = ()) -> None:
        self.cancel()
        self._text = text
        self._reply = self._compose(text)
        self._shown = 0
        self.on_status.emit("thinking")
        self._timer = QTimer(self)
        self._timer.setInterval(16)
        self._timer.timeout.connect(self._tick)
        self._timer.start()

    def _compose(self, text: str) -> str:
        t = text.lower().strip()
        if any(w in t for w in ("halo", "hai", "hi", "hello")):
            body = ("Halo! Aku Pochi. Saat ini aku berjalan dalam mode tiruan, jadi "
                    "balasanku tidak terhubung ke model atau data sistem.")
        elif any(w in t for w in ("cpu", "ram", "sistem", "baterai", "battery")):
            body = ("Aku tidak membaca data sistem dalam mode ini. Buka kartu System Monitor "
                    "di dashboard untuk angka nyata dari /proc dan /sys.")
        elif any(w in t for w in ("jam", "waktu", "time")):
            body = f"Waktu lokal mesin ini: {datetime.now().strftime('%H:%M:%S')}."
        elif "?" in t or any(w in t for w in ("apa", "kenapa", "bagaimana", "how", "why")):
            body = (f"Pertanyaanmu: “{text.strip()[:120]}”. Aku belum memanggil model, "
                    "jadi ini bukan jawaban nyata.")
        else:
            body = (f"Aku menerima: “{text.strip()[:120]}”. Hubungkan Hermes ACP atau "
                    "9Router untuk jawaban nyata.")
        return body

    def _tick(self) -> None:
        step = random.choice((2, 3, 3, 4, 6))
        self._shown = min(len(self._reply), self._shown + step)
        chunk = self._reply[self._shown - step:self._shown]
        if chunk:
            self.on_delta.emit(chunk)
        if self._shown >= len(self._reply):
            self._finish()

    def _finish(self) -> None:
        if self._timer is not None:
            self._timer.stop()
            self._timer.deleteLater()
            self._timer = None
        self.on_message.emit(self._reply, "DEMO")

    def cancel(self) -> None:
        if self._timer is not None:
            self._timer.stop()
            self._timer.deleteLater()
            self._timer = None
