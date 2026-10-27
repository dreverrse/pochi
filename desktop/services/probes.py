"""Probe koneksi nyata (tanpa data palsu).

Semua fungsi di sini hanya membaca status yang benar-benar tersedia:
port 9Router, keterjangkauan OpenRouter, proses gateway Hermes, dan state
platform Telegram yang ditulis gateway. Tidak ada token yang
dikembalikan/di-log; key OpenRouter hanya dibaca untuk header adapter.
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
OPENROUTER_BASE = "https://openrouter.ai/api/v1"
CONFIG_HOME = Path(os.environ.get("XDG_CONFIG_HOME", Path.home() / ".config"))
OPENROUTER_KEY_FILE = CONFIG_HOME / "pochi-desktop" / "openrouter.key"


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
    return _env_file_value(("HERMES_CUSTOM_LOCALHOST_20128_API_KEY",))


def _env_file_value(names, path: Path | None = None) -> str:
    """Baca nilai pertama yang cocok dari file .env bergaya KEY=VALUE."""
    wanted = set(names)
    try:
        for line in (path or (HERMES_HOME / ".env")).read_text().splitlines():
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            k, v = line.split("=", 1)
            if k.strip() in wanted:
                return v.strip().strip('"').strip("'")
    except Exception:
        pass
    return ""


def openrouter_api_key(path: Path | None = None) -> str:
    """Key OpenRouter: env → ~/.hermes/.env → file 0600 milik Pochi.

    Nilai tidak pernah ditampilkan di UI/log; hanya dipakai untuk header
    Authorization di proses adapter.
    """
    source = openrouter_key_source(path)
    if source == "env":
        for name in ("OPENROUTER_API_KEY", "POCHI_OPENROUTER_KEY"):
            val = os.environ.get(name, "").strip()
            if val:
                return val
        return ""
    if source == "hermes":
        return _env_file_value(("OPENROUTER_API_KEY", "POCHI_OPENROUTER_KEY"))
    if source == "file":
        try:
            return (path or OPENROUTER_KEY_FILE).read_text().strip()
        except Exception:
            pass
    return ""


def openrouter_key_source(path: Path | None = None) -> str:
    """Asal key yang menang: ``env`` | ``hermes`` | ``file`` | kosong."""
    for name in ("OPENROUTER_API_KEY", "POCHI_OPENROUTER_KEY"):
        if os.environ.get(name, "").strip():
            return "env"
    if _env_file_value(("OPENROUTER_API_KEY", "POCHI_OPENROUTER_KEY")):
        return "hermes"
    try:
        if (path or OPENROUTER_KEY_FILE).read_text().strip():
            return "file"
    except Exception:
        pass
    return ""


def save_openrouter_key(key: str, path: Path | None = None) -> None:
    """Simpan key ke file terpisah dengan izin 0600; string kosong menghapus."""
    target = path or OPENROUTER_KEY_FILE
    value = (key or "").strip()
    if not value:
        try:
            target.unlink()
        except FileNotFoundError:
            pass
        return
    target.parent.mkdir(parents=True, exist_ok=True)
    fd = os.open(target, os.O_WRONLY | os.O_CREAT | os.O_TRUNC | os.O_NOFOLLOW,
                 0o600)
    with os.fdopen(fd, "w", encoding="utf-8") as fh:
        fh.write(value + "\n")
    try:
        os.chmod(target, 0o600)
    except OSError:
        pass


def openrouter_key_hint(path: Path | None = None) -> str:
    """Petunjuk aman untuk UI: sumber + 4 karakter terakhir (tanpa key utuh)."""
    source = openrouter_key_source(path)
    if not source:
        return ""
    key = openrouter_api_key(path)
    tail = key[-4:] if len(key) >= 8 else "••••"
    label = {
        "env": "dari environment",
        "hermes": "dari ~/.hermes/.env",
        "file": "tersimpan",
    }.get(source, "tersimpan")
    return f"{label} · ••••{tail}"


def openrouter_status(timeout: float = 0.8) -> dict:
    ok, ms, err = tcp_probe(OPENROUTER_BASE, timeout)
    return {
        "ok": ok, "latency_ms": ms, "endpoint": "openrouter.ai:443",
        "detail": "Cloud terjangkau." if ok else (err or "Tidak terjangkau."),
    }


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
