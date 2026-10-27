# Pochi Desktop "Ringkas" Redesign — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ganti bahasa visual dan ekspresi maskot desktop Pochi tanpa mengubah fitur, backend, atau arsitektur: kapsul ungu, panel Ringkas (musik + agenda), Chat, dan 15 ekspresi.

**Architecture:** Tetap PyQt6. `IslandWindow` menyimpan satu panel expanded dengan `QStackedWidget` berisi halaman `Ringkas` dan `Chat`; ukuran jendela berubah saat berpindah halaman. Theme dan komponen kartu menjadi sumber warna/bentuk. `PochiMascot` menjadi mesin ekspresi QPainter dengan 15 bentuk.

**Tech Stack:** Python 3, PyQt6, QPainter, QSS, X11 (XFCE), Poppins.

**Spec:** `docs/superpowers/specs/2026-10-28-pochi-desktop-redesign-design.md`

## Global Constraints

- Desktop Linux saja; jangan menyentuh `app/` (Android), `site/`, atau workflow APK.
- PyQt6 yang sudah ada; **tanpa dependency baru**.
- Jangan mengubah `services/`, `integration/`, `app/state.py`, `platform_x11/` kecuali disebut eksplisit.
- Pertahankan nama publik lama di `app/theme.py` (`BG`, `PANEL`, `PURPLE`, `qss`, `hexc`, dst.) agar dashboard/settings tidak rusak.
- Semua teks UI berbahasa Indonesia, konsisten dengan yang ada.
- Hormati `cfg.reduce_motion` di maskot dan transisi panel.
- Tanpa aset jaringan saat runtime; Poppins dari `desktop/assets/fonts/`.
- Test berjalan offscreen: `QT_QPA_PLATFORM=offscreen /usr/bin/python3 tests/<file>.py`.
- Smoke test X11: `DISPLAY=:0.0 /usr/bin/python3 tests/smoke.py`.
- Commit setiap task selesai; jangan gabung beberapa task dalam satu commit.
- Jangan memakai data palsu untuk sparkline/agenda; hanya data service/state yang ada.

## Review Focus

1. **Wallpaper terang**: panel hitam di atas wallpaper putih/terang harus tetap terbaca; rim dan teks tidak boleh hilang. Test: screenshot panel di atas `QImage` putih dan hitam.
2. **Ringkas kosong**: tanpa media dan tanpa task, kartu tidak boleh kosong/crash. Test: `set_media({})` + `tasks=[]`.
3. **Teks panjang**: judul lagu, nama event, dan pesan error panjang harus elide, bukan melebarkan panel. Test: judul 120 karakter.
4. **Skala & layar kecil**: `scale=0.75` dan work area 1366×768; panel harus muat. Test: geometry dalam `work_area()`.
5. **Toggle cepat & izin saat collapsed**: buka/tutup berulang saat animasi, dan permission datang saat kapsul tertutup. Test: tidak stuck, permission membuka Chat.

---

### Task 1: Design tokens + komponen dasar

**Files:**
- Modify: `desktop/app/theme.py`
- Modify: `desktop/ui/components.py`
- Test: `desktop/tests/test_design_tokens.py` (create)

**Interfaces:**
- Produces di `theme.py`: `VOID`, `PANEL`, `CARD_TOP`, `CARD_BOTTOM`, `CARD_HI`, `BORDER`, `TEXT`, `MUTED`, `DIM`, `VIOLET`, `VIOLET_LIGHT`, `CYAN`, `GREEN`, `AMBER`, `RED` (semua `QColor`), dan alias lama tetap.
- Produces di `components.py`:
  - `class Pill(QLabel)`: `__init__(text: str = "", tone: str = "muted", parent=None)`, `set_tone(tone: str) -> None`.
  - `class SoftCard(QFrame)`: `__init__(padding: int = 13, parent=None)`, atribut `body: QVBoxLayout`.
  - `class Sparkline(QWidget)`: `__init__(color: QColor | None = None, parent=None)`, `set_values(values: list[float]) -> None`.
  - `def sparkline_points(values: list[float], width: float, height: float) -> list[QPointF]`.
