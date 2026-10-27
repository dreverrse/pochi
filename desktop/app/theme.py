"""Pochi desktop — palet warna, tipografi, dan stylesheet terpusat.

Semua komponen UI mengambil warna dari sini supaya identitas visual konsisten:
latar matte-black, panel graphite, aksen ungu → cyan, status hijau/amber/merah.
"""
from __future__ import annotations

from PyQt6.QtGui import QColor

# ---------------------------------------------------------------- palet inti
# Identitas visual meniru versi Android (Dynamic Island iOS): hitam murni,
# permukaan kartu gelap #141416, hairline #26262B, dan warna aksen iOS.
BG = QColor("#000000")          # island black (iOS)
BG_SOFT = QColor("#0A0A0A")     # matte black (backing mascot)
PANEL = QColor("#141416")        # cardSurface
PANEL_2 = QColor("#1C1C1E")
PANEL_3 = QColor("#2C2C2E")     # iosGray
BORDER = QColor("#26262B")       # borderHairline
BORDER_SOFT = QColor("#1C1C20")
TEXT = QColor("#FFFFFF")
TEXT_SOFT = QColor("#E5E5EA")
MUTED = QColor("#8E8E93")
DIM = QColor("#636366")

PURPLE = QColor("#AF52DE")       # iosPurple
PURPLE_DEEP = QColor("#8E4EC6")
CYAN = QColor("#38BDF8")         # working (sky)
GREEN = QColor("#34C759")        # iosGreen
AMBER = QColor("#FF9500")        # iosAmber / waiting
RED = QColor("#FF3B30")          # iosRed

STATUS_COLORS = {
    "idle": TEXT,
    "listening": AMBER,
    "thinking": CYAN,
    "working": CYAN,
    "success": GREEN,
    "error": RED,
}


def hexc(c: QColor, alpha: int | None = None) -> str:
    """#RRGGBB atau rgba() untuk QSS."""
    if alpha is None:
        return c.name()
    return f"rgba({c.red()}, {c.green()}, {c.blue()}, {alpha})"


