# Arsitektur Pochi

## Gambaran
Pochi adalah asisten pribadi berbentuk notch/dynamic island yang hidup di tepi atas layar. Dua perangkat: **Android** (sudah ada, `app/`) dan **Linux/Arch** (baru, `desktop/`), saling terhubung langsung via jaringan lokal (peer-to-peer).

## Lapisan (Linux / PyQt6)
- **UI** (`desktop/ui/`, PyQt6) — rendering notch (Dynamic Island), animasi, halaman: Chat, Settings, dashboard, activity (musik/lirik/timer), maskot. Komponen: `island.py`, `activity.py`, `lyrics.py`, `mascot.py`, `chat.py`, `dashboard.py`, `settings.py`, `tray.py`, `components.py`, `icons.py`.
- **Layanan** (`desktop/services/`) — `media.py` (MPRIS), `lyrics.py` + `lyric_service.py` (LRCLIB, LRC, cache SQLite), `fullscreen.py` (sembunyi saat layar penuh), `system.py`, `probes.py`, `bridge.py` (backend AI).
- **Platform** (`desktop/platform_x11/`) — anchor, work area, X11.
- **App** (`desktop/app/`) — konfigurasi, tema, state/controller.
- **Protokol bersama** (`shared/`) — spesifikasi data, contoh payload JSON, vektor tes.

## Alur data
```
MPRIS ─▶ services/media.py ─▶ activity.py (kartu media) ─▶ lyrics (LRCLIB sinkron)
UPower/NetworkManager ─▶ services/probes.py ─▶ dashboard
Layar penuh (X11/EWMH) ─▶ services/fullscreen.py ─▶ sembunyi
```

## Direktori
```
/app        Android (Kotlin, sudah ada)
/desktop    Linux PyQt6 (pengganti Tauri — hasil M0)
/shared     spesifikasi protokol + vektor tes
/docs       keputusan, audit, protokol, arsitektur
/scripts    skrip build & jalankan satu perintah
```