- Consumes: `theme.hexc`, `theme.qss` yang sudah ada.

- [ ] **Step 1: Tulis test yang gagal**

Buat `desktop/tests/test_design_tokens.py`:

```python
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
```

- [ ] **Step 2: Jalankan, pastikan gagal**

Run: `cd desktop && QT_QPA_PLATFORM=offscreen /usr/bin/python3 tests/test_design_tokens.py`
Expected: FAIL karena `VOID`/`Pill`/`sparkline_points` belum ada.

- [ ] **Step 3: Implementasi token + komponen**

Di `theme.py` tambahkan token baru dan pertahankan alias lama. Nilai persis:

```python
VOID = QColor("#05030A")
PANEL = QColor("#0B0812")
CARD_TOP = QColor("#191524")
CARD_BOTTOM = QColor("#12101A")
CARD_HI = QColor("#241B3D")
BORDER = QColor("#26202F")
TEXT = QColor("#F5F3FF")
MUTED = QColor("#9A94AE")
DIM = QColor("#6B6380")
VIOLET = QColor("#7C5CFF")
VIOLET_LIGHT = QColor("#C4B5FD")
CYAN = QColor("#4CC9F0")
GREEN = QColor("#3DDC97")
AMBER = QColor("#FFB547")
RED = QColor("#FF5C7A")
```

Tambahkan QSS untuk `QFrame#softCard`, `QFrame#ringkasCard`, `QLabel#pill`, `QLabel#pill-green`, `QLabel#pill-violet`, `QLabel#pill-red`, `QLabel#pill-amber`. Pakai `border-radius: 20px`, `background: qlineargradient(... CARD_TOP → CARD_BOTTOM)`, hairline `BORDER`.

Di `components.py`, implementasikan `Pill` (set `objectName("pill")`, properti `tone`), `SoftCard` (QFrame + `body` QVBoxLayout), `Sparkline` (simpan `values`, `paintEvent` menggambar polyline + area transparan), dan `sparkline_points` (normalisasi min/max; jika semua nilai sama, taruh di tengah).

- [ ] **Step 4: Jalankan test, pastikan lulus**

Run: `cd desktop && QT_QPA_PLATFORM=offscreen /usr/bin/python3 tests/test_design_tokens.py`
Expected: `ok` untuk 4 test, exit 0.

- [ ] **Step 5: Commit**

```bash
git add desktop/app/theme.py desktop/ui/components.py desktop/tests/test_design_tokens.py
git commit -m "feat(desktop): token warna + komponen kartu/pill/sparkline"
```

---

### Task 2: Mesin 15 ekspresi maskot

**Files:**
- Modify: `desktop/ui/mascot.py`
- Test: `desktop/tests/test_mascot.py` (create)

**Interfaces:**
- Produces:
  - `EMOTES: tuple[str, ...] = ("wink", "angry", "neutral", "happy", "excited", "laughing", "sad", "crying", "annoyed", "surprised", "confused", "shy", "sleepy", "thinking", "love")`
  - `EMOTE_LABELS: dict[str, str]` (label Indonesia/Inggris pendek).
  - `SLEEP_AFTER = 90.0`
  - `def emote_for_status(status: str, error_count: int = 0) -> str`
  - `def idle_emote(rng: random.Random | None = None) -> str`
  - `PochiMascot.set_emote(name: str) -> None`
  - `PochiMascot.current_emote() -> str`
  - `PochiMascot.poke() -> str` (siklus wink → happy → surprised → shy)
  - `PochiMascot.pet() -> str` (set `love`)
  - `PochiMascot.set_status(status: str) -> None` (tetap ada)
- Konsumsi: `Status` dari `app.state`.

- [ ] **Step 1: Tulis test yang gagal**

Buat `desktop/tests/test_mascot.py`:

```python
import os
import random
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

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

if __name__ == "__main__":
    failed = 0
    for fn in (test_mapping, test_all_15_render, test_poke_cycle,
               test_idle_emote_is_known, test_reduce_motion_has_no_timer):
        try:
            fn()
        except Exception as exc:
            print(f"FAIL {fn.__name__}: {exc}")
            failed += 1
        else:
            print(f"ok  {fn.__name__}")
    sys.exit(1 if failed else 0)
```

