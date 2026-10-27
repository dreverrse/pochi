"""Helper teks bersama untuk adapter (sanitasi rahasia, nama model)."""
from __future__ import annotations

import re

_SECRET_RE = re.compile(
    r"(?i)(sk-[a-z0-9_\-]{8,}|bearer\s+[a-z0-9._\-]{8,}|"
    r"(?:api[_-]?key|token|secret)\s*[:=]\s*\S+)"
)


def sanitize(text: str, limit: int = 240) -> str:
    """Buang kemungkinan kredensial dari teks yang akan ditampilkan."""
    def repl(m: re.Match) -> str:
        token = m.group(0)
        token = re.split(r"[:=]", token, maxsplit=1)[0]
        return token + "=…"

    text = _SECRET_RE.sub(repl, str(text))
    text = " ".join(text.strip().split())
    return text[:limit]


def model_short(model_id: str) -> str:
    m = model_id or ""
    for prefix in ("custom:kyle:", "custom:", "openrouter/"):
        if m.startswith(prefix):
            m = m[len(prefix):]
    return m or "model"


def extract_text(content) -> str:
    if isinstance(content, str):
        return content
    if isinstance(content, dict):
        inner = content.get("text")
        if inner is None and "content" in content:
            return extract_text(content.get("content"))
        return str(inner or "")
    if isinstance(content, list):
        return "".join(extract_text(c) for c in content)
    return ""
