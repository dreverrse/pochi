"""Uji memori lintas sesi/backend (OpenRouter, Hermes, 9Router).

Jalankan:
    QT_QPA_PLATFORM=offscreen /usr/bin/python3 tests/test_memory.py
"""
from __future__ import annotations

import os
import stat
import sys
import tempfile
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

from PyQt6.QtCore import QCoreApplication  # noqa: E402

from app.config import Config  # noqa: E402
from app.state import Controller  # noqa: E402
from integration.bridge import PochiBridge  # noqa: E402
from integration.hermes_adapter import history_preamble  # noqa: E402
from services.memory import SEED_HEADER, MemoryStore  # noqa: E402


def check(name: str, fn) -> None:
    started = time.monotonic()
    fn()
    print(f"ok  {name}  ({(time.monotonic() - started) * 1000:.0f} ms)")


class RecordingBridge(PochiBridge):
    name = "test"
    label = "Test"

    def __init__(self):
        super().__init__()
        self.sent: list[tuple[str, list[dict], list]] = []

    def send(self, text, history=None, files=()):
        self.sent.append((text, list(history or []), list(files)))


def test_store_roundtrip_and_perms() -> None:
    with tempfile.TemporaryDirectory() as tmp:
        path = Path(tmp) / "pochi-desktop" / "memory.json"
        store = MemoryStore(path)
        assert store.count == 0
        store.add("user", "Aku suka kopi", session="s1", meta="OPENROUTER")
        store.add("assistant", "Baik", session="s1")
        store.add("system", "diabaikan", session="s1")
        store.add("user", "   ", session="s1")
        assert store.count == 2
        reloaded = MemoryStore(path)
        assert reloaded.count == 2
        assert reloaded.recent(1)[0]["text"] == "Baik"
        mode = stat.S_IMODE(path.stat().st_mode)
        assert mode == 0o600, oct(mode)
        reloaded.clear()
        assert MemoryStore(path).count == 0


def test_store_trim_and_session_filter() -> None:
    with tempfile.TemporaryDirectory() as tmp:
        store = MemoryStore(Path(tmp) / "memory.json", max_turns=3)
        for i in range(5):
            store.add("user", f"pesan {i}", session="lama")
        assert store.count == 3
        assert [t["text"] for t in store.recent(3)] == \
            ["pesan 2", "pesan 3", "pesan 4"]
        store.add("user", "sesi baru", session="baru")
        only_old = store.recent(10, exclude_session="baru")
        assert all(t["session"] == "lama" for t in only_old)
        assert not any(t["text"] == "sesi baru" for t in only_old)


def test_seed_messages_are_compact() -> None:
    with tempfile.TemporaryDirectory() as tmp:
        store = MemoryStore(Path(tmp) / "memory.json")
        for i in range(6):
            store.add("user", f"pertanyaan panjang nomor {i} " + "x" * 200,
                      session="lama")
            store.add("assistant", f"jawaban {i}", session="lama")
        seed = store.seed_messages(limit=20, exclude_session="baru",
                                   max_chars=700)
        assert len(seed) == 2
        assert seed[0]["role"] == "user" and seed[1]["role"] == "assistant"
        block = seed[0]["content"]
        assert block.startswith(SEED_HEADER)
        assert len(block) <= 700 + 20, len(block)
        assert "jawaban 5" in block          # yang terbaru dipertahankan
        assert "pendek" not in block
        # Sesi berjalan tidak dimasukkan lagi (hindari duplikasi history UI).
        assert store.seed_messages(exclude_session="lama") == []


def test_history_preamble() -> None:
    history = [
        {"role": "user", "content": "Namaku Kyle"},
        {"role": "assistant", "content": "Halo Kyle"},
        {"role": "tool", "content": "diabaikan"},
    ]
    text = history_preamble(history, max_chars=300)
    assert text.startswith("[Memori Pochi")
    assert "Pengguna: Namaku Kyle" in text
    assert "Pochi: Halo Kyle" in text
    assert "diabaikan" not in text
    assert 0 < len(text) <= 300 + 80
    assert history_preamble([]) == ""
    assert history_preamble([{"role": "user", "content": "  "}]) == ""


def test_controller_shares_memory_across_backends(app) -> None:
    with tempfile.TemporaryDirectory() as tmp:
        store = MemoryStore(Path(tmp) / "memory.json")
        store.add("user", "Namaku Kyle, aku suka kopi.", session="sesi-lama")
        store.add("assistant", "Siap, kucatat.", session="sesi-lama")
        bridge = RecordingBridge()
        ctl = Controller(Config(backend="openrouter", auto_collapse_seconds=0),
                         bridge, memory=store)

        assert ctl.send("Halo lagi")
        _text, history, _files = bridge.sent[-1]
        assert history[0]["content"].startswith(SEED_HEADER)
        assert "Kyle" in history[0]["content"]
        assert all(m["content"] != "Halo lagi" for m in history)
        assert store.count == 3                     # user baru tercatat

        ctl._on_message("Tentu, Kyle.", "OPENROUTER")
        assert store.count == 4
        assert store.recent(1)[0]["text"] == "Tentu, Kyle."

        ctl.forget_memory()
        assert store.count == 0
        assert ctl.send("Masih ingat?")
        _text, history2, _files = bridge.sent[-1]
        assert all(not m["content"].startswith(SEED_HEADER)
                   for m in history2)
        assert any(m["content"] == "Halo lagi" for m in history2)


def main() -> int:
    app = QCoreApplication.instance() or QCoreApplication([])
    check("store roundtrip + 0600", test_store_roundtrip_and_perms)
    check("trim + filter sesi", test_store_trim_and_session_filter)
    check("seed ringkas", test_seed_messages_are_compact)
    check("preamble Hermes", test_history_preamble)
    check("controller bagi memori", lambda: test_controller_shares_memory_across_backends(app))
    print("ALL MEMORY TESTS PASSED")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