- [ ] **Step 2: Jalankan, pastikan gagal**

Run: `cd desktop && QT_QPA_PLATFORM=offscreen /usr/bin/python3 tests/test_mascot.py`
Expected: FAIL karena `EMOTES`/`emote_for_status` belum ada.

- [ ] **Step 3: Implementasi mesin ekspresi**

Tulis ulang bagian ekspresi `mascot.py`:

- `EMOTES`, `EMOTE_LABELS`, `EMOTE_BY_STATUS`, `IDLE_POOL`, `POKE_CYCLE`.
- `emote_for_status`: idle → neutral; listening → surprised; thinking/working → thinking; success → happy (jika `error_count` 0) atau excited; error → sad, tetapi `error_count >= 3` → annoyed; status tak dikenal → neutral.
- `idle_emote(rng)`: pilih dari `neutral/wink/confused/thinking`.
- `PochiMascot.__init__` tambah `_explicit_emote: str | None`, `_poke_index`, `_idle_since`, `_sleepy`. Timer `_tick` juga mengganti ekspresi idle tiap 20–40 dtk dan memanggil `set_emote("sleepy")` setelah `SLEEP_AFTER`.
- `set_status` menyimpan status; `current_emote` memprioritaskan `set_emote` eksplisit, lalu `sleepy`, lalu `emote_for_status`.
- `poke`/`pet` mengeset ekspresi eksplisit dan reset timer idle.
- Gambar 15 bentuk dengan QPainter: pil, caret, garis miring, `> <`, oval, hati, titik, mulut, blush, `?`, `zZ`, sparkle. Referensi bentuk: `pochi image/ekspresion.jpg`.
- `paintEvent` tetap menggambar backing squircle dengan rim glow ungu (`VIOLET_LIGHT`) dan latar `CARD_TOP → CARD_BOTTOM`.
- Hormati `reduce_motion`: timer berhenti, tidak ada blink/siklus.

- [ ] **Step 4: Jalankan test, pastikan lulus**

Run: `cd desktop && QT_QPA_PLATFORM=offscreen /usr/bin/python3 tests/test_mascot.py`
Expected: 5 `ok`, exit 0.

- [ ] **Step 5: Commit**

```bash
git add desktop/ui/mascot.py desktop/tests/test_mascot.py
git commit -m "feat(desktop): mesin 15 ekspresi Pochi"
```

---

### Task 3: Panel Ringkas (kartu musik + agenda)

**Files:**
- Modify: `desktop/ui/activity.py`
- Modify: `desktop/ui/island.py`
- Test: `desktop/tests/test_island.py` (create)

**Interfaces:**
- Produces di `activity.py`:
  - `class AgendaCard(QWidget)`: `__init__(controller, parent=None)`, `refresh() -> None`, label `next_text: QLabel`.
  - `MediaCard` tetap `__init__(media, on_lyrics=None, parent=None)`; tambah `cover: QLabel`, `empty: QLabel`, `progress: QProgressBar`, `time_now: QLabel`, `time_end: QLabel`; `set_media(data: dict) -> None` tetap.
- Produces di `island.py`:
  - `IslandWindow.page -> str` (`"ringkas"` | `"chat"`).
  - `IslandWindow.show_ringkas() -> None`, `IslandWindow.show_chat(focus: bool = True) -> None`.
  - `IslandWindow._ringkas_rect() -> QRect`, `_chat_rect() -> QRect`.
  - `IslandWindow.expand()` membuka Ringkas.
  - Atribut: `compact_mascot`, `music_card`, `agenda_card`, `ask_bar`, `ringkas_tab`, `chat_tab`, `stack`.
  - Konstanta `RINGKAS_SIZE = (560, 300)`, `CHAT_SIZE = (430, 620)`, `COMPACT_SIZE = (236, 42)`.
- Konsumsi: `theme`, `SoftCard`, `Pill`, `Sparkline`, `MediaCard`, `controller.tasks`.

