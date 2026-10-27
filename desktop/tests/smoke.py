"""Smoke test UI nyata (butuh X11; jalankan DISPLAY=:0.0).

Memeriksa geometri compact/expanded, animasi, klik, Escape, focus-out,
streaming DEMO, timer, izin, dashboard, pengaturan, dan tray. Screenshot
disimpan ke ``tests/shots/`` untuk inspeksi manual.

    DISPLAY=:0.0 /usr/bin/python3 tests/smoke.py
"""
from __future__ import annotations

import sys
import tempfile
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from PyQt6.QtCore import QAbstractAnimation, Qt  # noqa: E402
from PyQt6.QtGui import QImage  # noqa: E402
from PyQt6.QtTest import QTest  # noqa: E402
from PyQt6.QtWidgets import QApplication, QSystemTrayIcon, QWidget  # noqa: E402

from app import config as config_module  # noqa: E402
from app import theme  # noqa: E402
from app.config import Config  # noqa: E402
from app.state import Controller, Status  # noqa: E402
from integration.mock_adapter import MockAdapter  # noqa: E402
from platform_x11.x11 import work_area  # noqa: E402
from services.health import HealthService  # noqa: E402
from services.memory import MemoryStore  # noqa: E402
from services.media import MediaService  # noqa: E402
from services.system import SystemMonitor  # noqa: E402
from ui.dashboard import DashboardWindow  # noqa: E402
from ui.fonts import load_fonts  # noqa: E402
from ui.island import SHADOW, IslandWindow  # noqa: E402
from ui.settings import SettingsDialog  # noqa: E402
from ui.tray import PochiTray  # noqa: E402

OUT = ROOT / "tests" / "shots"
OUT.mkdir(parents=True, exist_ok=True)

APP: QApplication


def wait_until(predicate, timeout: float = 4.0) -> bool:
    end = time.monotonic() + timeout
    while time.monotonic() < end:
        APP.processEvents()
        if predicate():
            return True
        QTest.qWait(15)
    APP.processEvents()
    return predicate()


def grab(widget, name: str) -> Path:
    APP.processEvents()
    path = OUT / name
    image = widget.grab()
    assert not image.isNull(), f"grab {name} null"
    assert image.save(str(path)), f"gagal menyimpan {name}"
    assert path.stat().st_size > 1000, f"{name} terlalu kecil"
    print(f"    screenshot: {path.relative_to(ROOT)} "
          f"({image.width()}x{image.height()})")
    return path


def near(color, rgb, tol=14) -> bool:
    return (abs(color.red() - rgb[0]) <= tol
            and abs(color.green() - rgb[1]) <= tol
            and abs(color.blue() - rgb[2]) <= tol)


