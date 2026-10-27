"""Pochi desktop - memuat font bundel (Poppins, OFL) tanpa instalasi sistem.

Font diambil dari assets/fonts/ (disebarkan bersama aplikasi). Jika file
tidak ada, fallback ke font sistem (Cantarell). Panggil load_fonts() di
awal (idempoten) sebelum membangun stylesheet.
"""
from __future__ import annotations

from pathlib import Path

from PyQt6.QtGui import QFontDatabase

_loaded = False
FONT_FAMILY = "Cantarell"   # fallback bila bundel tidak ada/rusak


def load_fonts() -> str:
    """Daftarkan font bundel dan kembalikan nama keluarga terpakai."""
    global _loaded, FONT_FAMILY
    if _loaded:
        return FONT_FAMILY
    _loaded = True

    base = Path(__file__).resolve().parent.parent / "assets" / "fonts"
    first_family = None
    for weight in ("Regular", "Medium", "SemiBold", "Bold"):
        f = base / f"Poppins-{weight}.ttf"
        if not f.exists():
            continue
        fid = QFontDatabase.addApplicationFont(str(f))
        if fid >= 0:
            fam = QFontDatabase.applicationFontFamilies(fid)
            if fam and first_family is None:
                first_family = fam[0]

    if first_family:
        FONT_FAMILY = first_family
    return FONT_FAMILY
