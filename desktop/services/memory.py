"""Memori percakapan lintas sesi dan lintas backend.

Semua backend (OpenRouter, Hermes, 9Router) memakai memori yang sama:

- percakapan disimpan lokal di ``~/.local/share/pochi-desktop/memory.json``
  dengan izin 0600 (tidak pernah di-upload ke mana pun);
- setiap sesi baru diberi ringkas percakapan sesi sebelumnya sebagai konteks;
- backend yang punya sesi sendiri (Hermes ACP) menerima konteks itu sebagai
  preamble pada prompt pertama.

Penyimpanan dibuat defensif: kegagalan tulis/baca tidak boleh menjatuhkan
aplikasi, dan file selalu ditulis atomik agar tidak korup saat listrik mati.
"""
from __future__ import annotations

import json
import os
import threading
import time
from pathlib import Path

DATA_HOME = Path(os.environ.get("XDG_DATA_HOME",
                                Path.home() / ".local" / "share"))
MEMORY_FILE = DATA_HOME / "pochi-desktop" / "memory.json"

MAX_TURNS = 120          # total giliran yang disimpan
TURN_CHARS = 1400        # potong satu giliran yang terlalu panjang
SEED_TURNS = 12          # giliran lama yang dijadikan konteks
SEED_CHARS = 1600        # batas karakter konteks agar hemat token

SEED_HEADER = ("[Memori Pochi dari sesi/backend sebelumnya — ini konteks, "
               "bukan permintaan baru]")
SEED_ACK = "Baik, aku ingat konteks itu dan akan melanjutkan pembahasan."


class MemoryStore:
    """Daftar giliran percakapan yang persisten dan aman untuk dibaca ulang."""

    def __init__(self, path: Path | None = None, max_turns: int = MAX_TURNS):
        self.path = Path(path) if path is not None else MEMORY_FILE
        self.max_turns = max(1, int(max_turns))
        self._lock = threading.Lock()
        self._turns: list[dict] = []
        self._load()

    # ------------------------------------------------------------- disk I/O
    def _load(self) -> None:
        try:
            data = json.loads(self.path.read_text(encoding="utf-8"))
        except Exception:
            return
        turns = data.get("turns") if isinstance(data, dict) else None
        if not isinstance(turns, list):
            return
        clean = []
        for t in turns:
            if not isinstance(t, dict):
                continue
            role = t.get("role")
            text = t.get("text")
            if role not in ("user", "assistant") or not isinstance(text, str):
                continue
            clean.append({
                "role": role,
                "text": text[:TURN_CHARS],
                "ts": float(t.get("ts") or 0),
                "meta": str(t.get("meta") or ""),
                "session": str(t.get("session") or ""),
            })
        self._turns = clean[-self.max_turns:]

    def _save(self) -> None:
        try:
            self.path.parent.mkdir(parents=True, exist_ok=True)
            tmp = self.path.with_suffix(".json.tmp")
            fd = os.open(tmp, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
            with os.fdopen(fd, "w", encoding="utf-8") as fh:
                json.dump({"version": 1, "turns": self._turns}, fh,
                          ensure_ascii=False, indent=1)
            os.replace(tmp, self.path)
            try:
                os.chmod(self.path, 0o600)
            except OSError:
                pass
        except OSError:
            pass

    # ---------------------------------------------------------------- publik
    def add(self, role: str, text: str, *, meta: str = "",
            session: str = "", ts: float | None = None) -> None:
        text = (text or "").strip()
        if role not in ("user", "assistant") or not text:
            return
        with self._lock:
            self._turns.append({
                "role": role,
                "text": text[:TURN_CHARS],
                "ts": float(ts if ts is not None else time.time()),
                "meta": str(meta or "")[:24],
                "session": str(session or "")[:32],
            })
            if len(self._turns) > self.max_turns:
                del self._turns[:-self.max_turns]
            self._save()

    def recent(self, limit: int = SEED_TURNS,
               exclude_session: str = "") -> list[dict]:
        with self._lock:
            turns = [dict(t) for t in self._turns
                     if not exclude_session or t["session"] != exclude_session]
        return turns[-max(0, int(limit)):] if limit else []

    def seed_messages(self, limit: int = SEED_TURNS,
                      exclude_session: str = "",
                      max_chars: int = SEED_CHARS) -> list[dict]:
        """Dua pesan (user+assistant) berisi konteks sesi sebelumnya.

        Dipakai sebagai awalan ``history`` untuk OpenRouter/9Router dan
        sebagai preamble Hermes, sehingga semua backend "ingat" hal yang sama
        tanpa menduplikasi percakapan sesi yang sedang berjalan.
        """
        turns = self.recent(limit, exclude_session)
        lines: list[str] = []
        total = len(SEED_HEADER) + 1
        for turn in reversed(turns):
            text = " ".join(turn["text"].split())
            if not text:
                continue
            line = f"{'Pengguna' if turn['role'] == 'user' else 'Pochi'}: {text}"
            if len(line) > 500:
                line = line[:497] + "…"
            if total + len(line) + 1 > max_chars:
                break
            lines.append(line)
            total += len(line) + 1
        if not lines:
            return []
        lines.reverse()
        block = SEED_HEADER + "\n" + "\n".join(lines)
        return [
            {"role": "user", "content": block},
            {"role": "assistant", "content": SEED_ACK},
        ]

    @property
    def count(self) -> int:
        with self._lock:
            return len(self._turns)

    def clear(self) -> None:
        with self._lock:
            self._turns = []
            self._save()