def main() -> int:
    global APP
    app = QApplication(sys.argv[:1])
    APP = app
    app.setStyleSheet(theme.qss(load_fonts()))

    tmp = tempfile.TemporaryDirectory()
    old_dir, old_file = config_module.CONFIG_DIR, config_module.CONFIG_FILE
    config_module.CONFIG_DIR = Path(tmp.name)
    config_module.CONFIG_FILE = Path(tmp.name) / "config.json"

    cfg = Config(backend="demo", auto_collapse_seconds=0,
                 start_collapsed=True, reduce_motion=False,
                 toggle_key="Ctrl+Alt+F12")
    bridge = MockAdapter(cfg)
    ctl = Controller(cfg, bridge, memory=MemoryStore(Path(tmp.name) / "memory.json"))
    monitor = SystemMonitor(cfg)
    media = MediaService()
    health = HealthService(cfg)
    island = IslandWindow(cfg, ctl, media)
    dashboard = DashboardWindow(cfg, ctl, monitor, health)

    try:
        # ------------------------------------------------------- start & compact
        ctl.start()
        island.show_without_activating()
        APP.processEvents()
        QTest.qWait(80)
        assert island.isVisible()
        assert island.mode == "collapsed"
        assert ctl.connection.backend == "demo" and ctl.connection.ok
        assert "DEMO" in ctl.connection.label
        assert island.status_label.text() == "Idle"

        g = island.geometry()
        assert g.width() == island._compact_w, (g.width(), island._compact_w)
        assert g.height() == island._compact_h
        area = work_area()
        assert area.contains(g), (area, g)

        shot = QImage(str(grab(island, "1_collapsed.png")))
        px = shot.pixelColor(SHADOW + 6, shot.height() // 2)
        assert px.alpha() > 180, f"panel transparan? alpha={px.alpha()}"
        assert near(px, (8, 8, 11), 20), px.name()
        print("    compact geometry:", g)

        # -------------------------------------------------------------- expand
        QTest.mouseClick(island, Qt.MouseButton.LeftButton,
                         pos=island.rect().center())
        assert island.mode == "expanded", "klik pil harus membuka panel"
        assert wait_until(lambda: island._anim.state()
                          == QAbstractAnimation.State.Stopped
                          and island.geometry() == island._expanded_rect(),
                          1.0)
        APP.processEvents()
        eg = island.geometry()
        assert (eg.width(), eg.height()) == (island._expanded_w,
                                             island._expanded_h), eg
        assert area.contains(eg), (area, eg)
        assert island._expanded.isVisible() and not island._compact.isVisible()
        assert island.ask_bar.isVisible()
        assert island.music_card.isVisible()
        grab(island, "9_ringkas.png")
        island.show_chat()
        assert wait_until(lambda: island._anim.state()
                          == QAbstractAnimation.State.Stopped, 1.0)
        APP.processEvents()
        grab(island, "10_chat_new.png")
        print("    expanded geometry:", eg)

        # ------------------------------------------------------- demo streaming
        assert ctl.send("halo Pochi, apa kabar?")
        assert ctl.status in (Status.THINKING, Status.WORKING)
        APP.processEvents()
        assert island.ai_card.isVisible()
        assert island.btn_send.property("busy") == "true"
        assert wait_until(lambda: any(
            m.role == "assistant" and not m.streaming
            for m in ctl.messages), 5.0), "balasan DEMO tidak datang"
        reply = ctl.messages[-1]
        assert reply.meta == "DEMO" and not reply.text.startswith("(DEMO)")
        assert ctl.status == Status.SUCCESS
        assert island.chat.stack.currentIndex() == 1
        grab(island, "3_chat_reply.png")
        assert wait_until(lambda: ctl.status == Status.IDLE, 3.0)
        assert island.btn_send.property("busy") == "false"

        # --------------------------------------------------------------- timer
        task = ctl.add_timer(120, "Istirahat")
        APP.processEvents()
        assert island.timer_card.isVisible()
        assert island.agenda_card.next_text.text() != "Tidak ada agenda"
        assert task.remaining() > 0
        grab(island, "4_timer.png")
        ctl.remove_task(task.id)
        APP.processEvents()
        assert not island.timer_card.isVisible()

        # ----------------------------------------------------------- permission
        ctl._on_permission({
            "request_id": 99, "title": "Jalankan perintah",
            "detail": "echo halo", "options": [
                {"id": "allow", "label": "Izinkan", "kind": "allow_once"},
                {"id": "deny", "label": "Tolak", "kind": "reject_once"},
            ],
        })
        APP.processEvents()
        assert island.permission.isVisible()
        assert island.perm_buttons.count() >= 2
        ctl.respond_permission(None)
        APP.processEvents()
        assert not island.permission.isVisible()

        # -------------------------------------------------------- error & close
        ctl._on_error("Tes kegagalan koneksi")
        APP.processEvents()
        assert ctl.status == Status.ERROR
        assert ctl.messages[-1].error
        assert island.status_label.text() == "Error"
        grab(island, "5_error.png")
        ctl.set_status(Status.IDLE)
        APP.processEvents()

        # ------------------------------------------------------ Escape & close btn
        assert island.mode == "expanded"
        QTest.keyClick(island.input, Qt.Key.Key_Escape)
        assert island.page == "ringkas", "Esc dari Chat harus ke Ringkas"
        QTest.keyClick(island, Qt.Key.Key_Escape)
        assert island.mode == "collapsed", "Esc dari Ringkas harus menutup panel"
        QTest.qWait(300)

        QTest.mouseClick(island, Qt.MouseButton.LeftButton,
                         pos=island.rect().center())
        assert island.mode == "expanded"
        QTest.qWait(300)
        island.btn_close.click()
        assert island.mode == "collapsed"
        QTest.qWait(300)

        # ----------------------------------------------------------- focus out
        cfg.reduce_motion = True
        island.apply_config()
        island.expand(animate=False)
        island._expanded_at = time.monotonic() - 1
        dummy = QWidget()
        island._on_focus_changed(None, dummy)
        assert island.mode == "collapsed", "focus keluar harus menutup panel"
        dummy.deleteLater()

        # ------------------------------------------------------- duration check
        cfg.reduce_motion = False
        island.apply_config()
        assert island._duration(True) == 240
        island.expand(animate=True)
        assert island._anim.state() == QAbstractAnimation.State.Running, \
            "animasi expand harus berjalan"
        QTest.qWait(320)
        assert island.mode == "expanded"
        assert island.geometry() == island._expanded_rect()
        cfg.reduce_motion = True
        island.apply_config()
        assert island._duration(True) == 0

        # ----------------------------------------------------------- dashboard
        dash_chat = []
        dashboard.chatRequested.connect(lambda: dash_chat.append(True))
        dashboard.present(island)
        APP.processEvents()
        QTest.qWait(250)
        assert dashboard.isVisible()
        assert monitor.active and health.active
        assert dashboard.system.cpu.value.text() != "—"
        assert dashboard.connections.router.detail.full_text() != "Memeriksa…"
        assert dashboard.tasks.empty.isVisible()
        grab(dashboard, "6_dashboard.png")
        dashboard.actions.chat_btn.click()
        APP.processEvents()
        assert dash_chat, "Quick Action chat harus memancarkan sinyal"
        dashboard.hide()
        APP.processEvents()
        assert not monitor.active and not health.active

        # ------------------------------------------------------------ settings
        cfg.set_position(50, 50)
        dialog = SettingsDialog(cfg)
        dialog.show()
        APP.processEvents()
        QTest.qWait(80)
        assert dialog.pos_label.text() == "50, 50"
        grab(dialog, "7_settings.png")
        dialog.reset_pos.click()
        dialog.scale.setValue(1.1)
        dialog.reduce_motion.setChecked(True)
        dialog.anchor.setCurrentIndex(dialog.anchor.findData("top-right"))
        dialog.toggle_key.setKeySequence("Ctrl+Alt+F11")
        dialog.backend.setCurrentIndex(dialog.backend.findData("demo"))
        dialog.model.setText("inc/mercury-3")
        saved = []
        dialog.saved.connect(lambda: saved.append(True))
        dialog.accept()
        APP.processEvents()
        assert saved, "sinyal saved tidak dipancarkan"
        assert abs(cfg.scale - 1.1) < 1e-6
        assert cfg.reduce_motion
        assert cfg.anchor == "top-right"
        assert cfg.toggle_key == "Ctrl+Alt+F11"
        assert not cfg.has_custom_position()
        assert config_module.CONFIG_FILE.exists()
        text = config_module.CONFIG_FILE.read_text()
        for secret in ("api_key", "token", "secret", "password"):
            assert secret not in text.lower()

        island.apply_config()
        APP.processEvents()
        assert island.geometry() == island._compact_rect()
        assert bool(island.windowFlags()
                    & Qt.WindowType.WindowStaysOnTopHint) is True

        # ---------------------------------------------------------------- tray
        if QSystemTrayIcon.isSystemTrayAvailable():
            tray = PochiTray(ctl)
            tray.show()
            APP.processEvents()
            assert "Pochi" in tray.toolTip()
            tray.set_expanded(True)
            assert tray.open_action.text() == "Tutup Pochi"
            tray.hide()
            tray.deleteLater()
        else:
            print("    (tray tidak tersedia; dilewati)")

        # --------------------------------------------------------------- media
        if not media.snapshot().get("available"):
            assert "Tidak ada media" in island.music_card.empty.text()

        grab(island, "8_final_collapsed.png")
        print("\nALL UI SMOKE TESTS PASSED")
        return 0
    finally:
        dashboard.close()
        island.close()
        ctl.shutdown()
        monitor.stop()
        media.stop()
        config_module.CONFIG_DIR = old_dir
        config_module.CONFIG_FILE = old_file
        tmp.cleanup()


if __name__ == "__main__":
    raise SystemExit(main())
