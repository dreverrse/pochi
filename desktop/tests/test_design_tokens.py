import os
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

from PyQt6.QtWidgets import QApplication
from app import theme
from ui.components import Pill, SoftCard, Sparkline, sparkline_points

APP = QApplication.instance() or QApplication(sys.argv[:1])


def test_tokens():
    assert theme.VOID.name() == "#05030a"
    assert theme.VIOLET.name() == "#7c5cff"
    assert theme.VIOLET_LIGHT.name() == "#c4b5fd"
    assert theme.GREEN.name() == "#3ddc97"
    # alias lama tetap ada
    assert theme.BG is not None and theme.PURPLE is not None


def test_pill_tone():
    p = Pill("Siap", "green")
    assert p.text() == "Siap"
    assert "pill-green" in p.styleSheet() or p.property("tone") == "green"


def test_sparkline_points():
    pts = sparkline_points([0.0, 1.0, 0.5], 100.0, 28.0)
    assert len(pts) == 3
    assert pts[0].x() == 0.0 and pts[-1].x() == 100.0
    assert pts[1].y() < pts[0].y()  # nilai lebih tinggi = y lebih kecil


def test_widgets_construct():
    assert SoftCard().body is not None
    s = Sparkline()
    s.set_values([1, 2, 3])
    assert s.values == [1.0, 2.0, 3.0]


if __name__ == "__main__":
    failed = 0
    for fn in (test_tokens, test_pill_tone, test_sparkline_points,
               test_widgets_construct):
        try:
            fn()
        except Exception as exc:
            print(f"FAIL {fn.__name__}: {exc}")
            failed += 1
        else:
            print(f"ok  {fn.__name__}")
    sys.exit(1 if failed else 0)
