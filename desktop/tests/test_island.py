import os
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

from PyQt6.QtCore import QMimeData, QObject, QPointF, Qt, QUrl, pyqtSignal
from PyQt6.QtGui import QDropEvent
from PyQt6.QtWidgets import QApplication
from app import theme
from app.config import Config
from app.state import Controller, Status
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
    cfg = Config(backend="demo", auto_collapse_seconds=0, scale=0.75,
                 reduce_motion=True)
    ctl = Controller(cfg, MockAdapter(cfg))
    w = IslandWindow(cfg, ctl, FakeMedia())
    w.show()
    area = work_area()
    r = w._ringkas_rect()
    assert area.contains(r), (area, r)
    w.expand()
    APP.processEvents()
    assert w.page == "ringkas"
    assert w.width() >= 440, w.width()
    # Isi tidak boleh terhimpit di bawah minimum layout-nya sendiri.
    page = w.ringkas_page
    assert page.width() >= page.minimumSizeHint().width(), (
        page.width(), page.minimumSizeHint().width())
    assert page.height() >= page.minimumSizeHint().height(), (
        page.height(), page.minimumSizeHint().height())
    w.show_chat()
    APP.processEvents()
    page = w.chat_page
    assert page.width() >= page.minimumSizeHint().width(), (
        page.width(), page.minimumSizeHint().width())
    assert page.height() >= page.minimumSizeHint().height(), (
        page.height(), page.minimumSizeHint().height())
    assert area.contains(w.geometry()), (area, w.geometry())
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


def test_ringkas_does_not_steal_focus():
    w, ctl, media, cfg = make_window()
    w.expand()
    assert not w.input.hasFocus()
    w.collapse()
    ctl.shutdown()


def test_ask_bar_opens_chat():
    w, ctl, media, cfg = make_window()
    w.expand()
    w.ask_bar.click()
    assert w.page == "chat"
    assert w.stack.currentIndex() == 1
    w.collapse()
    ctl.shutdown()


def test_escape_chat_to_ringkas_to_collapsed():
    w, ctl, media, cfg = make_window()
    w.expand()
    w.show_chat()
    w._escape()
    assert w.page == "ringkas" and w.mode == "expanded"
    w._escape()
    assert w.mode == "collapsed"
    ctl.shutdown()


def test_draft_survives_collapse():
    w, ctl, media, cfg = make_window()
    w.expand()
    w.show_chat()
    w.input.setText("setengah pesan")
    w.collapse()
    w.expand()
    w.show_chat()
    assert w.input.text() == "setengah pesan"
    w.collapse()
    ctl.shutdown()


def test_rapid_toggle():
    w, ctl, media, cfg = make_window()
    for _ in range(5):
        w.expand()
        w.show_chat()
        w.show_ringkas()
        w.collapse()
    assert w.mode == "collapsed"
    ctl.shutdown()


def test_permission_opens_chat():
    w, ctl, media, cfg = make_window()
    w.expand()
    w._on_permission({"title": "Baca file", "detail": "x",
                      "options": [{"id": "allow", "label": "Izinkan",
                                   "kind": "allow"}]})
    assert w.page == "chat"
    assert w.permission.isVisible()
    assert not w._idle.isActive()
    w.collapse()
    ctl.shutdown()


def test_permission_clear():
    w, ctl, media, cfg = make_window()
    w.expand()
    w._on_permission({"title": "Baca file", "detail": "x",
                      "options": [{"id": "allow", "label": "Izinkan",
                                   "kind": "allow"}]})
    w._on_permission(None)
    assert w._permission is None
    assert not w.permission.isVisible()
    w.collapse()
    ctl.shutdown()


def test_status_sets_mascot():
    w, ctl, media, cfg = make_window()
    w._on_status(Status.SUCCESS)
    assert w.compact_mascot.current_emote() in {"happy", "excited"}
    w._on_status(Status.ERROR)
    assert w.compact_mascot.current_emote() in {"sad", "angry"}
    ctl.shutdown()


def test_error_count_two_cries():
    w, ctl, media, cfg = make_window()
    w._on_status(Status.ERROR)
    w._on_status(Status.ERROR)
    assert w.compact_mascot.current_emote() == "crying"
    ctl.shutdown()


