"""9Router (OpenAI-compatible lokal) — adapter fallback langsung ke model.

Dipakai bila Hermes ACP tidak tersedia. Kredensial dibaca server-side dari
environment atau ``~/.hermes/.env`` dan tidak pernah dikirim ke UI/log.
Streaming SSE diparse secara defensif karena beberapa gateway menambahkan
``data: [DONE]`` di ekor baris.
"""
from __future__ import annotations

import base64
import http.client
import json
import queue
import socket
import ssl
import threading
from pathlib import Path
from urllib.parse import urlsplit

from PyQt6.QtCore import QThread, pyqtSignal

from integration.bridge import PochiBridge
from integration.text import extract_text, sanitize
from services.probes import gateway_api_key, router_api_key, router_status

SYSTEM_PROMPT = (
    "Kamu adalah Pochi, asisten AI desktop pribadi di Linux. "
    "Jawab ringkas, jelas, dan dalam bahasa yang dipakai pengguna. "
    "Jangan mengarang data sistem, media, atau pesan: jika data tidak "
    "tersedia, katakan terus terang."
)

_IMG_MIME = {
    "png": "image/png", "jpg": "image/jpeg", "jpeg": "image/jpeg",
    "gif": "image/gif", "bmp": "image/bmp", "webp": "image/webp",
}


def _content_parts(text: str, files) -> list[dict]:
    """Bangun konten multimodal (teks + gambar base64) untuk API OpenAI-compatible."""
    parts = [{"type": "text", "text": text}]
    notes = []
    for f in files:
        try:
            path = Path(f)
            mime = _IMG_MIME.get(path.suffix.lower().lstrip("."))
            data = path.read_bytes()
            if mime and data:
                b64 = base64.b64encode(data).decode("ascii")
                parts.append({"type": "image_url", "image_url": {
                    "url": f"data:{mime};base64,{b64}"}})
            else:
                notes.append(path.name or f)
        except Exception:
            notes.append(f)
    if notes:
        parts.append({"type": "text",
                      "text": "[lampiran: " + ", ".join(notes) + "]"})
    return parts


def parse_sse_line(line: str) -> tuple[str, bool, str]:
    """Kembalikan (teks, selesai, error) dari satu baris SSE.

    Toleran terhadap ``content: null``, chunk reasoning, dan suffix
    ``data: [DONE]`` yang menempel pada baris JSON.
    """
    line = (line or "").strip()
    if not line or line.startswith(":"):
        return "", False, ""
    done = False
    if "data: [DONE]" in line:
        line = line.split("data: [DONE]", 1)[0].strip()
        done = True
    if line.startswith("data:"):
        line = line[5:].strip()
    if not line:
        return "", done, ""
    try:
        obj = json.loads(line)
    except Exception:
        return "", done, ""
    if not isinstance(obj, dict):
        return "", done, ""
    if obj.get("error"):
        err = obj["error"]
        msg = err.get("message") if isinstance(err, dict) else str(err)
        return "", True, sanitize(msg or "Gateway mengembalikan error", 200)
    choices = obj.get("choices") or []
    if choices and isinstance(choices[0], dict):
        ch = choices[0]
        delta = ch.get("delta") or {}
        text = extract_text(delta.get("content"))
        if not text:
            text = extract_text(ch.get("message", {}).get("content")) \
                if isinstance(ch.get("message"), dict) else ""
        if not text:
            text = extract_text(ch.get("text"))
        if text:
            return text, done, ""
    # beberapa gateway mengirim objek teks langsung
    text = extract_text(obj.get("content")) if obj.get("type") in (None, "text") else ""
    return text, done, ""


class RouterAdapter(PochiBridge):
    name = "9router"
    label = "9Router"

    def __init__(self, cfg, parent=None, key_provider=router_api_key):
        super().__init__(parent)
        self.cfg = cfg
        self._key_provider = key_provider
        self._worker: _RouterWorker | None = None

    def start(self) -> None:
        if self._worker is not None:
            return
        self._worker = _RouterWorker(self.cfg, self._key_provider,
                                     backend_name=self.name, backend_label=self.label)
        w = self._worker
        w.sig_status.connect(self.on_status)
        w.sig_delta.connect(self.on_delta)
        w.sig_message.connect(self.on_message)
        w.sig_error.connect(self.on_error)
        w.sig_connection.connect(self.on_connection)
        w.sig_usage.connect(self.on_usage)
        w.start()

    def send(self, text: str, history: list[dict] | None = None,
             files: list | tuple = ()) -> None:
        if self._worker is None:
            self.start()
        if self._worker is not None:
            self._worker.enqueue(text, history or [], list(files))

    def cancel(self) -> None:
        if self._worker is not None:
            self._worker.cancel()

    def stop(self) -> None:
        w, self._worker = self._worker, None
        if w is not None:
            w.request_stop()
            if not w.wait(4000):
                w.terminate()
                w.wait(1500)