- [ ] **Step 1: Tulis test yang gagal**

Buat `desktop/tests/test_island.py` (bagian Ringkas):

```python
import os
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

from PyQt6.QtCore import QObject, pyqtSignal
from PyQt6.QtWidgets import QApplication
from app.config import Config
from app.state import Controller
from integration.mock_adapter import MockAdapter
from platform_x11.x11 import work_area
from ui.island import IslandWindow

APP = QApplication.instance() or QApplication(sys.argv[:1])

class FakeMedia(QObject):
    changed = pyqtSignal(dict)
    def __init__(self):
        super().__init__()
        self._data = {}
    def acquire(self, owner): pass
    def release(self, owner): pass
    def snapshot(self): return dict(self._data)
    def previous(self): pass
    def play_pause(self): pass
    def next(self): pass

def make_window():
    cfg = Config(backend="demo", auto_collapse_seconds=0)
    ctl = Controller(cfg, MockAdapter(cfg))
    media = FakeMedia()
    return IslandWindow(cfg, ctl, media), ctl, media, cfg

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
    w.collapse(); ctl.shutdown()

def test_ringkas_empty_state():
    w, ctl, media, cfg = make_window()
    w.music_card.set_media({})
    w.agenda_card.refresh()
    assert w.music_card.isVisible() is False or w.music_card.empty.isVisible()
    assert w.agenda_card.next_text.text()
    w.collapse()
    ctl.shutdown()

def test_long_title_elides():
    w, ctl, media, cfg = make_window()
    long_title = "X" * 120
    w.music_card.set_media({"available": True, "title": long_title,
                            "artist": "Y" * 120, "playing": True})
    assert w.music_card.title.full_text() == long_title
    assert w.music_card.title.toolTip()
    w.collapse()
    ctl.shutdown()

if __name__ == "__main__":
    failed = 0
    for fn in (test_open_ringkas, test_compact_capsule,
               test_ringkas_fits_small_screen, test_ringkas_empty_state,
               test_long_title_elides):
        try:
            fn()
        except Exception as exc:
            print(f"FAIL {fn.__name__}: {exc}")
            failed += 1
        else:
            print(f"ok  {fn.__name__}")
    sys.exit(1 if failed else 0)
```

- [ ] **Step 2: Jalankan, pastikan gagal**

Run: `cd desktop && QT_QPA_PLATFORM=offscreen /usr/bin/python3 tests/test_island.py`
Expected: FAIL karena `page`, `ringkas_tab`, `ask_bar`, `music_card`, `agenda_card` belum ada.

- [ ] **Step 3: Implementasi Ringkas**

Di `activity.py`:

- Restyle `MediaCard` menjadi kartu `SoftCard` dengan cover 48×48 (placeholder gradien ungu), judul/artis `EllipsisLabel`, tombol prev/play/next/lirik, progress tipis, dan label waktu.
- Tambah `AgendaCard`: header bulan + tanggal besar, strip 5 hari (Sen–Jum/Sab–Min mengikuti `datetime.date.today()`), lalu task terdekat dari `controller.tasks` (jam selesai + label) atau teks "Tidak ada agenda". Sambungkan `controller.tasksChanged` ke `refresh`.

Di `island.py`:

- `_compute_sizes` menghitung `self._ringkas_w/h`, `self._chat_w/h`, dan kapsul `COMPACT_SIZE` (236×42) dikali `cfg.scale`; `compact_width` lama di-clamp minimal 236 agar config lama tidak membuat kapsul sempit.
- `_build_compact`: maskot 28 + label `Pochi`/status + `ActivityBars`; tanpa backing, dengan rim glow.
- `_build_expanded` menjadi: header (maskot 26, nama, tab `Ringkas`/`Chat`, gear, collapse) + `QStackedWidget` + halaman Ringkas + halaman Chat.
- Halaman Ringkas: `QHBoxLayout` berisi `MediaCard` dan `AgendaCard` (masing-masing `SoftCard`), lalu `ask_bar` (`QPushButton` bergaya input + tombol kirim) di bawah.
- `self.ringkas_tab` dan `self.chat_tab` adalah `QPushButton` checkable; klik `Ringkas` → `show_ringkas()`, klik `Chat` → `show_chat()`.
- `expand()` memanggil `show_ringkas()`; `show_ringkas()` mengatur stack index 0 + geometry; `show_chat()` index 1 + geometry (implementasi transisi di Task 4).
- `ask_bar.clicked` / `returnPressed` → `show_chat(focus=True)` (sementara cukup sinyal; transisi penuh di Task 4).
- `page` property.

