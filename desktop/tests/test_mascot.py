import os
import random
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

from PyQt6.QtCore import Qt
from PyQt6.QtWidgets import QApplication
from app.state import Status
from ui.mascot import (EMOTES, SLEEP_AFTER, PochiMascot, emote_for_status,
                       idle_emote)

APP = QApplication.instance() or QApplication(sys.argv[:1])


def test_mapping():
    assert emote_for_status(Status.IDLE) == "neutral"
    assert emote_for_status(Status.THINKING) == "thinking"
    assert emote_for_status(Status.WORKING) == "thinking"
    assert emote_for_status(Status.SUCCESS) in {"happy", "excited"}
    assert emote_for_status(Status.ERROR) in {"sad", "angry"}
    assert emote_for_status(Status.ERROR, error_count=3) == "annoyed"


def test_all_15_render():
    faces = set()
    for name in EMOTES:
        w = PochiMascot(size=64, show_backing=True)
        w.set_emote(name)
        img = w.grab().toImage()
        assert not img.isNull(), name
        faces.add(bytes(img.bits().asstring(img.sizeInBytes())))
    assert len(EMOTES) == 15
    assert len(faces) >= 12  # minimal 12 bentuk berbeda


def test_poke_cycle():
    w = PochiMascot(size=64)
    got = [w.poke() for _ in range(4)]
    assert got == ["wink", "happy", "surprised", "shy"], got
    assert w.pet() == "love"
    assert w.current_emote() == "love"


def test_idle_emote_is_known():
    rng = random.Random(1)
    assert idle_emote(rng) in {"neutral", "wink", "confused", "thinking"}
    assert SLEEP_AFTER == 90.0


def test_reduce_motion_has_no_timer():
    w = PochiMascot(size=64, reduce_motion=True)
    w.show()
    assert not w._timer.isActive()


def test_interactive_mascot():
    w = PochiMascot(size=64, interactive=True)
    assert not w.testAttribute(Qt.WidgetAttribute.WA_TransparentForMouseEvents)
    assert w.poke() == "wink"


if __name__ == "__main__":
    failed = 0
    for fn in (test_mapping, test_all_15_render, test_poke_cycle,
               test_idle_emote_is_known, test_reduce_motion_has_no_timer,
               test_interactive_mascot):
        try:
            fn()
        except Exception as exc:
            print(f"FAIL {fn.__name__}: {exc}")
            failed += 1
        else:
            print(f"ok  {fn.__name__}")
    sys.exit(1 if failed else 0)
