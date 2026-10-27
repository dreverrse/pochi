"""Uji logika murni Pochi (tanpa server, tanpa menampilkan jendela).

Jalankan:
    QT_QPA_PLATFORM=offscreen /usr/bin/python3 tests/test_logic.py
"""
from __future__ import annotations

import json
import os
import re
import sys
import tempfile
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

from PyQt6.QtCore import QCoreApplication  # noqa: E402

from app import config as config_module  # noqa: E402
from app.config import Config  # noqa: E402
from app.state import Controller, Status  # noqa: E402
from integration.auto_adapter import _candidates  # noqa: E402
from integration.hermes_adapter import parse_update  # noqa: E402
from integration.mock_adapter import MockAdapter  # noqa: E402
from integration.router_adapter import parse_sse_line  # noqa: E402
from integration.text import extract_text, model_short, sanitize  # noqa: E402
from services.probes import gateway_status  # noqa: E402


def check(name: str, fn) -> None:
    started = time.monotonic()
    fn()
    print(f"ok  {name}  ({(time.monotonic() - started) * 1000:.0f} ms)")


def wait_until(app, predicate, timeout: float = 4.0) -> bool:
    end = time.monotonic() + timeout
    while time.monotonic() < end:
        app.processEvents()
        if predicate():
            return True
        time.sleep(0.01)
    app.processEvents()
    return predicate()


# --------------------------------------------------------------------- tests
def test_sse_parser() -> None:
    text, done, err = parse_sse_line(
        'data: {"choices":[{"delta":{"content":"Halo"}}]}')
    assert (text, done, err) == ("Halo", False, ""), (text, done, err)

    text, done, err = parse_sse_line(
        'data: {"choices":[{"delta":{"content":"!"}}]}data: [DONE]')
    assert text == "!" and done and not err, (text, done, err)

    text, done, err = parse_sse_line(
        'data: {"choices":[{"delta":{"content":null}}]}')
    assert text == "" and not done and not err

    text, done, err = parse_sse_line(
        'data: {"error":{"message":"model overload"}}')
    assert text == "" and done and "overload" in err

    assert parse_sse_line("") == ("", False, "")
    assert parse_sse_line(": ping") == ("", False, "")
    assert parse_sse_line("data: not-json") == ("", False, "")


def test_acp_updates() -> None:
    assert parse_update({
        "sessionUpdate": "agent_message_chunk",
        "content": {"type": "text", "text": "Hai"},
    }) == {"_delta": "Hai"}
    assert parse_update({
        "sessionUpdate": "agent_thought_chunk",
        "content": {"type": "text", "text": "rahasia"},
    }) is None
    tool = parse_update({
        "sessionUpdate": "tool_call", "toolCallId": "t1",
        "title": "Baca file", "kind": "read", "status": "completed",
    })
    assert tool["state"] == "done" and tool["title"] == "Baca file"
    running = parse_update({
        "sessionUpdate": "tool_call_update", "toolCallId": "t1",
        "title": "Baca file", "status": "in_progress",
    })
    assert running["state"] == "running"
    plan = parse_update({
        "sessionUpdate": "plan",
        "entries": [{"content": "Cek repo", "status": "in_progress"}],
    })
    assert plan["title"] == "Rencana" and plan["detail"] == "Cek repo"
    assert parse_update({"sessionUpdate": "usage_update"}) is None


def test_text_helpers() -> None:
    assert extract_text({"type": "text", "text": "abc"}) == "abc"
    assert extract_text([{"text": "a"}, "b"]) == "ab"
    assert model_short("custom:inc/mercury-3") == "inc/mercury-3"
    assert model_short("") == "model"
    secret = sanitize("api_key=super-rahasia-123 token: abcdefgh")
    assert "super-rahasia-123" not in secret
    assert "abcdefgh" not in secret


def test_config_roundtrip() -> None:
    with tempfile.TemporaryDirectory() as tmp:
        old_dir, old_file = config_module.CONFIG_DIR, config_module.CONFIG_FILE
        config_module.CONFIG_DIR = Path(tmp)
        config_module.CONFIG_FILE = Path(tmp) / "config.json"
        try:
            cfg = Config(anchor="bogus", scale=9.0, y_offset=-4,
                         backend="mock", compact_width=10,
                         expanded_width=9999, auto_collapse_seconds=9999)
            cfg.apply_clamps()
            assert cfg.anchor == "top-center"
            assert cfg.scale == 1.35
            assert cfg.y_offset == 0
            assert cfg.backend == "demo"       # legacy mock → demo
            assert cfg.compact_width == 140
            assert cfg.expanded_width == 760
            assert cfg.auto_collapse_seconds == 600
            cfg.set_position(120, 80)
            cfg.save()
            data = json.loads((Path(tmp) / "config.json").read_text())
            for key in ("api_key", "token", "secret", "password"):
                assert key not in data
            loaded = Config.load()
            assert loaded.has_custom_position()
            assert (loaded.pos_x, loaded.pos_y) == (120, 80)
            loaded.reset_position()
            assert not loaded.has_custom_position()
        finally:
            config_module.CONFIG_DIR = old_dir
            config_module.CONFIG_FILE = old_file


