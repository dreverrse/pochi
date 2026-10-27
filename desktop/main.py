"""Pochi Dynamic Island — titik masuk aplikasi desktop.

Jalankan dengan Python sistem yang memiliki PyQt6:

    /usr/bin/python3 main.py

Opsi:
    --backend auto|hermes|9router|openrouter|demo   paksa backend untuk sesi ini
    --demo                                alias untuk --backend demo
    --no-tray                             jangan pasang ikon tray
    --dashboard                           buka dashboard saat mulai
"""
from __future__ import annotations

import argparse
import signal
import sys

from PyQt6.QtCore import QTimer
from PyQt6.QtGui import QFont
from PyQt6.QtWidgets import QApplication, QSystemTrayIcon

from app import theme
from app.config import BACKENDS, Config
from app.state import Controller
from integration.auto_adapter import create_bridge
from services.health import HealthService
from services.media import MediaService
from services.system import SystemMonitor
from ui.dashboard import DashboardWindow
from ui.fonts import load_fonts
from ui.icons import app_icon
from ui.island import IslandWindow
from ui.settings import SettingsDialog
from ui.timer_dialog import TimerDialog
from ui.tray import PochiTray


def _parse_args(argv):
    parser = argparse.ArgumentParser(
        prog="pochi-desktop",
        description="Pochi Dynamic Island untuk Linux (X11/XFCE).")
    parser.add_argument("--backend", choices=BACKENDS,
                        help="Paksa backend untuk sesi ini (tidak disimpan).")
    parser.add_argument("--demo", action="store_true",
                        help="Jalankan mode DEMO berlabel (tanpa backend nyata).")
    parser.add_argument("--no-tray", action="store_true",
                        help="Jangan pasang ikon tray.")
    parser.add_argument("--dashboard", action="store_true",
                        help="Buka dashboard saat aplikasi mulai.")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = _parse_args(sys.argv[1:] if argv is None else argv)
    cfg = Config.load()
    if args.demo:
        cfg.backend = "demo"
    elif args.backend:
        cfg.backend = args.backend
    cfg.apply_clamps()

    app = QApplication(sys.argv[:1])
    app.setApplicationName("Pochi")
    app.setApplicationDisplayName("Pochi Dynamic Island")
    app.setDesktopFileName("pochi-desktop")
    app.setWindowIcon(app_icon())
    app.setQuitOnLastWindowClosed(False)
    family = load_fonts()
    app.setFont(QFont(family, 10))
    app.setStyleSheet(theme.qss(family))

    # Ctrl+C di terminal tetap menutup aplikasi dengan rapi.
    signal.signal(signal.SIGINT, lambda *_: app.quit())
    signal.signal(signal.SIGTERM, lambda *_: app.quit())
    sig_timer = QTimer()
    sig_timer.setInterval(300)
    sig_timer.timeout.connect(lambda: None)
    sig_timer.start()

    bridge = create_bridge(cfg)
    controller = Controller(cfg, bridge)
    monitor = SystemMonitor(cfg)
    media = MediaService()
    health = HealthService(cfg)

    island = IslandWindow(cfg, controller, media)
    dashboard = DashboardWindow(cfg, controller, monitor, health)

    # ------------------------------------------------------------- actions
    def open_chat():
        dashboard.hide()
        island.present()

    def open_settings():
        before = {
            "backend": cfg.backend,
            "model_name": cfg.model_name,
            "model_base": cfg.model_base,
            "request_timeout": cfg.request_timeout,
            "hermes_command": cfg.hermes_command,
        }
        dialog = SettingsDialog(cfg, island, controller)

        def apply_saved():
            island.apply_config()
            dashboard.apply_config()
            monitor.set_interval(cfg.system_poll_ms)
            if dashboard.isVisible():
                dashboard.present(island)
            changed = any(getattr(cfg, key) != value
                          for key, value in before.items())
            if changed:
                controller.replace_bridge(create_bridge(cfg))
                controller.notify(
                    "Pengaturan disimpan",
                    "Backend dimulai ulang dengan konfigurasi baru.", "info")
            else:
                controller.notify(
                    "Pengaturan disimpan", "Perubahan tampilan diterapkan.",
                    "info")

        dialog.saved.connect(apply_saved)
        dialog.exec()

    def open_timer():
        TimerDialog(controller, island).exec()

    island.dashboardRequested.connect(lambda: dashboard.present(island))
    island.settingsRequested.connect(open_settings)
    island.timerRequested.connect(open_timer)
    island.quitRequested.connect(app.quit)

    dashboard.chatRequested.connect(open_chat)
    dashboard.settingsRequested.connect(open_settings)
    dashboard.timerRequested.connect(open_timer)
    dashboard.hideRequested.connect(dashboard.hide)

    tray = None
    if not args.no_tray and QSystemTrayIcon.isSystemTrayAvailable():
        tray = PochiTray(controller, app)
        tray.toggleRequested.connect(island.toggle)
        tray.dashboardRequested.connect(lambda: dashboard.present(island))
        tray.timerRequested.connect(open_timer)
        tray.settingsRequested.connect(open_settings)
        tray.quitRequested.connect(app.quit)
        island.expandedChanged.connect(tray.set_expanded)
        tray.show()

    def cleanup():
        sig_timer.stop()
        try:
            controller.shutdown()
        except Exception:
            pass
        monitor.stop()
        media.stop()
        if tray is not None:
            tray.hide()
        dashboard.hide()
        island.hide()

    app.aboutToQuit.connect(cleanup)

    island.show_without_activating()
    controller.start()
    if not cfg.start_collapsed:
        QTimer.singleShot(250, island.present)
    if args.dashboard:
        QTimer.singleShot(300, lambda: dashboard.present(island))

    return app.exec()


if __name__ == "__main__":
    raise SystemExit(main())