- [ ] **Step 4: Jalankan test, pastikan lulus**

Run: `cd desktop && QT_QPA_PLATFORM=offscreen /usr/bin/python3 tests/test_island.py`
Expected: 5 `ok`, exit 0.

- [ ] **Step 5: Screenshot Ringkas**

Tambahkan ke test:

```python
def test_screenshot_ringkas():
    w, ctl, media, cfg = make_window()
    w.expand()
    APP.processEvents()
    out = Path(__file__).parent / "shots" / "9_ringkas.png"
    assert w.grab().save(str(out))
    w.collapse(); ctl.shutdown()
```

Jalankan lagi, lalu buka `desktop/tests/shots/9_ringkas.png` dan bandingkan dengan `docs/mockups/desktop-redesign-v1.png`.

- [ ] **Step 6: Commit**

```bash
git add desktop/ui/activity.py desktop/ui/island.py desktop/tests/test_island.py desktop/tests/shots/9_ringkas.png
git commit -m "feat(desktop): panel Ringkas dengan kartu musik + agenda"
```

---

### Task 4: Transisi Ringkas ↔ Chat, fokus, draft, Esc

**Files:**
- Modify: `desktop/ui/island.py`
- Modify: `desktop/ui/chat.py`
- Modify: `desktop/tests/test_island.py`

**Interfaces:**
- Produces:
  - `IslandWindow.show_chat(focus: bool = True) -> None`
  - `IslandWindow.show_ringkas() -> None`
  - `IslandWindow._escape() -> None` (Chat → Ringkas → collapsed)
  - `ChatPanel.set_empty_state(title: str, subtitle: str, chips: list[tuple[str, str]]) -> None` (opsional; boleh tetap seperti sekarang)
- Konsumsi: `QPropertyAnimation` yang sudah ada, `cfg.reduce_motion`.

- [ ] **Step 1: Tulis test transisi yang gagal**

Tambahkan ke `desktop/tests/test_island.py`:

```python
def test_ringkas_does_not_steal_focus():
    w, ctl, media, cfg = make_window()
    w.expand()
    assert not w.input.hasFocus()
    w.collapse(); ctl.shutdown()

def test_ask_bar_opens_chat():
    w, ctl, media, cfg = make_window()
    w.expand()
    w.ask_bar.click()
    assert w.page == "chat"
    assert w.stack.currentIndex() == 1
    w.collapse(); ctl.shutdown()

def test_escape_chat_to_ringkas_to_collapsed():
    w, ctl, media, cfg = make_window()
    w.expand(); w.show_chat()
    w._escape()
    assert w.page == "ringkas" and w.mode == "expanded"
    w._escape()
    assert w.mode == "collapsed"
    ctl.shutdown()

def test_draft_survives_collapse():
    w, ctl, media, cfg = make_window()
    w.expand(); w.show_chat()
    w.input.setText("setengah pesan")
    w.collapse(); w.expand(); w.show_chat()
    assert w.input.text() == "setengah pesan"
    w.collapse(); ctl.shutdown()

def test_rapid_toggle():
    w, ctl, media, cfg = make_window()
    for _ in range(5):
        w.expand(); w.show_chat(); w.show_ringkas(); w.collapse()
    assert w.mode == "collapsed"
    ctl.shutdown()
```

- [ ] **Step 2: Jalankan, pastikan gagal**

Run: `cd desktop && QT_QPA_PLATFORM=offscreen /usr/bin/python3 tests/test_island.py`
Expected: FAIL pada 4 test baru.

- [ ] **Step 3: Implementasi transisi + restyle Chat**