def test_gateway_probe() -> None:
    with tempfile.TemporaryDirectory() as tmp:
        home = Path(tmp)
        state = {
            "pid": os.getpid(), "gateway_state": "running",
            "platforms": {"telegram": {"state": "connected"}},
            "updated_at": "2026-01-01T00:00:00Z",
        }
        (home / "gateway_state.json").write_text(json.dumps(state))
        out = gateway_status(home)
        assert out["running"] and out["telegram"] == "connected"
        (home / "gateway_state.json").write_text("{broken")
        out = gateway_status(home)
        assert not out["running"]


def test_candidates() -> None:
    cfg = Config(backend="auto", hermes_command="__definitely_not_installed__")
    import integration.auto_adapter as auto_module

    original = auto_module.openrouter_api_key
    auto_module.openrouter_api_key = lambda: ""   # isolasi dari key mesin ini
    try:
        labels = [label for _factory, label in _candidates(cfg)]
        assert labels == ["9Router", "DEMO"], labels
        auto_module.openrouter_api_key = lambda: "kunci-uji"
        labels = [label for _factory, label in _candidates(cfg)]
        assert labels == ["9Router", "OpenRouter", "DEMO"], labels
    finally:
        auto_module.openrouter_api_key = original
    demo = [label for _f, label in _candidates(Config(backend="demo"))]
    assert demo == ["DEMO"]
    hermes = [label for _f, label in _candidates(Config(backend="hermes"))]
    assert hermes == ["Hermes ACP"]


def test_controller_demo(app) -> None:
    cfg = Config(backend="demo", auto_collapse_seconds=0)
    bridge = MockAdapter(cfg)
    ctl = Controller(cfg, bridge)
    states: list[str] = []
    replies: list[str] = []
    ctl.statusChanged.connect(states.append)
    ctl.replyReady.connect(replies.append)
    ctl.start()
    assert ctl.connection.backend == "demo"
    assert ctl.connection.ok

    assert ctl.send("halo Pochi") is True
    assert ctl.busy
    assert ctl.send("pesan kedua") is False       # ditolak saat sibuk
    assert wait_until(app, lambda: bool(replies), 4.0)
    assert ctl.status == Status.SUCCESS
    assert len(ctl.messages) == 2
    assistant = ctl.messages[-1]
    assert assistant.role == "assistant" and not assistant.streaming
    assert assistant.meta == "DEMO"
    assert not assistant.text.startswith("(DEMO)")
    assert Status.THINKING in states and Status.SUCCESS in states

    assert wait_until(app, lambda: ctl.status == Status.IDLE, 3.0)

    # timer nyata + penyelesaian
    task = ctl.add_timer(60, "Uji timer")
    assert task in ctl.tasks
    shown = ctl.format_task(task)
    assert re.fullmatch(r"\d+:\d{2}", shown), shown
    minutes, seconds = shown.split(":")
    assert (int(minutes), int(seconds)) in {(0, 59), (1, 0)}, shown
    task.ends_at = time.monotonic() - 1
    ctl._tick_tasks()
    assert any(n.title == "Timer selesai" for n in ctl.notices)
    ctl.remove_task(task.id)
    assert not ctl.tasks

    # error terlihat dan bisa pulih
    ctl._on_error("Tes kegagalan")
    assert ctl.status == Status.ERROR
    assert ctl.messages[-1].error and ctl.messages[-1].meta == "ERROR"
    ctl.set_status(Status.IDLE)

    ctl.clear_notices()
    assert not ctl.notices
    ctl.shutdown()


def test_mock_cancel(app) -> None:
    cfg = Config(backend="demo", auto_collapse_seconds=0)
    bridge = MockAdapter(cfg)
    ctl = Controller(cfg, bridge)
    ctl.start()
    assert ctl.send("halo")
    ctl.cancel()
    assert not wait_until(app, lambda: any(
        m.role == "assistant" for m in ctl.messages), 0.8)
    assert ctl.status == Status.IDLE
    ctl.shutdown()


def main() -> int:
    app = QCoreApplication(sys.argv)
    tests = [
        ("SSE parser", test_sse_parser),
        ("ACP updates", test_acp_updates),
        ("text helpers", test_text_helpers),
        ("config roundtrip", test_config_roundtrip),
        ("gateway probe", test_gateway_probe),
        ("auto candidates", test_candidates),
        ("controller demo", lambda: test_controller_demo(app)),
        ("mock cancel", lambda: test_mock_cancel(app)),
    ]
    for name, fn in tests:
        check(name, fn)
    print("\nALL LOGIC TESTS PASSED")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
