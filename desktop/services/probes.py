"""Probe koneksi nyata (tanpa data palsu).

Semua fungsi di sini hanya membaca status yang benar-benar tersedia:
port 9Router, proses gateway Hermes, dan state platform Telegram yang
ditulis gateway. Tidak ada token yang dikembalikan/di-log.
"""
from __future__ import annotations

import json
import os
import shutil
import socket
import time
from pathlib import Path
from urllib.parse import urlsplit

HERMES_HOME = Path(os.environ.get("HERMES_HOME", Path.home() / ".hermes"))
ROUTER_BASE = "http://127.0.0.1:20128/v1"


def _split_target(base: str) -> tuple[str, int]:
    u = urlsplit(base if "://" in base else "http://" + base)
    host = u.hostname or "127.0.0.1"
    port = u.port or (443 if u.scheme == "https" else 80)
    return host, int(port)


def tcp_probe(base: str = ROUTER_BASE, timeout: float = 0.8) -> tuple[bool, int, str]:
    host, port = _split_target(base)
    started = time.monotonic()
    try:
        with socket.create_connection((host, port), timeout=timeout):
            pass
        return True, int((time.monotonic() - started) * 1000), ""
    except Exception as exc:
        return False, 0, f"{host}:{port} tidak merespons"


def has_command(command: str) -> bool:
    try:
        parts = command.split()
        return bool(parts and shutil.which(parts[0]))
    except Exception:
        return False


def gateway_status(home: Path = HERMES_HOME) -> dict:
    """Baca state gateway Hermes + status Telegram (real, bukan asumsi)."""
    path = home / "gateway_state.json"
    out = {
        "running": False, "pid": 0, "telegram": "unknown",
        "updated": "", "detail": "State gateway tidak ditemukan.",
    }
    try:
        data = json.loads(path.read_text())
    except Exception:
        if (home / "gateway.pid").exists():
            out["detail"] = "File state gateway belum tersedia."
        return out
    pid = int(data.get("pid") or 0)
    alive = False
    if pid > 0:
        try:
            os.kill(pid, 0)
            alive = True
        except Exception:
            alive = False
    out["pid"] = pid
    out["running"] = alive and data.get("gateway_state") == "running"
    tg = (data.get("platforms") or {}).get("telegram") or {}
    out["telegram"] = str(tg.get("state") or "not-configured")
    out["updated"] = str(data.get("updated_at") or "")
    if out["running"]:
        out["detail"] = "Gateway berjalan."
    elif alive:
        out["detail"] = f"Proses ada, state={data.get('gateway_state')}"
    else:
        out["detail"] = "Gateway tidak berjalan."
    return out


def router_status(base: str = ROUTER_BASE, timeout: float = 0.8) -> dict:
    ok, ms, err = tcp_probe(base, timeout)
    host, port = _split_target(base)
    return {
        "ok": ok, "latency_ms": ms,
        "endpoint": f"{host}:{port}",
        "detail": "Port terbuka." if ok else (err or "Tidak terjangkau."),
    }


def router_api_key() -> str:
    """Ambil kredensial 9Router dari env / ~/.hermes/.env (tidak pernah di-log)."""
    for name in ("POCHI_ROUTER_KEY", "HERMES_CUSTOM_LOCALHOST_20128_API_KEY"):
        val = os.environ.get(name, "").strip()
        if val:
            return val
    env_file = HERMES_HOME / ".env"
    try:
        for line in env_file.read_text().splitlines():
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            k, v = line.split("=", 1)
            if k.strip() == "HERMES_CUSTOM_LOCALHOST_20128_API_KEY":
                return v.strip().strip('"').strip("'")
    except Exception:
        pass
    return ""


def environment_summary() -> dict:
    """Ringkasan non-rahasia untuk README/diagnostik."""
    return {
        "session_type": os.environ.get("XDG_SESSION_TYPE", "unknown"),
        "desktop": os.environ.get("XDG_CURRENT_DESKTOP", "unknown"),
        "display": os.environ.get("DISPLAY", ""),
        "hermes_home": str(HERMES_HOME),
        "hermes_binary": shutil.which("hermes") or "",
        "router_base": ROUTER_BASE,
    }
