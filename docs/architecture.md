# Arsitektur Pochi

## Gambaran
Pochi adalah asisten pribadi berbentuk notch/dynamic island yang hidup di tepi atas layar. Dua perangkat: **Android** (sudah ada, `app/`) dan **Linux/Arch** (baru, `desktop/`), saling terhubung langsung via jaringan lokal (peer-to-peer).

## Lapisan (Linux / Tauri 2)
- **UI** (`desktop/src/`, web: TypeScript + Svelte / vanilla) — rendering notch, animasi CSS, halaman: Music, Focus, Water, Shelf, To-do, Devices, Settings.
- **Backend Rust** (`desktop/src-tauri/`) — jendela (frameless, transparan, always-on-top), D-Bus (MPRIS, UPower, NetworkManager, BlueZ), SQLite, jaringan (WebSocket TLS, mDNS), tray.
- **Protokol bersama** (`shared/`) — spesifikasi data, contoh payload JSON, vektor tes; diimplementasikan dua kali (Rust & Kotlin) dan diuji dengan vektor sama.

## Alur data
```
MPRIS/UPower/NetworkManager ─▶ Rust (D-Bus) ─▶ Tauri event ─▶ UI web (notch)
Device lain ── (mDNS discover) ── WebSocket TLS mutual ── sync (todo/water/shelf/timer)
```

## Direktori
```
/app        Android (sudah ada, tidak dipindahkan)
/desktop    Linux Tauri 2
/shared     spesifikasi protokol + vektor tes
/docs       keputusan, audit, protokol, arsitektur, tes manual
/scripts    skrip build & jalankan satu perintah
```