- `show_chat(focus=True)`: set `_page="chat"`, stack index 1, animasi geometry dari `_ringkas_rect()` ke `_chat_rect()` (240 ms; 0 ms jika reduce motion), lalu fokus input jika `focus`.
- `show_ringkas()`: simpan teks input ke `self._draft_text`, set `_page="ringkas"`, stack index 0, animasi ke `_ringkas_rect()`, jangan fokus input.
- `expand()`: buka Ringkas, **tidak** memanggil `_focus_input`.
- `collapse()`: simpan `self.input.text()`; saat `show_chat` berikutnya, pulihkan.
- `_escape`: jika page chat → `show_ringkas()`; jika page ringkas → `collapse()`.
- `keyPressEvent` Escape dan `_escape` memakai logika yang sama.
- `_on_focus_changed`: tetap collapse saat fokus keluar; jangan collapse jika `self._permission` ada.
- `ask_bar.clicked` → `show_chat(focus=True)`.
- `_on_header_dragged` tetap.
- Restyle `chat.py`: empty-state maskot 86, judul 18 px, subtitle 10 px, dua chip; bubble user memakai `theme.VIOLET` transparan, bubble asisten `SoftCard`; avatar 22 px; spacing 8 px. Tidak mengubah API `ChatPanel`.

- [ ] **Step 4: Jalankan test, pastikan lulus**

Run: `cd desktop && QT_QPA_PLATFORM=offscreen /usr/bin/python3 tests/test_island.py`
Expected: 10 `ok`, exit 0.

- [ ] **Step 5: Commit**

```bash
git add desktop/ui/island.py desktop/ui/chat.py desktop/tests/test_island.py
git commit -m "feat(desktop): transisi Ringkas-Chat, fokus, dan draft"
```

---

### Task 5: Izin tool + wiring status/ekspresi

**Files:**
- Modify: `desktop/ui/island.py`
- Modify: `desktop/tests/test_island.py`

**Interfaces:**
- Produces:
  - `IslandWindow._on_permission(payload)` membuka Chat otomatis.
  - `IslandWindow._error_count: int` dan pemetaan ke `emote_for_status`.
  - `IslandWindow.compact_mascot`, `header_mascot`, `chat_mascot` (widget maskot).
- Konsumsi: `emote_for_status`, `Status`.

- [ ] **Step 1: Tulis test yang gagal**

Tambahkan:

```python
def test_permission_opens_chat():
    w, ctl, media, cfg = make_window()
    w.expand()
    w._on_permission({"title": "Baca file", "detail": "x",
                      "options": [{"id": "allow", "label": "Izinkan",
                                   "kind": "allow"}]})
    assert w.page == "chat"
    assert w.permission.isVisible()
    assert not w._idle.isActive()
    w.collapse(); ctl.shutdown()

def test_status_sets_mascot():
    w, ctl, media, cfg = make_window()
    w._on_status(Status.SUCCESS)
    assert w.compact_mascot.current_emote() in {"happy", "excited"}
    w._on_status(Status.ERROR)
    assert w.compact_mascot.current_emote() in {"sad", "angry"}
    ctl.shutdown()
```

- [ ] **Step 2: Jalankan, pastikan gagal**

Run: `cd desktop && QT_QPA_PLATFORM=offscreen /usr/bin/python3 tests/test_island.py`
Expected: FAIL karena maskot belum tersambung/ekspresi belum berubah.

- [ ] **Step 3: Implementasi**

- `_on_permission`: set payload, isi banner, `show_chat(focus=False)`, `_idle.stop()`, jangan auto-collapse.
- `_on_status`: hitung `error_count` (naik saat ERROR, reset saat IDLE/SUCCESS), panggil `set_status`/`set_emote` pada `compact_mascot`, `header_mascot`, `chat_mascot`.
- `_set_busy`: guard jika `btn_send`/`input` belum ada.
- `compact_label` tetap "Pochi" saat idle; status saat sibuk.
- `ActivityBars.set_active(busy or listening)`.
- `closeEvent`/`apply_config` tetap.

- [ ] **Step 4: Jalankan test, pastikan lulus**

