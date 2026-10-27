"""Komponen UI kecil yang dipakai ulang di island, dashboard, dan dialog."""
from __future__ import annotations

import math

from PyQt6.QtCore import QPoint, QPointF, QRectF, QSize, Qt, QTimer, pyqtSignal
from PyQt6.QtGui import (
    QColor, QFont, QLinearGradient, QPainter, QPainterPath, QPen,
)
from PyQt6.QtWidgets import (
    QDialog, QFrame, QHBoxLayout, QLabel, QProgressBar, QPushButton,
    QSizePolicy, QVBoxLayout, QWidget,
)

from app import theme
from ui.icons import make_icon


class IconButton(QPushButton):
    """Tombol ikon persegi dengan hover halus dan tooltip wajib."""

    def __init__(self, icon_name: str, tooltip: str = "", size: int = 32,
                 icon_size: int = 17, color: QColor | None = None,
                 hover_color: QColor | None = None, parent=None):
        super().__init__(parent)
        self._name = icon_name
        self._color = QColor(color or theme.MUTED)
        self._hover = QColor(hover_color or theme.TEXT)
        self.setObjectName("iconBtn")
        self.setFixedSize(size, size)
        self.setIconSize(QSize(icon_size, icon_size))
        self.setIcon(make_icon(icon_name, self._color, icon_size))
        self.setCursor(Qt.CursorShape.PointingHandCursor)
        self.setFocusPolicy(Qt.FocusPolicy.TabFocus)
        if tooltip:
            self.setToolTip(tooltip)
            self.setAccessibleName(tooltip)

    def set_icon_name(self, icon_name: str, color: QColor | None = None) -> None:
        self._name = icon_name
        if color is not None:
            self._color = QColor(color)
        self.setIcon(make_icon(self._name, self._color,
                               self.iconSize().width()))

    def enterEvent(self, e):
        self.setIcon(make_icon(self._name, self._hover, self.iconSize().width()))
        super().enterEvent(e)

    def leaveEvent(self, e):
        self.setIcon(make_icon(self._name, self._color, self.iconSize().width()))
        super().leaveEvent(e)


class Card(QFrame):
    """Kartu dashboard dengan header ikon + judul."""

    def __init__(self, title: str = "", icon_name: str = "",
                 accent: QColor | None = None, parent=None):
        super().__init__(parent)
        self.setObjectName("card")
        self._accent = QColor(accent or theme.PURPLE)
        self.v = QVBoxLayout(self)
        self.v.setContentsMargins(14, 12, 14, 12)
        self.v.setSpacing(9)
        self.header = QHBoxLayout()
        self.header.setSpacing(8)
        self.icon = QLabel()
        if icon_name:
            self.icon.setPixmap(make_icon(icon_name, self._accent, 16).pixmap(16, 16))
            self.icon.setFixedSize(16, 16)
            self.header.addWidget(self.icon)
        self.title = QLabel(title.upper())
        self.title.setObjectName("eyebrow")
        self.header.addWidget(self.title)
        self.header.addStretch(1)
        self.v.addLayout(self.header)

    def add(self, widget: QWidget, stretch: int = 0):
        self.v.addWidget(widget, stretch)
        return widget

    def add_layout(self, layout):
        self.v.addLayout(layout)


class Divider(QFrame):
    def __init__(self, parent=None):
        super().__init__(parent)
        self.setObjectName("divider")
        self.setFixedHeight(1)
        self.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Fixed)


class Dot(QWidget):
    def __init__(self, color: QColor | str = theme.MUTED, size: int = 8,
                 parent=None):
        super().__init__(parent)
        self._color = QColor(color)
        self.setFixedSize(size, size)
        self.setAttribute(Qt.WidgetAttribute.WA_TransparentForMouseEvents, True)

    def set_color(self, color: QColor | str) -> None:
        c = QColor(color)
        if c != self._color:
            self._color = c
            self.update()

    def paintEvent(self, _):
        p = QPainter(self)
        p.setRenderHint(QPainter.RenderHint.Antialiasing, True)
        p.setPen(Qt.PenStyle.NoPen)
        p.setBrush(self._color)
        p.drawEllipse(QRectF(0, 0, self.width(), self.height()))


