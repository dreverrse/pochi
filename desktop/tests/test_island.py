import os
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

from PyQt6.QtCore import QObject, pyqtSignal
from PyQt6.QtWidgets import QApplication
from app import theme
from app.config import Config
from app.state import Controller
from integration.mock_adapter import MockAdapter
from platform_x11.x11 import work_area
from ui.island import IslandWindow

APP = QApplication.instance() or QApplication(sys.argv[:1])
APP.setStyleSheet(theme.qss())


class FakeMedia(QObject):
    changed = pyqtSignal(dict)

    def __init__(self):
        super().__init__()
        self._data = {}

    def acquire(self, owner):
        pass

    def release(self, owner):
        pass

    def snapshot(self):
        return dict(self._data)

    def previous(self):
        pass

    def play_pause(self):
        pass

    def next(self):
        pass


def make_window():
    cfg = Config(backend="demo", auto_collapse_seconds=0, reduce_motion=True)
    ctl = Controller(cfg, MockAdapter(cfg))
    media = FakeMedia()
    w = IslandWindow(cfg, ctl, media)
    w.show()
    return w, ctl, media, cfg


def test_open_ringkas():
    w, ctl, media, cfg = make_window()
    w.expand()
    assert w.page == "ringkas"
    assert w.width() >= int(560 * cfg.scale)
    assert w.ringkas_tab.isChecked()
    assert w.ask_bar.isVisible()
    assert w.music_card is not None and w.agenda_card is not None
    w.collapse()
    ctl.shutdown()


def test_compact_capsule():
    w, ctl, media, cfg = make_window()
    assert w.mode == "collapsed"
    assert w.compact_mascot is not None
    assert w.width() >= int(236 * cfg.scale)
    ctl.shutdown()


def test_ringkas_fits_small_screen():
    cfg = Config(backend="demo", auto_collapse_seconds=0, scale=0.75)
    ctl = Controller(cfg, MockAdapter(cfg))
    w = IslandWindow(cfg, ctl, FakeMedia())
    area = work_area()
    r = w._ringkas_rect()
    assert area.contains(r), (area, r)
    w.collapse()
    ctl.shutdown()


def test_ringkas_empty_state():
    w, ctl, media, cfg = make_window()
    w.expand()
    w.music_card.set_media({})
    w.agenda_card.refresh()
    assert w.music_card.isVisible() is False or w.music_card.empty.isVisible()
    assert w.agenda_card.next_text.text()
    w.collapse()
    ctl.shutdown()


def test_long_title_elides():
    w, ctl, media, cfg = make_window()
    w.expand()
    long_title = "X" * 120
    w.music_card.set_media({"available": True, "title": long_title,
                            "artist": "Y" * 120, "playing": True})
    assert w.music_card.title.full_text() == long_title
    assert w.music_card.title.toolTip()
    w.collapse()
    ctl.shutdown()


def test_screenshot_ringkas():
    w, ctl, media, cfg = make_window()
    w.expand()
    APP.processEvents()
    out = Path(__file__).parent / "shots" / "9_ringkas.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    assert w.grab().save(str(out))
    w.collapse()
    ctl.shutdown()


if __name__ == "__main__":
    failed = 0
    for fn in (test_open_ringkas, test_compact_capsule,
               test_ringkas_fits_small_screen, test_ringkas_empty_state,
               test_long_title_elides, test_screenshot_ringkas):
        try:
            fn()
        except Exception as exc:
            print(f"FAIL {fn.__name__}: {exc}")
            failed += 1
        else:
            print(f"ok  {fn.__name__}")
    sys.exit(1 if failed else 0)
