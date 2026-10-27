"""Uji integrasi OpenRouter: key 0600, profil adapter, fallback, SSE.

Jalankan:
    QT_QPA_PLATFORM=offscreen /usr/bin/python3 tests/test_openrouter.py
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

from app.config import OPENROUTER_DEFAULT_MODEL, Config  # noqa: E402
from integration.auto_adapter import _candidates, create_bridge  # noqa: E402
from integration.router_adapter import (  # noqa: E402
    OpenRouterAdapter, RouterAdapter, parse_sse_line,
)
from services import probes  # noqa: E402
from services.probes import (  # noqa: E402
    OPENROUTER_BASE, openrouter_api_key, openrouter_key_hint,
    openrouter_key_source, save_openrouter_key,
)

ENV_NAMES = ("OPENROUTER_API_KEY", "POCHI_OPENROUTER_KEY")


def check(name: str, fn) -> None:
    started = time.monotonic()
    fn()
    print(f"ok  {name}  ({(time.monotonic() - started) * 1000:.0f} ms)")


class sandbox:
    """Isolasi env + ~/.hermes agar key asli di mesin tidak ikut terbaca."""

    def __enter__(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.saved_env = {k: os.environ.pop(k, None) for k in ENV_NAMES}
        self.saved_home = probes.HERMES_HOME
        probes.HERMES_HOME = self.root / "hermes"
        return self.root

    def __exit__(self, *exc):
        probes.HERMES_HOME = self.saved_home
        for k, v in self.saved_env.items():
            if v is None:
                os.environ.pop(k, None)
            else:
                os.environ[k] = v
        self.tmp.cleanup()


def test_key_file_roundtrip() -> None:
    with sandbox() as root:
        path = root / "pochi-desktop" / "openrouter.key"
        assert openrouter_api_key(path) == ""
        assert openrouter_key_hint(path) == ""
        save_openrouter_key("sk-or-v1-rahasia-abcd", path)
        assert openrouter_api_key(path) == "sk-or-v1-rahasia-abcd"
        assert openrouter_key_source(path) == "file"
        mode = stat.S_IMODE(path.stat().st_mode)
        assert mode == 0o600, oct(mode)
        hint = openrouter_key_hint(path)
        assert hint.endswith("abcd") and "rahasia" not in hint, hint
        assert hint.startswith("tersimpan"), hint
        save_openrouter_key("", path)
        assert not path.exists()
        assert openrouter_api_key(path) == ""
        assert openrouter_key_source(path) == ""


def test_key_priority_env_over_file() -> None:
    with sandbox() as root:
        path = root / "openrouter.key"
        save_openrouter_key("dari-file", path)
        os.environ["OPENROUTER_API_KEY"] = "dari-env"
        assert openrouter_api_key(path) == "dari-env"
        assert openrouter_key_source(path) == "env"
        assert openrouter_key_hint(path).startswith("dari environment")
        del os.environ["OPENROUTER_API_KEY"]
        assert openrouter_api_key(path) == "dari-file"
        # ~/.hermes/.env menjadi sumber kedua
        hermes = probes.HERMES_HOME
        hermes.mkdir(parents=True, exist_ok=True)
        (hermes / ".env").write_text("OPENROUTER_API_KEY=dari-hermes\n")
        assert openrouter_api_key(path) == "dari-hermes"
        assert openrouter_key_source(path) == "hermes"


def test_config_defaults() -> None:
    cfg = Config()
    assert cfg.openrouter_model == OPENROUTER_DEFAULT_MODEL
    cfg.openrouter_model = "   "
    cfg.apply_clamps()
    assert cfg.openrouter_model == OPENROUTER_DEFAULT_MODEL
    cfg2 = Config(backend="openrouter")
    cfg2.apply_clamps()
    assert cfg2.backend == "openrouter"


def test_profiles() -> None:
    cfg = Config(backend="openrouter", openrouter_model="google/gemini-2.5-flash")
    with sandbox() as root:
        os.environ["OPENROUTER_API_KEY"] = "kunci-uji"
        prof = OpenRouterAdapter(cfg).profile()
        assert prof.name == "openrouter" and prof.label == "OpenRouter"
        assert prof.base == OPENROUTER_BASE
        assert prof.model == "google/gemini-2.5-flash"
        assert prof.key == "kunci-uji"
        assert prof.tag == "OPENROUTER" and prof.where == "Cloud"
        assert "rahasia" not in prof.auth_hint.lower()
        del os.environ["OPENROUTER_API_KEY"]

        router = RouterAdapter(cfg).profile()
        assert router.name == "9router" and router.base == cfg.model_base
        assert router.model == cfg.model_name and router.tag == "9ROUTER"


def test_candidates() -> None:
    import integration.auto_adapter as auto_module

    original = auto_module.openrouter_api_key
    base = Config(backend="auto", hermes_command="__definitely_not_installed__")
    try:
        auto_module.openrouter_api_key = lambda: ""
        labels = [label for _f, label in _candidates(base)]
        assert labels == ["9Router", "DEMO"], labels
        auto_module.openrouter_api_key = lambda: "kunci-uji"
        labels = [label for _f, label in _candidates(base)]
        assert labels == ["9Router", "OpenRouter", "DEMO"], labels
    finally:
        auto_module.openrouter_api_key = original
    assert [label for _f, label in _candidates(Config(backend="openrouter"))] \
        == ["OpenRouter"]
    bridge = create_bridge(Config(backend="openrouter"))
    assert isinstance(bridge, OpenRouterAdapter)
    bridge.stop()


def test_sse_shapes() -> None:
    text, done, err = parse_sse_line(
        'data: {"choices":[{"delta":{"content":"halo"}}]}')
    assert (text, done, err) == ("halo", False, "")
    _text, done, err = parse_sse_line("data: [DONE]")
    assert done and not err
    text, _done, err = parse_sse_line(
        'data: {"error":{"message":"No auth credentials found"}}')
    assert not text and err and "auth" in err.lower()


def main() -> int:
    check("key file 0600 + hapus", test_key_file_roundtrip)
    check("prioritas env/.env/file", test_key_priority_env_over_file)
    check("config default & clamp", test_config_defaults)
    check("profil 9Router/OpenRouter", test_profiles)
    check("kandidat auto & explicit", test_candidates)
    check("parse SSE OpenRouter", test_sse_shapes)
    print("ALL OPENROUTER TESTS PASSED")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
