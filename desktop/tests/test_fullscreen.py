"""Tes untuk services/fullscreen.py (deteksi layar penuh)."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from services.fullscreen import FullscreenService  # noqa: E402


class _FakeSubprocess:
    def __init__(self, active="0x3a00003", state=""):
        self._active = active
        self._state = state
        self.calls = []

    def check_output(self, args, **kw):
        self.calls.append(args)
        if args[0] == "xprop" and args[1] == "-root":
            return f"_NET_ACTIVE_WINDOW(WINDOW): window id # {self._active}, 0x0\n"
        return f"_NET_WM_STATE(ATOM) = {self._state}\n"


def test_detects_fullscreen(monkeypatch):
    fs = FullscreenService(parent=None)
    monkeypatch.setattr("services.fullscreen.subprocess.check_output",
                       _FakeSubprocess(state="_NET_WM_STATE_FULLSCREEN").check_output)
    assert fs.is_fullscreen_now() is True


def test_detects_not_fullscreen(monkeypatch):
    fs = FullscreenService(parent=None)
    monkeypatch.setattr("services.fullscreen.subprocess.check_output",
                       _FakeSubprocess(state="_NET_WM_STATE_FOCUSED").check_output)
    assert fs.is_fullscreen_now() is False


def test_detects_active_missing(monkeypatch):
    fs = FullscreenService(parent=None)

    def no_active(*a, **k):
        return "tidak ada window\n"
    monkeypatch.setattr("services.fullscreen.subprocess.check_output", no_active)
    assert fs.is_fullscreen_now() is False


if __name__ == "__main__":
    # tanpa pytest: stub manual
    import services.fullscreen as mod
    orig = mod.subprocess.check_output
    try:
        ok = 0
        mod.subprocess.check_output = _FakeSubprocess(
            state="_NET_WM_STATE_FULLSCREEN").check_output
        if FullscreenService(parent=None).is_fullscreen_now(): ok += 1
        else: print("FAIL fullscreen")
        mod.subprocess.check_output = _FakeSubprocess(
            state="_NET_WM_STATE_FOCUSED").check_output
        if not FullscreenService(parent=None).is_fullscreen_now(): ok += 1
        else: print("FAIL non-fullscreen")
        mod.subprocess.check_output = lambda *a, **k: "x"
        if not FullscreenService(parent=None).is_fullscreen_now(): ok += 1
        else: print("FAIL no active")
        print(f"ok  {ok}/3")
        sys.exit(0 if ok == 3 else 1)
    finally:
        mod.subprocess.check_output = orig
