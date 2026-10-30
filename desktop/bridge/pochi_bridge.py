#!/usr/bin/env python3
"""
Pochi Bridge — jembatan agen coding ala Coucou (Tahap 13).

Server HTTP kecil (stdlib murni, tanpa dependensi) yang berjalan
di laptop dan menghubungkan agen coding (Claude Code lewat hook)
dengan Pochi di Android:

    POST /pochi/hook        <- hook agen (aktivitas sesi)
    POST /pochi/permission  <- hook PermissionRequest (menunggu
                               keputusan dari HP, maks ~110 detik)
    GET  /pochi/agents      -> { sessions, pendingApproval }
    POST /pochi/approve     <- { id, decision: allow|deny|always }
    GET  /pochi/health      -> { ok: true }

Jalankan:  python3 pochi_bridge.py [--port 8788]
Lalu pasang hook lewat pochi_hook.py (lihat README di folder ini).
"""

import argparse
import json
import os
import threading
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

STATE_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                          "pochi_bridge_state.json")

lock = threading.Lock()
sessions = {}            # session_id -> dict sesi
pending_approval = None  # dict izin yang sedang menunggu keputusan
approval_event = threading.Event()
approval_decision = {"value": None}

# Sesi yang tidak diperbarui selama ini dianggap selesai.
SESSION_TTL_SECONDS = 30 * 60


def now_ms():
    return int(time.time() * 1000)


def save_state():
    try:
        with open(STATE_FILE, "w", encoding="utf-8") as fh:
            json.dump({"sessions": sessions}, fh)
    except OSError:
        pass


def load_state():
    global sessions
    try:
        with open(STATE_FILE, "r", encoding="utf-8") as fh:
            sessions = json.load(fh).get("sessions", {})
    except (OSError, ValueError):
        sessions = {}


def prune_sessions():
    cutoff = now_ms() - SESSION_TTL_SECONDS * 1000
    stale = [sid for sid, s in sessions.items()
             if s.get("updated", 0) < cutoff and s.get("state") != "DONE"]
    for sid in stale:
        sessions[sid]["state"] = "IDLE"


def public_sessions():
    with lock:
        prune_sessions()
        items = sorted(sessions.values(),
                       key=lambda s: s.get("updated", 0), reverse=True)
        return items


def upsert_session(payload, **fields):
    sid = payload.get("session_id") or "default"
    cwd = payload.get("cwd") or ""
    project = os.path.basename(cwd.rstrip("/")) if cwd else ""
    with lock:
        sess = sessions.get(sid, {
            "id": sid,
            "agent": payload.get("pochi_agent") or "Claude Code",
            "project": project,
            "state": "IDLE",
            "action": "",
            "file": "",
            "plus": 0,
            "minus": 0,
        })
        if project:
            sess["project"] = project
        sess.update(fields)
        sess["updated"] = now_ms()
        sessions[sid] = sess
        save_state()
        return sess


def summarize_tool_input(tool_name, tool_input):
    """Ringkasan manusiawi untuk kartu izin & ticker."""
    if not isinstance(tool_input, dict):
        return tool_name or ""
    if tool_name == "Bash":
        cmd = (tool_input.get("command") or "")[:140]
        desc = tool_input.get("description") or ""
        return f"{cmd} — {desc}" if desc else cmd
    for key in ("file_path", "path", "notebook_path"):
        if tool_input.get(key):
            return str(tool_input[key])
    if tool_name in ("WebFetch", "WebSearch"):
        return str(tool_input.get("url") or tool_input.get("query") or "")
    return tool_name or ""


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):  # senyap
        pass

    def _send_json(self, code, obj):
        body = json.dumps(obj).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _read_json(self):
        length = int(self.headers.get("Content-Length") or 0)
        if length <= 0:
            return {}
        try:
            return json.loads(self.rfile.read(length).decode("utf-8"))
        except (ValueError, UnicodeDecodeError):
            return {}

    # ---------------- GET ----------------
    def do_GET(self):
        if self.path.startswith("/pochi/health"):
            self._send_json(200, {"ok": True, "bridge": "pochi"})
        elif self.path.startswith("/pochi/agents"):
            with lock:
                approval = pending_approval
            self._send_json(200, {
                "sessions": public_sessions(),
                "pendingApproval": approval,
            })
        else:
            self._send_json(404, {"error": "not found"})

    # ---------------- POST ----------------
    def do_POST(self):
        global pending_approval
        payload = self._read_json()

        if self.path.startswith("/pochi/hook"):
            self._handle_activity(payload)
            self._send_json(200, {"ok": True})

        elif self.path.startswith("/pochi/permission"):
            self._handle_permission(payload)

        elif self.path.startswith("/pochi/approve"):
            decision = payload.get("decision")
            with lock:
                if pending_approval and payload.get("id") == pending_approval["id"]:
                    approval_decision["value"] = decision
                    approval_event.set()
                    self._send_json(200, {"ok": True})
                else:
                    self._send_json(404, {"ok": False, "error": "no such approval"})
        else:
            self._send_json(404, {"error": "not found"})

    # ---------------- logika ----------------
    def _handle_activity(self, payload):
        event = payload.get("hook_event_name") or ""
        tool = payload.get("tool_name") or ""
        tool_input = payload.get("tool_input") or {}

        if event == "SessionStart":
            upsert_session(payload, state="IDLE", action="Sesi dimulai")
        elif event == "PreToolUse":
            upsert_session(
                payload, state="WORKING",
                action=f"Menjalankan {tool}" if tool else "Bekerja",
                file=os.path.basename(str(tool_input.get("file_path") or "")),
            )
        elif event == "PostToolUse":
            file_path = str(tool_input.get("file_path") or "")
            upsert_session(
                payload, state="WORKING",
                action=f"Selesai {tool}" if tool else "Bekerja",
                file=os.path.basename(file_path) if file_path else "",
            )
        elif event == "Stop":
            upsert_session(payload, state="DONE", action="Selesai")
        elif event == "SessionEnd":
            sid = payload.get("session_id") or "default"
            with lock:
                sessions.pop(sid, None)
                save_state()

    def _handle_permission(self, payload):
        global pending_approval
        tool = payload.get("tool_name") or "Tool"
        approval = {
            "id": uuid.uuid4().hex[:12],
            "agent": payload.get("pochi_agent") or "Claude Code",
            "tool": tool,
            "summary": summarize_tool_input(tool, payload.get("tool_input") or {}),
            "created": now_ms(),
        }
        with lock:
            pending_approval = approval
            approval_decision["value"] = None
            approval_event.clear()
        upsert_session(payload, state="WAITING",
                       action=f"Menunggu izin {tool}")

        # Tahan respons sampai HP memutuskan / batas waktu habis.
        decided = approval_event.wait(timeout=110)
        with lock:
            decision = approval_decision["value"] if decided else None
            if pending_approval and pending_approval["id"] == approval["id"]:
                pending_approval = None
        upsert_session(payload, state="WORKING",
                       action=f"Izin {tool} diputuskan")
        self._send_json(200, {"decision": decision})


def main():
    parser = argparse.ArgumentParser(description="Pochi Bridge")
    parser.add_argument("--port", type=int, default=8788)
    parser.add_argument("--host", default="0.0.0.0")
    args = parser.parse_args()

    load_state()
    server = ThreadingHTTPServer((args.host, args.port), Handler)
    print(f"Pochi Bridge berjalan di http://{args.host}:{args.port}")
    print("Endpoint HP: GET /pochi/agents — pastikan HP & laptop "
          "satu jaringan (Wi-Fi/Tailscale).")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nBerhenti.")


if __name__ == "__main__":
    main()
