#!/usr/bin/env python3
"""
pochi_hook.py — hook Claude Code untuk Pochi Bridge (Tahap 13).

Dipanggil Claude Code lewat konfigurasi hooks di
~/.claude/settings.json. Membaca JSON peristiwa dari stdin dan
meneruskannya ke jembatan di laptop ini (localhost:8788):

- Peristiwa aktivitas (SessionStart, PreToolUse, PostToolUse,
  Stop, SessionEnd): kirim & lupakan — tidak pernah menahan
  agen (batas 2 detik, diam bila jembatan mati).
- PermissionRequest: menunggu keputusan dari Pochi di HP
  (Izinkan/Tolak/Selalu). Bila jembatan mati atau waktu habis
  tanpa keputusan, skrip keluar TANPA keputusan agar Claude
  Code kembali ke prompt izin biasa di terminal.

Contoh konfigurasi (gabungkan ke ~/.claude/settings.json):

{
  "hooks": {
    "SessionStart":  [{"hooks": [{"type": "command", "command": "python3 /path/ke/pochi_hook.py"}]}],
    "PreToolUse":    [{"hooks": [{"type": "command", "command": "python3 /path/ke/pochi_hook.py"}]}],
    "PostToolUse":   [{"hooks": [{"type": "command", "command": "python3 /path/ke/pochi_hook.py"}]}],
    "Stop":          [{"hooks": [{"type": "command", "command": "python3 /path/ke/pochi_hook.py"}]}],
    "SessionEnd":    [{"hooks": [{"type": "command", "command": "python3 /path/ke/pochi_hook.py"}]}],
    "PermissionRequest": [{"hooks": [{"type": "command", "command": "python3 /path/ke/pochi_hook.py", "timeout": 120}]}]
  }
}
"""

import json
import sys
import urllib.request

BRIDGE = "http://127.0.0.1:8788"


def post(path, payload, timeout):
    req = urllib.request.Request(
        BRIDGE + path,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8") or "{}")


def main():
    try:
        payload = json.load(sys.stdin)
    except (ValueError, OSError):
        return 0

    event = payload.get("hook_event_name") or ""

    if event == "PermissionRequest":
        try:
            result = post("/pochi/permission", payload, timeout=115)
        except Exception:
            return 0  # jembatan mati -> prompt terminal biasa
        decision = result.get("decision")
        if decision in ("allow", "always"):
            behavior = "allow"
        elif decision == "deny":
            behavior = "deny"
        else:
            return 0  # tanpa keputusan -> prompt terminal biasa
        out = {
            "hookSpecificOutput": {
                "hookEventName": "PermissionRequest",
                "decision": {"behavior": behavior},
            }
        }
        if behavior == "deny":
            out["hookSpecificOutput"]["decision"]["message"] = \
                "Ditolak dari Pochi di HP pemilik."
        print(json.dumps(out))
        return 0

    # Aktivitas: kirim & lupakan.
    try:
        post("/pochi/hook", payload, timeout=2)
    except Exception:
        pass
    return 0


if __name__ == "__main__":
    sys.exit(main())