class _RouterWorker(QThread):
    sig_status = pyqtSignal(str)
    sig_delta = pyqtSignal(str)
    sig_message = pyqtSignal(str, str)
    sig_error = pyqtSignal(str)
    sig_connection = pyqtSignal(object)
    sig_usage = pyqtSignal(int, int)

    def __init__(self, cfg, key_provider=router_api_key,
                 backend_name="9router", backend_label="9Router"):
        super().__init__()
        self.cfg = cfg
        self._key_provider = key_provider
        self._backend_name = backend_name
        self._backend_label = backend_label
        self._jobs: queue.Queue = queue.Queue()
        self._stop_evt = threading.Event()
        self._cancel_evt = threading.Event()
        self._conn: http.client.HTTPConnection | None = None
        self._resp = None

    # ------------------------------------------------------------- public
    def enqueue(self, text: str, history: list[dict], files: list) -> None:
        self._jobs.put((text, history, files))

    def cancel(self) -> None:
        self._cancel_evt.set()
        try:
            if self._resp is not None:
                self._resp.close()
        except Exception:
            pass

    def request_stop(self) -> None:
        self._stop_evt.set()
        self.cancel()
        self._jobs.put(None)

    # ---------------------------------------------------------------- run
    def run(self) -> None:
        self._probe()
        while not self._stop_evt.is_set():
            try:
                job = self._jobs.get(timeout=0.12)
            except queue.Empty:
                continue
            if job is None:
                break
            text, history, files = job
            self._cancel_evt.clear()
            try:
                self._run_prompt(text, history, files)
            except Exception as exc:
                if self._cancel_evt.is_set():
                    self.sig_status.emit("idle")
                else:
                    self.sig_error.emit(_friendly_error(exc))
            finally:
                self._close()

    def _probe(self) -> None:
        status = router_status(self.cfg.model_base, timeout=0.7)
        self.sig_connection.emit({
            "backend": self._backend_name,
            "label": self._backend_label,
            "ok": bool(status["ok"]),
            "model": self.cfg.model_name,
            "detail": (f"Lokal · {status['latency_ms']} ms"
                       if status["ok"] else status["detail"]),
            "latency_ms": status["latency_ms"],
        })

    # ------------------------------------------------------------- request
    def _run_prompt(self, text: str, history: list[dict], files: list) -> None:
        self.sig_status.emit("thinking")
        messages = [{"role": "system", "content": SYSTEM_PROMPT}]
        for m in history or []:
            role = m.get("role")
            content = m.get("content")
            if role in ("user", "assistant") and isinstance(content, str) and content.strip():
                messages.append({"role": role, "content": content})
        if files:
            messages.append({"role": "user", "content": _content_parts(text, files)})
        elif not messages or messages[-1].get("content") != text:
            messages.append({"role": "user", "content": text})

        u = urlsplit(self.cfg.model_base if "://" in self.cfg.model_base
                     else "http://" + self.cfg.model_base)
        host = u.hostname or "127.0.0.1"
        port = u.port or (443 if u.scheme == "https" else 80)
        base_path = (u.path or "").rstrip("/")
        path = f"{base_path}/chat/completions"
        key = self._key_provider()
        headers = {
            "Content-Type": "application/json",
            "Accept": "text/event-stream",
            "User-Agent": "pochi-desktop/2.0",
        }
        if key:
            headers["Authorization"] = f"Bearer {key}"
        body = json.dumps({
            "model": self.cfg.model_name,
            "messages": messages,
            "stream": True,
        }).encode("utf-8")

        if u.scheme == "https":
            conn = http.client.HTTPSConnection(
                host, port, timeout=self.cfg.request_timeout,
                context=ssl.create_default_context())
        else:
            conn = http.client.HTTPConnection(
                host, port, timeout=self.cfg.request_timeout)
        self._conn = conn
        conn.request("POST", path, body=body, headers=headers)
        resp = conn.getresponse()
        self._resp = resp
        if resp.status != 200:
            detail = ""
            try:
                detail = sanitize(resp.read(600).decode("utf-8", "replace"), 160)
            except Exception:
                pass
            if resp.status in (401, 403):
                raise RouterError(
                    "9Router menolak kredensial. Periksa key di ~/.hermes/.env "
                    "(tidak ditampilkan di UI).")
            raise RouterError(f"9Router HTTP {resp.status}. {detail}".strip())

        ctype = (resp.getheader("Content-Type") or "").lower()
        if "text/event-stream" not in ctype:
            payload = resp.read().decode("utf-8", "replace")
            self._handle_plain(payload)
            return

        accumulated = ""
        usage_emitted = False
        while not self._stop_evt.is_set():
            if self._cancel_evt.is_set():
                break
            raw = resp.readline()
            if not raw:
                break
            chunk, done, err = parse_sse_line(raw.decode("utf-8", "replace"))
            if err:
                if self._cancel_evt.is_set():
                    break
                if accumulated.strip():
                    self.sig_message.emit(accumulated.strip(), self._backend_name.upper())
                else:
                    self.sig_error.emit(err)
                return
            if chunk:
                accumulated += chunk
                self.sig_delta.emit(chunk)
            if done:
                break

        if self._cancel_evt.is_set():
            self.sig_status.emit("idle")
            return
        text_out = accumulated.strip()
        if not text_out:
            self.sig_error.emit(
                "Model tidak mengembalikan teks (reasoning mungkin menghabiskan token).")
            return
        if self._cancel_evt.is_set():
            self.sig_status.emit("idle")
            return
        self.sig_message.emit(text_out, self._backend_name.upper())
        if not usage_emitted:
            self.sig_usage.emit(max(0, len(text_out) // 4), 0)

    def _handle_plain(self, payload: str) -> None:
        payload = payload.split("data: [DONE]", 1)[0].strip()
        try:
            obj = json.loads(payload)
        except Exception:
            raise RouterError("Respons 9Router bukan JSON yang valid.")
        if obj.get("error"):
            err = obj["error"]
            msg = err.get("message") if isinstance(err, dict) else str(err)
            raise RouterError(sanitize(msg or "9Router error", 200))
        choices = obj.get("choices") or []
        text = ""
        if choices and isinstance(choices[0], dict):
            msg = choices[0].get("message") or {}
            text = extract_text(msg.get("content") if isinstance(msg, dict) else "")
        text = text.strip()
        if not text:
            raise RouterError("Model tidak mengembalikan teks.")
        if self._cancel_evt.is_set():
            self.sig_status.emit("idle")
            return
        self.sig_message.emit(text, self._backend_name.upper())

    def _close(self) -> None:
        try:
            if self._resp is not None:
                self._resp.close()
        except Exception:
            pass
        try:
            if self._conn is not None:
                self._conn.close()
        except Exception:
            pass
        self._resp = None
        self._conn = None


class GatewayAdapter(RouterAdapter):
    """9ochi Gateway (OpenAI-compatible lokal via virtual key).

    Memakai ``POCHI_GATEWAY_KEY`` dari env / ~/.hermes/.env. Gateway sendiri
    meneruskan ke provider (mis. 9Router) sekaligus mencatat usage/biaya dan
    menegakkan anggaran/rate limit per virtual key.
    """

    name = "gateway"
    label = "Pochi Gateway"

    def __init__(self, cfg, parent=None):
        super().__init__(cfg, parent, key_provider=gateway_api_key)


class RouterError(RuntimeError):
    pass


def _friendly_error(exc: Exception) -> str:
    if isinstance(exc, (socket.timeout, TimeoutError)):
        return "9Router timeout. Model terlalu lama merespons; coba lagi."
    if isinstance(exc, ConnectionRefusedError):
        return "9Router tidak berjalan di port lokal. Nyalakan 9Router lalu coba lagi."
    if isinstance(exc, socket.gaierror):
        return "Alamat 9Router tidak valid."
    if isinstance(exc, (http.client.HTTPException, OSError)):
        return f"Koneksi 9Router gagal: {sanitize(str(exc), 160)}"
    return sanitize(str(exc), 220) or "Terjadi kesalahan pada 9Router."
