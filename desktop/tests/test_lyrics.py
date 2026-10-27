"""Tes untuk services/lyrics.py (parser LRC + cache)."""
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from services.lyrics import LyricsCache, LyricsFetcher, parse_lrc  # noqa: E402


def test_parse_lrc():
    text = (
        "[00:12.34]Halo dunia\n"
        "[00:15.00][00:20.00]Dua timestamp\n"
        "[by: Something]\n"
        "[00:25]Tanpa pecahan\n"
        "bukan lirik\n"
    )
    out = parse_lrc(text)
    assert out == [
        (12340, "Halo dunia"),
        (15000, "Dua timestamp"),
        (20000, "Dua timestamp"),
        (25000, "Tanpa pecahan"),
    ], out


def test_cache_roundtrip():
    with tempfile.TemporaryDirectory() as td:
        cache = LyricsCache(Path(td) / "l.db")
        key = LyricsCache.key("Judul", "Artis", "Album", 180.0)
        assert cache.get(key) is None
        cache.put(key, "[00:01.00]x")
        assert cache.get(key) == "[00:01.00]x"
        key2 = LyricsCache.key("judul", "artis", "album", 195.0)
        assert key2 == key


def test_fetcher_uses_cache_without_network():
    import services.lyrics as mod
    with tempfile.TemporaryDirectory() as td:
        cache = LyricsCache(Path(td) / "l.db")
        key = LyricsCache.key("T", "A", "", 60.0)
        cache.put(key, "[00:03.00]ter-cache")
        f = LyricsFetcher(cache)

        orig = mod.fetch_lrclib
        mod.fetch_lrclib = lambda *a, **k: (_ for _ in ()).throw(
            AssertionError("tidak boleh akses jaringan"))
        try:
            got = f.get_synced("T", "A", "", 60.0)
            assert got == [(3000, "ter-cache")], got
        finally:
            mod.fetch_lrclib = orig


if __name__ == "__main__":
    failed = 0
    for fn in (test_parse_lrc, test_cache_roundtrip,
               test_fetcher_uses_cache_without_network):
        try:
            fn()
        except Exception as exc:  # noqa: BLE001
            print(f"FAIL {fn.__name__}: {exc}")
            failed += 1
        else:
            print(f"ok  {fn.__name__}")
    sys.exit(1 if failed else 0)
