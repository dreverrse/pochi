# Pochi Desktop (Dynamic Island)

Overlay asisten pribadi Pochi untuk Linux (EndeavourOS / X11). Terinspirasi
Dynamic Island iPhone: saat idle tampil sebagai kapsul hitam ramping di tepi
atas layar, dan melebar jadi panel chat saat diklik. Wajah Pochi (mata putih
di atas persegi matte-hitam) dipakai konsisten di kapsul, dashboard, dan
avatar chat.

Dibangun dengan **PyQt6** (Qt 6, native, ringan). Tidak ada API key di
source; key model hanya dari env (`POCHI_API_KEY`) bila perlu. Font bundel
Poppins ikut disebar di `assets/fonts/`; ikon memakai emoji Noto Color Emoji
yang tersedia di sistem.

## Arsitektur (lapisan)

```
pochi_desktop/
  main.py                    titik masuk (QApplication + wiring)
  app/
    config.py                pengaturan (posisi/ukuran/aksen/gateway)
    state.py                 Controller + event bus + status/messages
    theme.py                 palet & stylesheet terpusat
  integration/
    bridge.py                kontrak PochiBridge (abstract)
    auto_adapter.py          pilih backend otomatis (9Router→Hermes→demo)
    mock_adapter.py          MockAdapter (mode tiruan, tanpa label demo)
    hermes_adapter.py        integrasi Hermes resmi (ACP)
    router_adapter.py        klien OpenAI-compatible 9Router (localhost:20128)
    text.py                  pemecah teks/streaming
  platform_x11/
    x11.py                   helper X11: geometri layar + posisi anchor
  services/
    system.py, health.py, media.py, probes.py   data nyata (/proc, power)
  ui/
    island.py                jendela overlay frameless: kapsul + panel expanded
    mascot.py                wajah Pochi (mata putih, status via bentuk mata)
    chat.py                  panel percakapan + drag & drop file/gambar
    activity.py              area aktivitas kontekstual
    components.py            komponen bersama (IconButton, Dot, Card, dll)
    dashboard.py, settings.py, timer_dialog.py, tray.py, icons.py, fonts.py
```

## Menjalankan

```bash
cd /home/kyle/AI/pochi_desktop
/usr/bin/python3 main.py        # pakai system python (punya PyQt6)
```

Otomatis saat login: unit systemd user.

```bash
systemctl --user enable --now pochi-desktop.service
```

Butuh compositor untuk transparansi (xfwm4 compositing aktif).

## Interaksi

- **Idle**: kapsul hitam murni dengan dua mata Pochi + label status. Titik
 /kapsul bisa **digeser** (posisi tersimpan) dan diklik untuk melebar.
- Klik kapsul → melebar jadi panel chat. `Esc` / alihkan fokus → menutup.
- **Chat**: ketik pesan, atau **seret gambar/file** ke panel (drag & drop) —
  lampiran tampil sebagai chip thumbnail sebelum dikirim. Muka Pochi muncul
  di empty state dan sebagai avatar tiap balasan.
- Status: idle putih, sibuk biru, sukses hijau, error merah.
- Pengaturan (`⋯`): posisi, skala, aksen, pintasan, reduce motion.

## Backend

- `auto` (default): mencoba 9Router → Hermes → fallback tiruan.
- `hermes` / `9router`: integrasi nyata.
- `demo`: mode tiruan (tanpa backend nyata).
- Backend dipilih lewat `--backend` atau di pengaturan.

## Menghentikan

```bash
systemctl --user stop pochi-desktop.service
# atau
pkill -f "pochi_desktop/main.py"
```

## Keamanan

- Tidak ada key di source. Key model hanya dari env (`POCHI_API_KEY`).
- Komunikasi model hanya ke `localhost:20128` (private). Tidak ada port publik.
- Tidak ada eksekusi shell dari teks pengguna.