def qss(family: str = "Poppins") -> str:
    """Stylesheet global. Latar island dilukis manual (rounded + shadow)."""
    return f"""
    * {{
        font-family: '{family}', 'Noto Sans', 'Cantarell', sans-serif;
        outline: none;
    }}
    QWidget {{
        background: transparent;
        color: {TEXT.name()};
        font-size: 13px;
        font-weight: 500;
    }}
    QToolTip {{
        background: {PANEL_2.name()};
        color: {TEXT.name()};
        border: 1px solid {BORDER.name()};
        border-radius: 8px;
        padding: 6px 9px;
        font-size: 11px;
    }}

    /* ---------------------------------------------------------- kartu */
    QFrame#card {{
        background: {PANEL.name()};
        border: 1px solid {BORDER_SOFT.name()};
        border-radius: 16px;
    }}
    QFrame#strip {{
        background: {PANEL_2.name()};
        border: 1px solid {BORDER_SOFT.name()};
        border-radius: 12px;
    }}
    QFrame#banner {{
        background: rgba(167, 139, 250, 0.10);
        border: 1px solid rgba(167, 139, 250, 0.35);
        border-radius: 12px;
    }}
    QFrame#errorBanner {{
        background: rgba(244, 63, 94, 0.10);
        border: 1px solid rgba(244, 63, 94, 0.35);
        border-radius: 12px;
    }}
    QFrame#divider {{
        background: {BORDER_SOFT.name()};
        max-height: 1px;
        border: none;
    }}
    QFrame#bubbleUser {{
        background: rgba(167, 139, 250, 0.14);
        border: 1px solid rgba(167, 139, 250, 0.30);
        border-radius: 14px;
    }}
    QFrame#bubbleAssistant {{
        background: {PANEL_2.name()};
        border: 1px solid {BORDER_SOFT.name()};
        border-radius: 14px;
    }}
    QFrame#bubbleError {{
        background: rgba(244, 63, 94, 0.10);
        border: 1px solid rgba(244, 63, 94, 0.32);
        border-radius: 14px;
    }}

    /* ---------------------------------------------------------- teks */
    QLabel#eyebrow {{
        color: {DIM.name()};
        font-size: 10px;
        font-weight: 600;
        letter-spacing: 1.2px;
    }}
    QLabel#title {{
        color: {TEXT.name()};
        font-size: 14px;
        font-weight: 600;
    }}
    QLabel#subtitle {{
        color: {MUTED.name()};
        font-size: 11px;
    }}
    QLabel#dim {{
        color: {DIM.name()};
        font-size: 11px;
    }}
    QLabel#stat {{
        color: {TEXT.name()};
        font-size: 20px;
        font-weight: 700;
    }}
    QLabel#name {{
        color: {TEXT.name()};
        font-size: 13px;
        font-weight: 600;
    }}
    QLabel#pill {{
        background: {PANEL_2.name()};
        border: 1px solid {BORDER.name()};
        border-radius: 9px;
        color: {MUTED.name()};
        font-size: 10px;
        font-weight: 600;
        padding: 3px 8px;
    }}

    /* ---------------------------------------------------------- tombol */
    QPushButton {{
        background: {PANEL_2.name()};
        border: 1px solid {BORDER.name()};
        border-radius: 10px;
        color: {TEXT_SOFT.name()};
        padding: 7px 12px;
        font-size: 12px;
        font-weight: 500;
    }}
    QPushButton:hover {{
        background: {PANEL_3.name()};
        color: {TEXT.name()};
    }}
    QPushButton:pressed {{
        background: {BORDER.name()};
    }}
    QPushButton:disabled {{
        color: {DIM.name()};
        background: {PANEL.name()};
        border-color: {BORDER_SOFT.name()};
    }}
    QPushButton#iconBtn {{
        background: transparent;
        border: none;
        border-radius: 9px;
        padding: 6px;
    }}
    QPushButton#iconBtn:hover {{
        background: {PANEL_3.name()};
    }}
    QPushButton#iconBtn:pressed {{
        background: {BORDER.name()};
    }}
    QPushButton#send {{
        background: {PURPLE.name()};
        border: 1px solid {PURPLE.name()};
        border-radius: 12px;
        color: {BG.name()};
        padding: 0;
    }}
    QPushButton#send:hover {{
        background: #B9A3FC;
        border-color: #B9A3FC;
    }}
    QPushButton#send:pressed {{
        background: {PURPLE_DEEP.name()};
        border-color: {PURPLE_DEEP.name()};
    }}
    QPushButton#send[busy="true"] {{
        background: {PANEL_3.name()};
        border-color: {BORDER.name()};
        color: {TEXT.name()};
    }}
    QPushButton#primary {{
        background: {PURPLE.name()};
        border: 1px solid {PURPLE.name()};
        color: {BG.name()};
        font-weight: 600;
    }}
    QPushButton#primary:hover {{
        background: #B9A3FC;
    }}
    QPushButton#danger {{
        background: rgba(244, 63, 94, 0.12);
        border: 1px solid rgba(244, 63, 94, 0.35);
        color: #FDA4AF;
    }}
    QPushButton#danger:hover {{
        background: rgba(244, 63, 94, 0.20);
    }}
    QPushButton#ghost {{
        background: transparent;
        border: 1px solid transparent;
        color: {MUTED.name()};
    }}
    QPushButton#ghost:hover {{
        background: {PANEL_2.name()};
        color: {TEXT.name()};
    }}
    QPushButton#chip {{
        background: {PANEL_2.name()};
        border: 1px solid {BORDER_SOFT.name()};
        border-radius: 12px;
        color: {MUTED.name()};
        padding: 5px 11px;
        font-size: 11px;
    }}
    QPushButton#chip:hover {{
        background: {PANEL_3.name()};
        color: {TEXT.name()};
        border-color: {BORDER.name()};
    }}
    QPushButton#segment {{
        background: transparent;
        border: 1px solid transparent;
        border-radius: 9px;
        color: {MUTED.name()};
        padding: 7px 10px;
        font-size: 11px;
        font-weight: 600;
    }}
    QPushButton#segment:hover {{
        color: {TEXT.name()};
        background: {PANEL_2.name()};
    }}
    QPushButton#segment:checked {{
        background: {PANEL_3.name()};
        border-color: {BORDER.name()};
        color: {TEXT.name()};
    }}

    /* ---------------------------------------------------------- input */
    QLineEdit, QSpinBox, QDoubleSpinBox, QComboBox, QKeySequenceEdit {{
        background: {PANEL_2.name()};
        border: 1px solid {BORDER.name()};
        border-radius: 10px;
        color: {TEXT.name()};
        padding: 8px 10px;
        selection-background-color: {PURPLE_DEEP.name()};
        selection-color: {TEXT.name()};
    }}
    QLineEdit:hover, QSpinBox:hover, QDoubleSpinBox:hover, QComboBox:hover {{
        border-color: #3A3A48;
    }}
    QLineEdit:focus, QSpinBox:focus, QDoubleSpinBox:focus,
    QComboBox:focus, QKeySequenceEdit:focus {{
        border-color: {PURPLE.name()};
    }}
    QLineEdit#chatInput {{
        background: {PANEL_2.name()};
        border: 1px solid {BORDER.name()};
        border-radius: 13px;
        padding: 10px 14px;
        font-size: 13px;
    }}
    QLineEdit#chatInput:focus {{
        border-color: rgba(167, 139, 250, 0.65);
        background: #1F1F29;
    }}
    QComboBox::drop-down {{
        border: none;
        width: 22px;
    }}
    QComboBox QAbstractItemView {{
        background: {PANEL_2.name()};
        border: 1px solid {BORDER.name()};
        border-radius: 8px;
        color: {TEXT.name()};
        selection-background-color: {PANEL_3.name()};
        padding: 4px;
    }}
    QCheckBox {{
        color: {TEXT_SOFT.name()};
        spacing: 8px;
        font-size: 12px;
    }}
    QCheckBox::indicator {{
        width: 16px;
        height: 16px;
        border-radius: 5px;
        border: 1px solid {BORDER.name()};
        background: {PANEL_2.name()};
    }}
    QCheckBox::indicator:checked {{
        background: {PURPLE.name()};
        border-color: {PURPLE.name()};
    }}
    QSlider::groove:horizontal {{
        height: 4px;
        background: {BORDER_SOFT.name()};
        border-radius: 2px;
    }}
    QSlider::sub-page:horizontal {{
        background: {PURPLE.name()};
        border-radius: 2px;
    }}
    QSlider::handle:horizontal {{
        width: 14px;
        height: 14px;
        margin: -6px 0;
        border-radius: 7px;
        background: {TEXT.name()};
        border: 2px solid {PURPLE.name()};
    }}

    /* ---------------------------------------------------------- progress */
    QProgressBar {{
        background: {BORDER_SOFT.name()};
        border: none;
        border-radius: 3px;
        min-height: 4px;
        max-height: 4px;
    }}
    QProgressBar::chunk {{
        background: {PURPLE.name()};
        border-radius: 3px;
    }}
    QProgressBar#cyan::chunk {{ background: {CYAN.name()}; }}
    QProgressBar#green::chunk {{ background: {GREEN.name()}; }}
    QProgressBar#amber::chunk {{ background: {AMBER.name()}; }}

    /* ---------------------------------------------------------- scroll */
    QScrollArea {{
        background: transparent;
        border: none;
    }}
    QScrollArea > QWidget > QWidget {{
        background: transparent;
    }}
    QScrollBar:vertical {{
        background: transparent;
        width: 5px;
        margin: 2px;
    }}
    QScrollBar::handle:vertical {{
        background: {BORDER.name()};
        border-radius: 2px;
        min-height: 24px;
    }}
    QScrollBar::handle:vertical:hover {{
        background: #3A3A48;
    }}
    QScrollBar::add-line:vertical, QScrollBar::sub-line:vertical,
    QScrollBar::add-page:vertical, QScrollBar::sub-page:vertical {{
        background: transparent;
        height: 0;
    }}

    /* ---------------------------------------------------------- menu */
    QMenu {{
        background: {PANEL_2.name()};
        border: 1px solid {BORDER.name()};
        border-radius: 10px;
        padding: 6px;
        color: {TEXT_SOFT.name()};
    }}
    QMenu::item {{
        padding: 7px 26px 7px 12px;
        border-radius: 6px;
        font-size: 12px;
    }}
    QMenu::item:selected {{
        background: {PANEL_3.name()};
        color: {TEXT.name()};
    }}
    QMenu::separator {{
        height: 1px;
        background: {BORDER_SOFT.name()};
        margin: 5px 8px;
    }}
    """
