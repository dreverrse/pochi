"""Pochi integration — kontrak penghubung ke "otak" Pochi.

UI hanya berbicara dengan ``PochiBridge``. Implementasi konkret:
``HermesAcpAdapter`` (Hermes Agent via ACP), ``RouterAdapter`` (9Router),
``OpenRouterAdapter`` (OpenRouter cloud), ``MockAdapter`` (DEMO), dan
``AutoAdapter`` (fallback terverifikasi).

Tidak ada API key di source, config, maupun di UI; key OpenRouter dibaca dari
environment/.env/file 0600 saat adapter dibuat.
"""
from __future__ import annotations

from PyQt6.QtCore import QObject, pyqtSignal


class PochiBridge(QObject):
    """Antarmuka abstrak backend Pochi (semua sinyal async)."""

    # status: idle | thinking | working | listening | success | error
    on_status = pyqtSignal(str)
    # potongan teks streaming
    on_delta = pyqtSignal(str)
    # teks final, meta (HERMES / 9ROUTER / DEMO)
    on_message = pyqtSignal(str, str)
    # dict aktivitas tool / None
    on_activity = pyqtSignal(object)
    # permintaan izin tool (dict) / None
    on_permission = pyqtSignal(object)
    # pesan error yang ramah pengguna
    on_error = pyqtSignal(str)
    # dict Connection
    on_connection = pyqtSignal(object)
    # pemakaian konteks (used, size)
    on_usage = pyqtSignal(int, int)

    name = "base"
    label = "Bridge"

    def start(self) -> None:
        """Mulai koneksi/backend (idempoten)."""

    def stop(self) -> None:
        """Hentikan proses/thread dengan bersih."""

    def send(self, text: str, history: list[dict] | None = None,
             files: list | tuple = ()) -> None:
        raise NotImplementedError

    def cancel(self) -> None:
        """Batalkan permintaan yang sedang berjalan."""

    def respond_permission(self, request_id, option_id: str | None) -> None:
        """Jawab permintaan izin tool (opsional pada adapter tertentu)."""

    def reset(self) -> None:
        """Lupakan konteks percakapan (jika didukung)."""
