"""Uji integrasi nyata: Hermes ACP dan 9Router (butuh layanan berjalan).

Tidak memakai API key di sisi klien; kredensial dibaca adapter dari
environment/~/.hermes/.env. Jalankan:

    /usr/bin/python3 tests/live_adapters.py

Uji ini tidak dijalankan otomatis oleh ``test_logic.py`` karena bergantung
pada Hermes, 9Router, dan kuota model.
"""
from __future__ import annotations

import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from PyQt6.QtCore import QCoreApplication  # noqa: E402

from app.config import Config  # noqa: E402
from app.state import Controller, Status  # noqa: E402
from integration.hermes_adapter import HermesAcpAdapter  # noqa: E402
from integration.router_adapter import RouterAdapter  # noqa: E402

APP: QCoreApplication
FAILED: list[str] = []


def wait(predicate, timeout: float) -> bool:
    end = time.monotonic() + timeout
    while time.monotonic() < end:
        APP.processEvents()
        if predicate():
            return True
        time.sleep(0.01)
    APP.processEvents()
    return predicate()


def run(name: str, fn) -> None:
    started = time.monotonic()
    try:
        fn()
    except Exception as exc:  # noqa: BLE001 - ringkas untuk CLI
        FAILED.append(name)
        print(f"FAIL {name}: {type(exc).__name__}: {exc}")
    else:
        print(f"ok   {name} ({time.monotonic() - started:.1f} s)")


def _controller(cfg) -> Controller:
    bridge = (HermesAcpAdapter(cfg) if cfg.backend == "hermes"
              else RouterAdapter(cfg))
    return Controller(cfg, bridge)


def _chat(cfg, prompt: str, timeout: float):
    ctl = _controller(cfg)
    replies: list[str] = []
    ctl.replyReady.connect(replies.append)
    ctl.start()
    try:
        assert wait(lambda: ctl.connection.ok, 30.0), \
            f"koneksi gagal: {ctl.connection.label} {ctl.connection.detail}"
        assert ctl.connection.backend == cfg.backend, ctl.connection.backend
        assert ctl.send(prompt), "send ditolak"
        assert wait(lambda: bool(replies), timeout), "tidak ada balasan"
        reply = replies[-1]
        assert reply.strip(), "balasan kosong"
        assert ctl.messages[-1].meta.upper() == cfg.backend.upper(), \
            ctl.messages[-1].meta
        return ctl, reply
    finally:
        ctl.shutdown()


def test_router() -> None:
    cfg = Config(backend="9router", auto_collapse_seconds=0)
    ctl, reply = _chat(cfg, "Balas hanya dengan kata: PONG", 60.0)
    assert "PONG" in reply.upper(), reply[:120]
    print(f"     9Router: {reply.strip()[:80]!r}")


def test_hermes() -> None:
    cfg = Config(backend="hermes", auto_collapse_seconds=0,
                 hermes_command="hermes")
    ctl, reply = _chat(cfg, "Balas hanya dengan kata: PONG", 120.0)
    assert "PONG" in reply.upper(), reply[:120]
    print(f"     Hermes: {reply.strip()[:80]!r}")


def test_router_cancel() -> None:
    cfg = Config(backend="9router", auto_collapse_seconds=0)
    ctl = _controller(cfg)
    replies: list[str] = []
    ctl.replyReady.connect(replies.append)
    ctl.start()
    try:
        assert wait(lambda: ctl.connection.ok, 20.0), ctl.connection.detail
        assert ctl.send("Tulis esai 300 kata tentang sejarah Linux.")
        assert wait(lambda: ctl.busy, 5.0)
        time.sleep(0.4)
        ctl.cancel()
        assert wait(lambda: not ctl.busy, 6.0), "status tidak kembali idle"
        time.sleep(1.0)
        APP.processEvents()
        assert not replies, f"balasan final tetap dikirim setelah cancel: {replies!r}"
        partials = [m for m in ctl.messages
                    if m.role == "assistant" and m.streaming]
        assert not partials, "pesan streaming parsial tertinggal setelah cancel"
    finally:
        ctl.shutdown()


def test_hermes_cancel() -> None:
    cfg = Config(backend="hermes", auto_collapse_seconds=0,
                 hermes_command="hermes")
    ctl = _controller(cfg)
    replies: list[str] = []
    ctl.replyReady.connect(replies.append)
    ctl.start()
    try:
        assert wait(lambda: ctl.connection.ok, 30.0), ctl.connection.detail
        assert ctl.send("Tulis esai 300 kata tentang sejarah Linux.")
        assert wait(lambda: ctl.busy, 8.0)
        time.sleep(0.8)
        ctl.cancel()
        assert wait(lambda: ctl.status == Status.IDLE, 8.0), \
            f"status tetap {ctl.status}"
        time.sleep(1.2)
        APP.processEvents()
        assert not replies, f"balasan final tetap dikirim setelah cancel: {replies!r}"
    finally:
        ctl.shutdown()


def main() -> int:
    global APP
    APP = QCoreApplication(sys.argv[:1])
    run("9Router chat", test_router)
    run("9Router cancel", test_router_cancel)
    run("Hermes ACP chat", test_hermes)
    run("Hermes ACP cancel", test_hermes_cancel)
    if FAILED:
        print(f"\nFAILED: {', '.join(FAILED)}")
        return 1
    print("\nALL LIVE ADAPTER TESTS PASSED")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