class MetricRow(QWidget):
    """Baris metrik: label, nilai, progress tipis."""

    def __init__(self, label: str, color_name: str = "", parent=None):
        super().__init__(parent)
        self._label_text = label
        v = QVBoxLayout(self)
        v.setContentsMargins(0, 0, 0, 0)
        v.setSpacing(4)
        row = QHBoxLayout()
        row.setSpacing(6)
        self.name = QLabel(label)
        self.name.setObjectName("dim")
        self.value = QLabel("—")
        self.value.setStyleSheet(
            f"color:{theme.TEXT_SOFT.name()}; font-size:11px; font-weight:600;")
        row.addWidget(self.name)
        row.addStretch(1)
        row.addWidget(self.value)
        v.addLayout(row)
        self.bar = QProgressBar()
        self.bar.setRange(0, 100)
        self.bar.setValue(0)
        self.bar.setTextVisible(False)
        self.bar.setFixedHeight(4)
        if color_name:
            self.bar.setObjectName(color_name)
        v.addWidget(self.bar)

    def set_value(self, value: float | None, text: str = "") -> None:
        if value is None:
            self.bar.setValue(0)
            self.value.setText(text or "—")
            return
        self.bar.setValue(int(max(0, min(100, value))))
        self.value.setText(text or f"{value:.0f}%")


class PulseDots(QWidget):
    """Tiga titik berdenyut untuk aktivitas berjalan (reduced-motion aware)."""

    def __init__(self, color: QColor | str = theme.PURPLE, size: int = 22,
                 reduce_motion: bool = False, parent=None):
        super().__init__(parent)
        self._color = QColor(color)
        self._phase = 0.0
        self._reduce = reduce_motion
        self.setFixedSize(size, size)
        self._timer = QTimer(self)
        self._timer.setInterval(90)
        self._timer.timeout.connect(self._tick)
        self._refresh_timer()

    def _refresh_timer(self) -> None:
        if not self._reduce and self.isVisible():
            if not self._timer.isActive():
                self._timer.start()
        else:
            self._timer.stop()

    def showEvent(self, event):
        super().showEvent(event)
        self._refresh_timer()

    def hideEvent(self, event):
        super().hideEvent(event)
        self._timer.stop()

    def set_reduce_motion(self, value: bool) -> None:
        self._reduce = value
        self._refresh_timer()
        self.update()

    def set_color(self, color: QColor | str) -> None:
        c = QColor(color)
        if c != self._color:
            self._color = c
            self.update()

    def _tick(self):
        self._phase = (self._phase + 0.22) % math.tau
        self.update()

    def paintEvent(self, _):
        p = QPainter(self)
        p.setRenderHint(QPainter.RenderHint.Antialiasing, True)
        p.setPen(Qt.PenStyle.NoPen)
        w = self.width()
        for i in range(3):
            a = 0.35 + 0.65 * (0.5 + 0.5 * math.sin(self._phase + i * 1.2))
            c = QColor(self._color)
            c.setAlphaF(0.35 + 0.6 * a)
            p.setBrush(c)
            x = w * (0.22 + i * 0.28)
            p.drawEllipse(QRectF(x - w * 0.07, w / 2 - w * 0.07, w * 0.14, w * 0.14))


class Pill(QLabel):
    """Label kecil bergaya chip dengan warna sesuai nada status."""

    TONES = {"muted", "green", "violet", "amber", "red", "cyan"}

    def __init__(self, text: str = "", tone: str = "muted", parent=None):
        super().__init__(text, parent)
        self.setObjectName("pill")
        self.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.setSizePolicy(QSizePolicy.Policy.Maximum, QSizePolicy.Policy.Fixed)
        self.set_tone(tone)

    def set_tone(self, tone: str) -> None:
        self.setProperty("tone", tone if tone in self.TONES else "muted")
        style = self.style()
        if style is not None:
            style.unpolish(self)
            style.polish(self)
        self.update()


