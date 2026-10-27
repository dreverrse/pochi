"""Lirik sinkron via LRCLIB (https://lrclib.net).

- Endpoint GET /api/get (track_name, artist_name, album_name, duration).
  Fallback: /api/search (q).
- Parse format LRC -> daftar (timestamp_ms, teks).
- Cache lokal SQLite di ~/.local/share/pochi/lyrics_cache.db agar tidak
  mengambil ulang.
- Pembatalan saat lagu berganti (generation).
"""
from __future__ import annotations

import hashlib
import json
import re
import sqlite3
import threading
import urllib.parse
import urllib.request
from pathlib import Path

LRCLIB_BASE = "https://lrclib.net/api"
USER_AGENT = "PochiDesktop/0.1 (lyrics)"

# Tempat data bersama (PRD: ~/.local/share/pochi/)
def data_dir() -> Path:
    return Path.home() / ".local" / "share" / "pochi"


# ---------------------------------------------------------------- LRC parser
_LINE_RE = re.compile(
    r"\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?\]"
    r"(?:\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?\])?"
    r"(.*)"
)


def _to_ms(m: str, s: str, frac: str | None) -> int:
    ms = int(s) * 1000 + int(m) * 60000
    if frac:
        if len(frac) == 1:
            ms += int(frac) * 100
        elif len(frac) == 2:
            ms += int(frac) * 10
        else:
            ms += int(frac)
    return ms


def parse_lrc(text: str) -> list[tuple[int, str]]:
    """Ubah LRC -> daftar terurut (ms, teks). Baris tanpa timestamp dilewati."""
    out: list[tuple[int, str]] = []
    for raw in text.splitlines():
        raw = raw.rstrip()
        if not raw or not raw.startswith("["):
            continue
        m = _LINE_RE.match(raw)
        if not m:
            continue
        m1, s1, f1, m2, s2, f2, lyric = m.groups()
        lyric = lyric.strip()
        if not lyric:
            continue
        ts = [_to_ms(m1, s1, f1)]
        if m2 is not None:
            ts.append(_to_ms(m2, s2, f2))
        for t in ts:
            out.append((t, lyric))
    out.sort(key=lambda x: x[0])
    return out


# ---------------------------------------------------------------- cache
class LyricsCache:
    def __init__(self, db: Path | None = None):
        self._db = db or (data_dir() / "lyrics_cache.db")
        self._lock = threading.Lock()
        self._conn: sqlite3.Connection | None = None

    def _connect(self) -> sqlite3.Connection:
        if self._conn is None:
            self._db.parent.mkdir(parents=True, exist_ok=True)
            self._conn = sqlite3.connect(str(self._db))
            self._conn.execute(
                "CREATE TABLE IF NOT EXISTS lyrics_cache("
                " track_key TEXT PRIMARY KEY,"
                " lrc_text TEXT,"
                " fetched_at INTEGER)"
            )
        return self._conn

    @staticmethod
    def key(track: str, artist: str, album: str, duration: float) -> str:
        basis = "|".join([track.strip().lower(), artist.strip().lower(),
                          album.strip().lower(), str(int(duration // 30))])
        return hashlib.sha256(basis.encode("utf-8")).hexdigest()[:24]

    def get(self, track_key: str) -> str | None:
        with self._lock:
            cur = self._connect().execute(
                "SELECT lrc_text FROM lyrics_cache WHERE track_key=?",
                (track_key,))
            row = cur.fetchone()
        return row[0] if row else None

    def put(self, track_key: str, lrc: str) -> None:
        with self._lock:
            self._connect().execute(
                "INSERT OR REPLACE INTO lyrics_cache(track_key,lrc_text,fetched_at)"
                " VALUES(?,?,?)",
                (track_key, lrc, int(__import__("time").time())))
            self._connect().commit()


# ---------------------------------------------------------------- client
def _http_json(url: str, timeout: float = 8.0) -> dict:
    req = urllib.request.Request(url, headers={
        "User-Agent": USER_AGENT, "Accept": "application/json"})
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8"))


def fetch_lrclib(track: str, artist: str = "", album: str = "",
                 duration: float = 0.0, timeout: float = 8.0) -> str | None:
    """Kembalikan teks LRC sinkron (atau plain) dari LRCLIB, atau None."""
    q = {"track_name": track}
    if artist:
        q["artist_name"] = artist
    if album:
        q["album_name"] = album
    if duration > 0:
        q["duration"] = str(int(duration))
    url = f"{LRCLIB_BASE}/get?" + urllib.parse.urlencode(q)
    try:
        data = _http_json(url, timeout)
    except Exception:
        data = None

    if data is None:
        # fallback search
        search_url = (f"{LRCLIB_BASE}/search?q="
                      + urllib.parse.quote(f"{artist} {track}".strip()))
        try:
            res = _http_json(search_url, timeout)
        except Exception:
            return None
        if not isinstance(res, list) or not res:
            return None
        data = res[0]

    if not isinstance(data, dict):
        return None
    sect = data.get("syncedLyrics") or data.get("plainLyrics")
    return sect if sect else None


class LyricsFetcher:
    """Bungkus fetch + cache + sinkronisasi (aman dipanggil dari thread)."""

    def __init__(self, cache: LyricsCache | None = None):
        self._cache = cache or LyricsCache()

    def get_synced(self, track: str, artist: str, album: str,
                   duration: float, timeout: float = 8.0) -> list[tuple[int, str]]:
        key = LyricsCache.key(track, artist, album, duration)
        text = self._cache.get(key)
        if text is None:
            text = fetch_lrclib(track, artist, album, duration, timeout)
            if text:
                self._cache.put(key, text)
        if not text:
            return []
        return parse_lrc(text)
