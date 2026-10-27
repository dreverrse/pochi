"""Pochi desktop — konfigurasi aplikasi.

Disimpan di ``~/.config/pochi-desktop/config.json``. File ini tidak pernah
menyimpan API key/token: kredensial 9Router dibaca runtime dari environment
atau ``~/.hermes/.env``, sedangkan key OpenRouter disimpan terpisah di
``~/.config/pochi-desktop/openrouter.key`` dengan izin 0600 (lihat
``services/probes.py`` dan ``integration/router_adapter.py``).
"""
from __future__ import annotations

import json
import os
from dataclasses import asdict, dataclass, fields
from pathlib import Path

CONFIG_DIR = Path(os.environ.get("XDG_CONFIG_HOME", Path.home() / ".config")) / "pochi-desktop"
CONFIG_FILE = CONFIG_DIR / "config.json"

SCHEMA_VERSION = 2

# Dipertahankan untuk kompatibilitas config lama. Identitas visual Pochi tetap
# ungu→cyan; aksen hanya memengaruhi sorotan kecil di beberapa tempat.
ACCENTS = {
    "violet": (0xA7, 0x8B, 0xFA),
    "cyan": (0x42, 0xD9, 0xFF),
    "emerald": (0x34, 0xD3, 0x99),
    "amber": (0xF5, 0x9E, 0x0B),
    "rose": (0xF4, 0x3F, 0x5E),
}

ANCHORS = ("top-left", "top-center", "top-right")
BACKENDS = ("auto", "hermes", "9router", "openrouter", "demo")
OPENROUTER_DEFAULT_MODEL = "openrouter/auto"


@dataclass
class Config:
    # ---- posisi & ukuran -------------------------------------------------
    anchor: str = "top-center"            # top-left | top-center | top-right
    y_offset: int = 12                    # jarak dari tepi atas (px)
    scale: float = 1.0                    # 0.75 .. 1.35
    pos_x: int = -1                       # -1 = ikut anchor; >=0 = posisi bebas
    pos_y: int = -1
    compact_width: int = 176
    expanded_width: int = 440
    expanded_height: int = 552

    # ---- tampilan --------------------------------------------------------
    accent: str = "violet"
    reduce_motion: bool = False
    always_on_top: bool = True
    start_collapsed: bool = True
    auto_collapse_seconds: float = 30.0
    system_poll_ms: int = 2000

    # ---- backend ---------------------------------------------------------
    backend: str = "auto"                 # auto | hermes | 9router | openrouter | demo
    hermes_command: str = "hermes"
    model_base: str = "http://127.0.0.1:20128/v1"
    model_name: str = "inc/mercury-3"
    openrouter_model: str = OPENROUTER_DEFAULT_MODEL
    request_timeout: int = 90

    # ---- interaksi -------------------------------------------------------
    toggle_key: str = "Ctrl+Alt+P"
    show_shortcut_hint: bool = True

    # ---- internal --------------------------------------------------------
    schema_version: int = SCHEMA_VERSION

    # ------------------------------------------------------------------ util
    def accent_rgb(self) -> tuple:
        return ACCENTS.get(self.accent, ACCENTS["violet"])

    def has_custom_position(self) -> bool:
        return self.pos_x >= 0 and self.pos_y >= 0

    def set_position(self, x: int, y: int) -> None:
        self.pos_x, self.pos_y = int(x), int(y)

    def reset_position(self) -> None:
        self.pos_x = self.pos_y = -1

    def apply_clamps(self) -> None:
        self.anchor = self.anchor if self.anchor in ANCHORS else "top-center"
        if self.backend == "mock":
            self.backend = "demo"
        self.backend = self.backend if self.backend in BACKENDS else "auto"
        self.scale = min(1.35, max(0.75, float(self.scale or 1.0)))
        self.y_offset = min(300, max(0, int(self.y_offset)))
        self.auto_collapse_seconds = min(600.0, max(0.0, float(self.auto_collapse_seconds)))
        self.expanded_width = min(760, max(360, int(self.expanded_width)))
        self.expanded_height = min(700, max(400, int(self.expanded_height)))
        self.compact_width = min(260, max(140, int(self.compact_width)))
        self.system_poll_ms = min(10_000, max(800, int(self.system_poll_ms)))
        self.request_timeout = min(300, max(15, int(self.request_timeout)))
        self.openrouter_model = (str(self.openrouter_model or "").strip()
                                 or OPENROUTER_DEFAULT_MODEL)[:120]

    def save(self) -> None:
        self.apply_clamps()
        CONFIG_DIR.mkdir(parents=True, exist_ok=True)
        tmp = CONFIG_FILE.with_suffix(".json.tmp")
        tmp.write_text(json.dumps(asdict(self), indent=2) + "\n")
        tmp.replace(CONFIG_FILE)

    @classmethod
    def load(cls) -> "Config":
        if CONFIG_FILE.exists():
            try:
                data = json.loads(CONFIG_FILE.read_text())
                known = {f.name for f in fields(cls)}
                cfg = cls(**{k: v for k, v in data.items() if k in known})
                cfg.apply_clamps()
                return cfg
            except Exception:
                pass
        cfg = cls()
        cfg.apply_clamps()
        return cfg

    def public_dict(self) -> dict:
        """Untuk diagnosa; tidak ada kredensial di sini."""
        d = asdict(self)
        return d