def test_permission_cannot_be_hidden():
    w, ctl, media, cfg = make_window()
    w.expand()
    w._on_permission({"title": "Baca file", "detail": "x",
                      "options": [{"id": "allow", "label": "Izinkan",
                                   "kind": "allow"}]})
    assert w.page == "chat"
    # Esc tidak boleh menutup atau pindah tab saat izin masih menunggu.
    w._escape()
    assert w.mode == "expanded" and w.page == "chat"
    w.show_ringkas()
    assert w.page == "chat"
    w.btn_close.click()
    assert w.mode == "expanded"
    assert not w.ringkas_tab.isEnabled() and not w.btn_close.isEnabled()
    w._on_permission(None)
    assert w.ringkas_tab.isEnabled() and w.btn_close.isEnabled()
    w.collapse()
    assert w.mode == "collapsed"
    ctl.shutdown()


def test_lyrics_button_reaches_chat_and_toggles():
    w, ctl, media, cfg = make_window()
    w.expand()
    w.music_card.lyrics_btn.click()
    assert w.page == "chat", "tombol lirik di Ringkas harus membuka Chat"
    assert w.lyrics_panel.is_open()
    # Kembali ke Ringkas, klik lagi: panel tetap terbuka dan Chat dibuka lagi.
    w.show_ringkas()
    w.music_card.lyrics_btn.click()
    assert w.page == "chat"
    assert w.lyrics_panel.is_open()
    # Di halaman Chat, klik berikutnya menutup panel.
    w.music_card.lyrics_btn.click()
    assert not w.lyrics_panel.is_open()
    w.collapse()
    ctl.shutdown()


def test_draft_cleared_after_send():
    w, ctl, media, cfg = make_window()
    w.show_chat()
    w.input.setText("halo Pochi")
    w._send()
    assert w.input.text() == ""
    assert w._draft_text == ""
    w.collapse()
    w.expand()
    w.show_chat()
    assert w.input.text() == "", "pesan terkirim tidak boleh muncul lagi"
    w.collapse()
    ctl.shutdown()


def test_drop_opens_chat():
    import tempfile
    w, ctl, media, cfg = make_window()
    w.expand()
    assert w.page == "ringkas"
    with tempfile.NamedTemporaryFile(suffix=".txt") as f:
        f.write(b"halo")
        f.flush()
        mime = QMimeData()
        mime.setUrls([QUrl.fromLocalFile(f.name)])
        ev = QDropEvent(QPointF(10, 10), Qt.DropAction.CopyAction, mime,
                        Qt.MouseButton.LeftButton, Qt.KeyboardModifier.NoModifier)
        w.dropEvent(ev)
    assert w.page == "chat", "drop di Ringkas harus membuka Chat"
    assert w.chat._draft, "file harus masuk ke composer Chat"
    w.chat.take_draft()
    w.collapse()
    ctl.shutdown()


def test_activity_is_not_stray_toplevel():
    w, ctl, media, cfg = make_window()
    assert w.activity.parentWidget() is not None
    assert w.activity.window() is w
    w.collapse()
    ctl.shutdown()


def test_chat_mascot_and_sleep_preferences():
    w, ctl, media, cfg = make_window()
    assert w.chat_mascot._reduce is True
    w.expand()
    w.collapse()
    assert w._sleep_timer.interval() == 90000
    ctl.shutdown()


if __name__ == "__main__":
    failed = 0
    for fn in (test_open_ringkas, test_compact_capsule,
               test_ringkas_fits_small_screen, test_ringkas_empty_state,
               test_long_title_elides, test_screenshot_ringkas,
               test_ringkas_does_not_steal_focus, test_ask_bar_opens_chat,
               test_escape_chat_to_ringkas_to_collapsed,
               test_draft_survives_collapse, test_rapid_toggle,
               test_permission_opens_chat, test_permission_clear,
               test_status_sets_mascot, test_error_count_two_cries,
               test_permission_cannot_be_hidden,
               test_lyrics_button_reaches_chat_and_toggles,
               test_draft_cleared_after_send, test_drop_opens_chat,
               test_activity_is_not_stray_toplevel,
               test_chat_mascot_and_sleep_preferences):
        try:
            fn()
        except Exception as exc:
            print(f"FAIL {fn.__name__}: {exc}")
            failed += 1
        else:
            print(f"ok  {fn.__name__}")
    sys.exit(1 if failed else 0)