Run: `cd desktop && QT_QPA_PLATFORM=offscreen /usr/bin/python3 tests/test_island.py`
Expected: 12 `ok`, exit 0.

- [ ] **Step 5: Commit**

```bash
git add desktop/ui/island.py desktop/tests/test_island.py
git commit -m "feat(desktop): izin membuka Chat + wiring ekspresi status"
```

---

### Task 6: Reskin dashboard/settings/tray + verifikasi akhir

**Files:**
- Modify: `desktop/ui/dashboard.py`
- Modify: `desktop/ui/settings.py`
- Modify: `desktop/ui/tray.py`
- Modify: `desktop/ui/icons.py`
- Modify: `desktop/tests/smoke.py`
- Modify: `desktop/README.md`

**Interfaces:**
- Konsumsi: token `theme`, `SoftCard`, `Pill`, `Sparkline`.
- Produksi: tidak ada API baru; hanya tampilan.

- [ ] **Step 1: Ganti warna hardcoded**

Cari `QColor("#` / `rgba(` di empat file; ganti dengan token theme yang setara. Jangan mengubah tata letak kartu dashboard. Tambahkan `Sparkline` di kartu System untuk RAM/CPU hanya jika data nyata tersedia.

- [ ] **Step 2: Update smoke test**

Di `desktop/tests/smoke.py` tambahkan screenshot:

```python
grab(island, "9_ringkas.png")
island.show_chat()
grab(island, "10_chat_new.png")
```

Pastikan urutan buka/tutup tidak mengubah test lama.

- [ ] **Step 3: Cek kontras di wallpaper terang/gelap**

Buat dua gambar komposit di `desktop/tests/shots/`: panel `ringkas` di atas latar putih `#F4F1FA` dan hitam `#05030A`. Simpan sebagai `11_light_bg.png` dan `12_dark_bg.png`. Periksa manual: rim ungu, teks, dan kartu tetap terbaca di keduanya. Jika tidak, naikkan alpha border/rim (bukan mengubah latar panel).

- [ ] **Step 4: Jalankan semua test**

Run:
```bash
cd desktop
QT_QPA_PLATFORM=offscreen /usr/bin/python3 tests/test_design_tokens.py
QT_QPA_PLATFORM=offscreen /usr/bin/python3 tests/test_mascot.py
QT_QPA_PLATFORM=offscreen /usr/bin/python3 tests/test_island.py
QT_QPA_PLATFORM=offscreen /usr/bin/python3 tests/test_logic.py
QT_QPA_PLATFORM=offscreen /usr/bin/python3 tests/test_lyrics.py
```
Expected: semua `ok`, exit 0.

- [ ] **Step 5: Smoke X11**

Run: `cd desktop && DISPLAY=:0.0 /usr/bin/python3 tests/smoke.py`
Expected: selesai tanpa traceback; screenshot baru ada di `tests/shots/`.

- [ ] **Step 6: Cek manual**

Jalankan `DISPLAY=:0.0 /usr/bin/python3 main.py --demo`, lalu:
- kapsul bisa digeser, klik membuka Ringkas, tidak merebut fokus
- klik ask bar → Chat, Esc → Ringkas, Esc → collapsed
- media/timer/permission tampil
- dashboard/settings/tray tidak berubah fungsi
- wallpaper terang dan gelap sama-sama terbaca

- [ ] **Step 7: Commit**

```bash
git add desktop/ui/dashboard.py desktop/ui/settings.py desktop/ui/tray.py desktop/ui/icons.py desktop/tests/smoke.py desktop/README.md desktop/tests/shots
git commit -m "feat(desktop): reskin dashboard/settings/tray + verifikasi"
```

---

## Catatan eksekusi

- Task 1–2 bisa dikerjakan tanpa X11 (offscreen).
- Task 3–5 butuh QApplication; offscreen cukup untuk test, X11 untuk screenshot nyata.
- Jangan mengubah `docs/superpowers/specs/...` kecuali ada keputusan baru; catat di `docs/decisions.md`.
- Jika ada konflik dengan kode saat ini, ikuti spec dan Interfaces; jangan mengarang API baru tanpa memperbarui plan.
