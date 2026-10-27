# Pochi Desktop (Dynamic Island)

Overlay asisten pribadi Pochi untuk Linux (EndeavourOS / X11). Terinspirasi
Dynamic Island iPhone: saat idle tampil sebagai kapsul ungu-hitam ramping di
tepi atas layar. Klik kapsul membuka panel **Ringkas** (musik + agenda), lalu
panel melebar menjadi **Chat** saat kamu mulai bertanya. Maskot Pochi punya
15 ekspresi yang mengikuti status dan interaksi.

Dibangun dengan **PyQt6** (Qt 6, native, ringan). Tidak ada API key di
source; key model hanya dari env (`POCHI_API_KEY`) bila perlu. Font bundel
Poppins ikut disebar di `assets/fonts/`; ikon digambar sendiri dengan
QPainter (tanpa dependency ikon eksternal).

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
    island.py                jendela overlay frameless: kapsul + Ringkas + Chat
    mascot.py                mesin 15 ekspresi Pochi (QPainter)
    chat.py                  panel percakapan + drag & drop file/gambar
    activity.py              kartu musik + agenda (data nyata)
    components.py            komponen bersama (Pill, SoftCard, Sparkline, dll)
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

- **Idle**: kapsul gelap dengan maskot Pochi + label status. Kapsul bisa
  **digeser** (posisi tersimpan) dan diklik untuk membuka **Ringkas**.
- **Ringkas**: kartu musik (judul, artis, progress, kontrol) dan kartu agenda
  (tanggal, strip hari, timer/task terdekat). Ringkas tidak merebut fokus
  keyboard. Klik bar "Tanya Pochi…" untuk pindah ke **Chat**.
- **Chat**: ketik pesan, atau **seret gambar/file** ke panel (drag & drop) —
  lampiran tampil sebagai chip thumbnail sebelum dikirim. Draft pesan tetap
  ada saat panel ditutup.
- `Esc`: Chat → Ringkas → tutup. Alihkan fokus ke luar panel juga menutup,
  kecuali saat ada permintaan izin tool.
- Status: idle netral, sibuk berpikir, sukses senang, error sedih; setelah
  3 error berturut-turut Pochi kesal, dan 2 error membuatnya menangis.
  Klik maskot = poke, klik kanan = pet, klik dua kali = tertawa. Ekspresi
  interaktif kembali ke status setelah beberapa detik.
- Pengaturan (`⋯`): posisi, skala, aksen, pintasan, reduce motion. Saat
  reduce motion aktif, blink, siklus ekspresi, dan animasi panel dimatikan.

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
