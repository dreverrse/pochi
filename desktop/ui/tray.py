"""Ikon tray Pochi — akses cepat saat island tertutup/tidak terlihat."""
from __future__ import annotations

from PyQt6.QtCore import pyqtSignal
from PyQt6.QtWidgets import QMenu, QSystemTrayIcon

from app import theme
from app.state import Status
from ui.icons import app_icon, make_icon


class PochiTray(QSystemTrayIcon):
    toggleRequested = pyqtSignal()
    dashboardRequested = pyqtSignal()
    timerRequested = pyqtSignal()
    settingsRequested = pyqtSignal()
    quitRequested = pyqtSignal()

    def __init__(self, controller, parent=None):
        super().__init__(app_icon(), parent)
        self.controller = controller
        self.setToolTip("Pochi")

        menu = QMenu()
        self.open_action = menu.addAction(
            make_icon("spark", theme.PURPLE, 15), "Buka Pochi")
        self.open_action.triggered.connect(self.toggleRequested)
        menu.addSeparator()
        dash = menu.addAction(
            make_icon("dashboard", theme.MUTED, 15), "Dashboard")
        dash.triggered.connect(self.dashboardRequested)
        timer = menu.addAction(make_icon("timer", theme.MUTED, 15), "Timer…")
        timer.triggered.connect(self.timerRequested)
        settings = menu.addAction(
            make_icon("settings", theme.MUTED, 15), "Pengaturan…")
        settings.triggered.connect(self.settingsRequested)
        menu.addSeparator()
        quit_action = menu.addAction(
            make_icon("power", theme.RED, 15), "Keluar")
        quit_action.triggered.connect(self.quitRequested)
        self._menu = menu
        self.setContextMenu(menu)

        self.activated.connect(self._on_activated)
        controller.statusChanged.connect(lambda _s: self._refresh())
        controller.connectionChanged.connect(lambda _c: self._refresh())
        controller.errorOccurred.connect(self.notify_error)
        self._refresh()

    # ------------------------------------------------------------- actions
    def _on_activated(self, reason):
        if reason in (QSystemTrayIcon.ActivationReason.Trigger,
                      QSystemTrayIcon.ActivationReason.DoubleClick):
            self.toggleRequested.emit()

    def set_expanded(self, expanded: bool):
        self.open_action.setText("Tutup Pochi" if expanded else "Buka Pochi")
        self.open_action.setIcon(make_icon(
            "chevron-up" if expanded else "spark",
            theme.PURPLE if not expanded else theme.MUTED, 15))

    def _refresh(self):
        status = Status.LABELS.get(self.controller.status, self.controller.status)
        conn = self.controller.connection
        backend = conn.label or "menghubungkan"
        if conn.ok and conn.model:
            backend = f"{backend} · {conn.model}"
        self.setToolTip(f"Pochi · {status} · {backend}")

    def notify_error(self, message: str):
        if self.supportsMessages():
            self.showMessage("Pochi", message,
                             QSystemTrayIcon.MessageIcon.Warning, 5000)

    def close(self):
        self.hide()