class SoftCard(QFrame):
    """Kartu membulat dengan gradien halus; isi ditaruh di ``body``."""

    def __init__(self, padding: int = 13, parent=None):
        super().__init__(parent)
        self.setObjectName("softCard")
        self.body = QVBoxLayout(self)
        self.body.setContentsMargins(padding, padding, padding, padding)
        self.body.setSpacing(8)


def sparkline_points(values: list[float], width: float,
                     height: float) -> list[QPointF]:
    """Titik polyline: x merata, y terbalik (nilai tinggi = y kecil)."""
    if not values:
        return []
    pad = 2.0
    usable = max(1.0, height - pad * 2)
    lo, hi = min(values), max(values)
    if hi - lo < 1e-9:
        ys = [height / 2.0 for _ in values]
    else:
        ys = [pad + (hi - v) / (hi - lo) * usable for v in values]
    if len(values) == 1:
        return [QPointF(0.0, ys[0])]
    step = width / (len(values) - 1)
    return [QPointF(i * step, y) for i, y in enumerate(ys)]


class Sparkline(QWidget):
    """Grafik garis mini tanpa dependency, cocok untuk RAM/CPU."""

    def __init__(self, color: QColor | None = None, parent=None):
        super().__init__(parent)
        self._color = QColor(color or theme.VIOLET)
        self.values: list[float] = []
        self.setMinimumHeight(28)
        self.setSizePolicy(QSizePolicy.Policy.Expanding,
                           QSizePolicy.Policy.Preferred)

    def set_values(self, values: list[float]) -> None:
        self.values = [float(v) for v in values]
        self.update()

    def paintEvent(self, _e) -> None:
        if len(self.values) < 2:
            return
        p = QPainter(self)
        p.setRenderHint(QPainter.RenderHint.Antialiasing)
        w, h = float(self.width()), float(self.height())
        pts = sparkline_points(self.values, w, h)

        line = QPainterPath(pts[0])
        for pt in pts[1:]:
            line.lineTo(pt)

        area = QPainterPath(line)
        area.lineTo(QPointF(pts[-1].x(), h))
        area.lineTo(QPointF(pts[0].x(), h))
        area.closeSubpath()

        grad = QLinearGradient(0, 0, 0, h)
        top = QColor(self._color)
        top.setAlpha(70)
        bottom = QColor(self._color)
        bottom.setAlpha(0)
        grad.setColorAt(0.0, top)
        grad.setColorAt(1.0, bottom)
        p.setPen(Qt.PenStyle.NoPen)
        p.setBrush(grad)
        p.drawPath(area)

        pen = QPen(self._color, 1.6)
        pen.setCapStyle(Qt.PenCapStyle.RoundCap)
        pen.setJoinStyle(Qt.PenJoinStyle.RoundJoin)
        p.setPen(pen)
        p.setBrush(Qt.BrushStyle.NoBrush)
        p.drawPath(line)


class EllipsisLabel(QLabel):
    """QLabel satu baris dengan elide otomatis."""

    def __init__(self, text: str = "", parent=None):
        super().__init__(parent)
        self._full = text
        self.setText(text)
        self.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Preferred)
        self.setMinimumWidth(10)

    def setText(self, text: str):  # noqa: N802 (Qt API)
        self._full = text
        super().setText(self._elided())

    def full_text(self) -> str:
        return self._full

    def resizeEvent(self, e):
        super().resizeEvent(e)
        super().setText(self._elided())

    def _elided(self) -> str:
        fm = self.fontMetrics()
        return fm.elidedText(self._full, Qt.TextElideMode.ElideRight,
                             max(10, self.width() - 2))


