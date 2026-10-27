"""Pemantauan sistem nyata dari /proc dan /sys (tanpa dependensi eksternal).

Polling hanya aktif saat ada konsumen yang terlihat (island expanded atau
dashboard), supaya idle tetap hemat CPU.
"""
from __future__ import annotations

import glob
import os
import shutil
import time

from PyQt6.QtCore import QObject, QTimer, pyqtSignal


class SystemMonitor(QObject):
    updated = pyqtSignal(dict)

    def __init__(self, cfg, parent=None):
        super().__init__(parent)
        self.cfg = cfg
        self._consumers: set[str] = set()
        self._timer = QTimer(self)
        self._timer.setInterval(max(800, int(cfg.system_poll_ms)))
        self._timer.timeout.connect(self.refresh)
        self._last_cpu: tuple[int, int] | None = None
        self._last: dict = {}

    # ------------------------------------------------------------ gating
    def acquire(self, key: str) -> None:
        self._consumers.add(key)
        if not self._timer.isActive():
            self.refresh()
            self._timer.start()

    def release(self, key: str) -> None:
        self._consumers.discard(key)
        if not self._consumers:
            self._timer.stop()

    @property
    def active(self) -> bool:
        return self._timer.isActive()

    def set_interval(self, ms: int) -> None:
        self._timer.setInterval(max(800, int(ms)))

    def stop(self) -> None:
        self._consumers.clear()
        self._timer.stop()

    # ------------------------------------------------------------ reading
    def refresh(self) -> None:
        snap = self._snapshot()
        if snap != self._last:
            self._last = snap
            self.updated.emit(dict(snap))

    def _snapshot(self) -> dict:
        cpu = self._cpu_percent()
        mem = self._meminfo()
        bat = self._battery()
        disk = self._disk()
        return {
            "cpu_percent": cpu,
            "cores": os.cpu_count() or 0,
            "load1": self._load1(),
            "mem_percent": mem.get("percent", 0.0),
            "mem_used_gb": mem.get("used_gb", 0.0),
            "mem_total_gb": mem.get("total_gb", 0.0),
            "battery_percent": bat.get("percent"),
            "battery_status": bat.get("status", ""),
            "plugged": bat.get("plugged"),
            "temp_c": self._temperature(),
            "disk_percent": disk.get("percent", 0.0),
            "disk_used_gb": disk.get("used_gb", 0.0),
            "disk_total_gb": disk.get("total_gb", 0.0),
            "uptime_s": self._uptime(),
            "when": time.time(),
        }

    def _cpu_percent(self) -> float:
        try:
            with open("/proc/stat") as f:
                parts = f.readline().split()
            nums = [int(x) for x in parts[1:]]
            idle = nums[3] + (nums[4] if len(nums) > 4 else 0)
            total = sum(nums)
        except Exception:
            return 0.0
        prev, self._last_cpu = self._last_cpu, (total, idle)
        if prev is None or total <= prev[0]:
            return 0.0
        dt = total - prev[0]
        di = idle - prev[1]
        return round(max(0.0, min(100.0, (dt - di) * 100.0 / dt)), 1)

    @staticmethod
    def _meminfo() -> dict:
        d = {}
        try:
            with open("/proc/meminfo") as f:
                for line in f:
                    k, v = line.split(":", 1)
                    d[k] = int(v.strip().split()[0]) * 1024
        except Exception:
            return {}
        total = d.get("MemTotal", 0)
        avail = d.get("MemAvailable", d.get("MemFree", 0))
        used = max(0, total - avail)
        return {
            "total_gb": round(total / 1024**3, 1),
            "used_gb": round(used / 1024**3, 1),
            "percent": round(used * 100.0 / total, 1) if total else 0.0,
        }

    @staticmethod
    def _battery() -> dict:
        for p in sorted(glob.glob("/sys/class/power_supply/BAT*")):
            try:
                cap = int(open(f"{p}/capacity").read().strip())
                status = open(f"{p}/status").read().strip()
                return {"percent": cap, "status": status}
            except Exception:
                continue
        # desktop: cek adaptor AC
        plugged = None
        for p in glob.glob("/sys/class/power_supply/A*"):
            try:
                on = open(f"{p}/online").read().strip()
                plugged = on == "1"
                break
            except Exception:
                continue
        return {"percent": None, "status": "AC" if plugged else "", "plugged": plugged}

    @staticmethod
    def _temperature() -> float | None:
        temps = []
        for p in glob.glob("/sys/class/thermal/thermal_zone*/temp"):
            try:
                v = int(open(p).read().strip())
                if v > 1000:
                    v /= 1000.0
                if -10 < v < 150:
                    temps.append(v)
            except Exception:
                continue
        return round(max(temps), 1) if temps else None

    @staticmethod
    def _disk() -> dict:
        try:
            u = shutil.disk_usage("/")
            return {
                "percent": round(u.used * 100.0 / u.total, 1) if u.total else 0.0,
                "used_gb": round(u.used / 1024**3, 1),
                "total_gb": round(u.total / 1024**3, 1),
            }
        except Exception:
            return {}

    @staticmethod
    def _load1() -> float:
        try:
            return float(open("/proc/loadavg").read().split()[0])
        except Exception:
            return 0.0

    @staticmethod
    def _uptime() -> float:
        try:
            return float(open("/proc/uptime").read().split()[0])
        except Exception:
            return 0.0


def format_uptime(seconds: float) -> str:
    s = int(seconds)
    d, s = divmod(s, 86400)
    h, s = divmod(s, 3600)
    m, _ = divmod(s, 60)
    if d:
        return f"{d}h {h}j"
    if h:
        return f"{h}j {m}m"
    return f"{m}m"
