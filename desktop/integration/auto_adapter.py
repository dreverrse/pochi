"""Pemilihan backend dengan fallback yang terlihat (bukan diam-diam).

Urutan mode ``auto``:
  1. Hermes ACP  — jalur resmi, streaming + tool + izin
  2. 9Router     — model langsung lewat gateway lokal
  3. OpenRouter  — cloud, hanya bila API key sudah tersimpan
  4. DEMO        — mock berlabel jelas (tidak menyamar sebagai AI nyata)

Jika sebuah kandidat gagal sebelum pernah menjawab, UI diberi tahu dan
kandidat berikutnya dicoba. Setelah backend benar-benar menjawab, kegagalan
tidak lagi memindahkan percakapan secara diam-diam.
"""
from __future__ import annotations

from PyQt6.QtCore import QTimer

from integration.bridge import PochiBridge
from integration.hermes_adapter import HermesAcpAdapter
from integration.mock_adapter import MockAdapter
from integration.router_adapter import OpenRouterAdapter, RouterAdapter
from services.probes import has_command, openrouter_api_key

START_TIMEOUT_MS = 35_000


class AutoAdapter(PochiBridge):
    name = "auto"
    label = "Auto"

    def __init__(self, cfg, parent=None):
        super().__init__(parent)
        self.cfg = cfg
        self._factories = _candidates(cfg)
        self._index = 0
        self._current: PochiBridge | None = None
        self._connected = False
        self._used = False
        self._switching = False
        self._last_error = ""
        self._pending_prompt: tuple[str, list[dict], list] | None = None
        self._timeout = QTimer(self)
        self._timeout.setSingleShot(True)
        self._timeout.timeout.connect(self._on_start_timeout)

    def start(self) -> None:
        self._index = 0
        self._used = False
        self._switching = False
        self._last_error = ""
        self._pending_prompt = None
        self._start_current()

    def _start_current(self) -> None:
        if self._index >= len(self._factories):
            return
        factory, label = self._factories[self._index]
        adapter = factory(self.cfg)
        self._current = adapter
        self._connected = False
        adapter.on_status.connect(self.on_status.emit)
        adapter.on_delta.connect(self._forward_delta)
        adapter.on_message.connect(self._forward_message)
        adapter.on_activity.connect(self.on_activity.emit)
        adapter.on_permission.connect(self.on_permission.emit)
        adapter.on_error.connect(self._on_error)
        adapter.on_connection.connect(self._on_connection)
        adapter.on_usage.connect(self.on_usage.emit)
        self.on_connection.emit({
            "backend": getattr(adapter, "name", "auto"),
            "label": getattr(adapter, "label", label),
            "ok": False,
            "model": "",
            "detail": f"Mencoba {label}…",
        })
        adapter.start()
        self._timeout.start(START_TIMEOUT_MS)
        if self._pending_prompt is not None:
            # Pengguna sudah menekan kirim saat backend belum siap: teruskan
            # prompt yang sama ke kandidat baru agar tidak hilang saat fallback.
            text, history, files = self._pending_prompt
            adapter.send(text, history, files)

    # ------------------------------------------------------- forwarding
    def _forward_delta(self, text: str) -> None:
        self._used = True
        self._pending_prompt = None
        self.on_delta.emit(text)

    def _forward_message(self, text: str, meta: str) -> None:
        self._used = True
        self._pending_prompt = None
        self.on_message.emit(text, meta)

    def _on_error(self, message: str) -> None:
        # Error saat kandidat belum pernah menjawab disimpan; fallback masih
        # boleh mencoba backend berikutnya tanpa menakut-nakuti pengguna.
        if self._connected or self._used:
            self._pending_prompt = None
            self.on_error.emit(message)
        else:
            self._last_error = str(message or "")

    def _on_connection(self, info) -> None:
        try:
            ok = bool(dict(info).get("ok"))
        except Exception:
            ok = False
        if ok:
            self._connected = True
            self._last_error = ""
            self._timeout.stop()
            self.on_connection.emit(info)
            return
        # kandidat gagal sebelum terhubung → coba berikutnya
        if not self._used and not self._switching:
            self._timeout.stop()
            self._switch(f"{dict(info).get('label', 'Backend')} tidak tersedia")
        else:
            self.on_connection.emit(info)

    def _on_start_timeout(self) -> None:
        if not self._connected and not self._switching:
            self._switch("Batas waktu koneksi habis")

    def _switch(self, reason: str) -> None:
        self._switching = True
        old = self._current
        self._current = None
        try:
            if old is not None:
                # Putuskan sinyal kandidat lama agar kegagalan saat shutdown
                # tidak memicu fallback kedua (melewati kandidat berikutnya).
                for sig in (old.on_status, old.on_delta, old.on_message,
                            old.on_activity, old.on_permission, old.on_error,
                            old.on_connection, old.on_usage):
                    try:
                        sig.disconnect()
                    except Exception:
                        pass
                old.stop()
        except Exception:
            pass
        self._index += 1
        if self._index >= len(self._factories):
            self._pending_prompt = None
            self.on_error.emit(
                self._last_error or f"{reason}. Tidak ada backend tersisa.")
            self._last_error = ""
            self._switching = False
            return
        nxt, label = self._factories[self._index]
        self.on_connection.emit({
            "backend": "auto", "label": "Auto", "ok": False,
            "model": "", "detail": f"{reason} — beralih ke {label}…",
        })
        self._switching = False
        self._start_current()

    # --------------------------------------------------------- contract
    def send(self, text: str, history: list[dict] | None = None,
             files: list | tuple = ()) -> None:
        if self._current is None:
            self.on_error.emit("Backend belum siap. Coba lagi sebentar.")
            return
        self._pending_prompt = (text, list(history or []), list(files))
        self._current.send(text, history, files)

    def cancel(self) -> None:
        self._pending_prompt = None
        if self._current is not None:
            self._current.cancel()

    def respond_permission(self, request_id, option_id: str | None) -> None:
        if self._current is not None:
            self._current.respond_permission(request_id, option_id)

    def reset(self) -> None:
        if self._current is not None:
            self._current.reset()

    def stop(self) -> None:
        self._timeout.stop()
        self._pending_prompt = None
        current, self._current = self._current, None
        if current is not None:
            current.stop()


def _candidates(cfg):
    hermes = (lambda c: HermesAcpAdapter(c), "Hermes ACP")
    router = (lambda c: RouterAdapter(c), "9Router")
    openrouter = (lambda c: OpenRouterAdapter(c), "OpenRouter")
    demo = (lambda c: MockAdapter(c), "DEMO")
    if cfg.backend == "demo":
        return [demo]
    if cfg.backend == "hermes":
        return [hermes]
    if cfg.backend == "9router":
        return [router]
    if cfg.backend == "openrouter":
        return [openrouter]
    out = []
    if has_command(cfg.hermes_command):
        out.append(hermes)
    out.append(router)
    # Cloud hanya ikut fallback kalau pengguna sudah menyimpan key (opt-in).
    if openrouter_api_key():
        out.append(openrouter)
    out.append(demo)
    return out


def create_bridge(cfg) -> PochiBridge:
    """Bangun bridge sesuai config (auto memakai fallback)."""
    if cfg.backend == "auto":
        return AutoAdapter(cfg)
    factory = {
        "hermes": lambda c: HermesAcpAdapter(c),
        "9router": lambda c: RouterAdapter(c),
        "openrouter": lambda c: OpenRouterAdapter(c),
        "demo": lambda c: MockAdapter(c),
    }.get(cfg.backend, lambda c: AutoAdapter(c))
    return factory(cfg)