class DialogShell(QDialog):
    """Dialog frameless dengan panel membulat, shadow tipis, dan header."""

    closed = pyqtSignal()

    def __init__(self, title: str, subtitle: str = "", parent=None,
                 width: int = 460, height: int = 540):
        super().__init__(parent)
        self.setWindowFlags(Qt.WindowType.Dialog | Qt.WindowType.FramelessWindowHint
                            | Qt.WindowType.WindowStaysOnTopHint)
        self.setAttribute(Qt.WidgetAttribute.WA_TranslucentBackground, True)
        self.setModal(True)
        self.resize(width + 36, height + 36)
        self._drag: QPoint | None = None
        outer = QVBoxLayout(self)
        outer.setContentsMargins(18, 18, 18, 18)
        self.panel = QWidget(self)
        outer.addWidget(self.panel)
        v = QVBoxLayout(self.panel)
        v.setContentsMargins(18, 14, 18, 16)
        v.setSpacing(12)

        head = QHBoxLayout()
        head.setSpacing(10)
        titles = QVBoxLayout()
        titles.setSpacing(1)
        self.title_label = QLabel(title)
        self.title_label.setObjectName("title")
        titles.addWidget(self.title_label)
        self.subtitle_label = QLabel(subtitle)
        self.subtitle_label.setObjectName("dim")
        self.subtitle_label.setVisible(bool(subtitle))
        titles.addWidget(self.subtitle_label)
        head.addLayout(titles, 1)
        close = IconButton("close", "Tutup", 30, 15)
        close.clicked.connect(self.reject)
        head.addWidget(close)
        v.addLayout(head)

        div = Divider()
        v.addWidget(div)

        self.body = QVBoxLayout()
        self.body.setSpacing(10)
        v.addLayout(self.body, 1)

    def add_widget(self, w: QWidget):
        self.body.addWidget(w)
        return w

    def paintEvent(self, _):
        p = QPainter(self)
        p.setRenderHint(QPainter.RenderHint.Antialiasing, True)
        r = QRectF(18, 18, self.width() - 36, self.height() - 36)
        for i in range(5, 0, -1):
            a = 8 + i * 4
            p.setPen(Qt.PenStyle.NoPen)
            p.setBrush(QColor(0, 0, 0, min(50, a)))
            rr = r.adjusted(-i, -i + 2, i, i + 2)
            p.drawRoundedRect(rr, 18 + i, 18 + i)
        p.setBrush(theme.PANEL)
        p.setPen(QPen(theme.BORDER, 1))
        p.drawRoundedRect(r, 18, 18)

    def mousePressEvent(self, e):
        if e.button() == Qt.MouseButton.LeftButton and e.position().y() < 64:
            self._drag = e.globalPosition().toPoint() - self.frameGeometry().topLeft()
        super().mousePressEvent(e)

    def mouseMoveEvent(self, e):
        if self._drag is not None:
            self.move(e.globalPosition().toPoint() - self._drag)
        super().mouseMoveEvent(e)

    def mouseReleaseEvent(self, e):
        self._drag = None
        super().mouseReleaseEvent(e)

    def keyPressEvent(self, e):
        if e.key() == Qt.Key.Key_Escape:
            self.reject()
            return
        super().keyPressEvent(e)

    def reject(self):
        self.closed.emit()
        super().reject()


def icon_label(name: str, color: QColor | str = theme.MUTED,
               size: int = 16) -> QLabel:
    lab = QLabel()
    lab.setPixmap(make_icon(name, color, size).pixmap(size, size))
    lab.setFixedSize(size, size)
    return lab


def caption(text: str) -> QLabel:
    lab = QLabel(text)
    lab.setObjectName("dim")
    lab.setWordWrap(True)
    return lab


def heading(text: str) -> QLabel:
    lab = QLabel(text)
    lab.setObjectName("title")
    return lab


def bold_font(size: int = 13, weight: QFont.Weight = QFont.Weight.DemiBold) -> QFont:
    f = QFont()
    f.setPointSize(size)
    f.setWeight(weight)
    return f
