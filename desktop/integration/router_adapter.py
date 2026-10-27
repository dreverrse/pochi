"""Adapter model langsung (OpenAI-compatible) — 9Router lokal & OpenRouter.

9Router dipakai bila Hermes ACP tidak tersedia; OpenRouter dipakai bila
pengguna memilihnya (atau sudah menyimpan key dan mode auto perlu fallback).
Kredensial dibaca server-side dari environment, ``~/.hermes/.env``, atau file
0600 milik Pochi, dan tidak pernah dikirim ke UI/log. Streaming SSE diparse
secara defensif karena beberapa gateway menambahkan ``data: [DONE]`` di ekor
baris.
"""
from __future__ import annotations

import base64
import http.client
import json
import queue
import socket
import ssl
import threading
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import urlsplit

from PyQt6.QtCore import QThread, pyqtSignal

from integration.bridge import PochiBridge
from integration.text import extract_text, sanitize
from services.probes import (
    OPENROUTER_BASE, openrouter_api_key, router_api_key, router_status,
)

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


@dataclass(frozen=True)
class ProviderProfile:
    """Detail satu penyedia OpenAI-compatible (base, model, key, label)."""

    name: str
    label: str
    tag: str
    base: str
    model: str
    key: str
    where: str          # "Lokal" / "Cloud" untuk teks status
    auth_hint: str      # saran saat 401/403, tanpa membocorkan key


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

    def __init__(self, cfg, parent=None):
        super().__init__(parent)
        self.cfg = cfg
        self._worker: _RouterWorker | None = None

    def profile(self) -> ProviderProfile:
        return ProviderProfile(
            name=self.name, label=self.label, tag="9ROUTER",
            base=self.cfg.model_base, model=self.cfg.model_name,
            key=router_api_key(), where="Lokal",
            auth_hint=("9Router menolak kredensial. Periksa key di "
                       "environment atau ~/.hermes/.env (tidak ditampilkan)."),
        )

    def start(self) -> None:
        if self._worker is not None:
            return
        self._worker = _RouterWorker(self.cfg, self.profile())
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


class OpenRouterAdapter(RouterAdapter):
    """OpenRouter (cloud, OpenAI-compatible) — key dari env/.env/file 0600."""

    name = "openrouter"
    label = "OpenRouter"

    def profile(self) -> ProviderProfile:
        return ProviderProfile(
            name=self.name, label=self.label, tag="OPENROUTER",
            base=OPENROUTER_BASE, model=self.cfg.openrouter_model,
            key=openrouter_api_key(), where="Cloud",
            auth_hint=("OpenRouter menolak API key. Buka Pengaturan → Backend "
                       "& model untuk mengisi/mengganti key."),
        )


class _RouterWorker(QThread):
    sig_status = pyqtSignal(str)
    sig_delta = pyqtSignal(str)
    sig_message = pyqtSignal(str, str)
    sig_error = pyqtSignal(str)
    sig_connection = pyqtSignal(object)
    sig_usage = pyqtSignal(int, int)

    def __init__(self, cfg, profile: ProviderProfile):
        super().__init__()
        self.cfg = cfg
        self.profile = profile
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
                    p = self.profile
                    self.sig_error.emit(
                        _friendly_error(exc, p.label, p.where.lower()))
            finally:
                self._close()

    def _probe(self) -> None:
        p = self.profile
        if p.name == "openrouter" and not p.key:
            self.sig_connection.emit({
                "backend": p.name, "label": p.label, "ok": False,
                "model": p.model,
                "detail": ("API key OpenRouter belum diisi. Buka Pengaturan → "
                           "Backend & model."),
                "latency_ms": 0,
            })
            return
        status = router_status(p.base, timeout=0.7)
        self.sig_connection.emit({
            "backend": p.name,
            "label": p.label,
            "ok": bool(status["ok"]),
            "model": p.model,
            "detail": (f"{p.where} · {status['latency_ms']} ms"
                       if status["ok"] else status["detail"]),
            "latency_ms": status["latency_ms"],
        })

    # ------------------------------------------------------------- request
    def _run_prompt(self, text: str, history: list[dict], files: list) -> None:
        p = self.profile
        if not p.key and p.name == "openrouter":
            self.sig_error.emit(
                "API key OpenRouter belum diisi. Buka Pengaturan → Backend & model.")
            return
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

        u = urlsplit(p.base if "://" in p.base else "http://" + p.base)
        host = u.hostname or "127.0.0.1"
        port = u.port or (443 if u.scheme == "https" else 80)
        base_path = (u.path or "").rstrip("/")
        path = f"{base_path}/chat/completions"
        headers = {
            "Content-Type": "application/json",
            "Accept": "text/event-stream",
            "User-Agent": "pochi-desktop/2.0",
        }
        if p.name == "openrouter":
            headers["X-Title"] = "Pochi Desktop"
        if p.key:
            headers["Authorization"] = f"Bearer {p.key}"
        body = json.dumps({
            "model": p.model,
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
                raise RouterError(p.auth_hint)
            if resp.status == 402 and p.name == "openrouter":
                raise RouterError(
                    "Saldo/kredit OpenRouter habis. Isi kredit atau pilih model "
                    "gratis (akhiran :free) di Pengaturan.")
            raise RouterError(f"{p.label} HTTP {resp.status}. {detail}".strip())

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
                    self.sig_message.emit(accumulated.strip(), p.tag)
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
        self.sig_message.emit(text_out, p.tag)
        if not usage_emitted:
            self.sig_usage.emit(max(0, len(text_out) // 4), 0)

    def _handle_plain(self, payload: str) -> None:
        p = self.profile
        payload = payload.split("data: [DONE]", 1)[0].strip()
        try:
            obj = json.loads(payload)
        except Exception:
            raise RouterError(f"Respons {p.label} bukan JSON yang valid.")
        if obj.get("error"):
            err = obj["error"]
            msg = err.get("message") if isinstance(err, dict) else str(err)
            raise RouterError(sanitize(msg or f"{p.label} error", 200))
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
        self.sig_message.emit(text, p.tag)

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


class RouterError(RuntimeError):
    pass


def _friendly_error(exc: Exception, label: str = "9Router",
                    where: str = "lokal") -> str:
    if isinstance(exc, (socket.timeout, TimeoutError)):
        return f"{label} timeout. Model terlalu lama merespons; coba lagi."
    if isinstance(exc, ConnectionRefusedError):
        if where == "lokal":
            return f"{label} tidak berjalan di port lokal. Nyalakan {label} lalu coba lagi."
        return f"Koneksi {label} ditolak server."
    if isinstance(exc, socket.gaierror):
        return f"Alamat {label} tidak valid atau DNS gagal."
    if isinstance(exc, ssl.SSLError):
        return f"TLS {label} gagal: {sanitize(str(exc), 160)}"
    if isinstance(exc, (http.client.HTTPException, OSError)):
        return f"Koneksi {label} gagal: {sanitize(str(exc), 160)}"
    return sanitize(str(exc), 220) or f"Terjadi kesalahan pada {label}."
