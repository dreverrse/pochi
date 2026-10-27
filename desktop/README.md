# Pochi Desktop (Dynamic Island)

Overlay asisten pribadi Pochi untuk Linux (EndeavourOS / X11). Terinspirasi
Dynamic Island iPhone: saat idle tampil sebagai kapsul ungu-hitam ramping di
tepi atas layar. Klik kapsul membuka panel **Ringkas** (musik + agenda), lalu
panel melebar menjadi **Chat** saat kamu mulai bertanya. Maskot Pochi punya
15 ekspresi yang mengikuti status dan interaksi.

Dibangun dengan **PyQt6** (Qt 6, native, ringan). Tidak ada API key di
source: key model dibaca saat runtime dari environment, `~/.hermes/.env`,
atau file 0600 khusus OpenRouter. Font bundel Poppins ikut disebar di
`assets/fonts/`; ikon digambar sendiri dengan QPainter (tanpa dependency
ikon eksternal).

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
    auto_adapter.py          pilih backend otomatis (Hermes→9Router→OpenRouter→demo)
    mock_adapter.py          MockAdapter (mode tiruan, tanpa label demo)
    hermes_adapter.py        integrasi Hermes resmi (ACP)
    router_adapter.py        klien OpenAI-compatible 9Router + OpenRouter
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
- Pengaturan (`⋯`): posisi, skala, aksen, pintasan, reduce motion, backend,
  model, serta API key OpenRouter (disimpan 0600). Saat reduce motion aktif,
  blink, siklus ekspresi, dan animasi panel dimatikan.

## Backend

- `auto` (default): Hermes ACP → 9Router → OpenRouter (hanya bila key sudah
  tersimpan) → fallback tiruan.
- `hermes` / `9router`: integrasi lokal yang sudah ada.
- `openrouter`: cloud OpenAI-compatible (`https://openrouter.ai/api/v1`),
  model default `openrouter/auto` dan bisa diganti di Pengaturan.
- `demo`: mode tiruan (tanpa backend nyata).
- Backend dipilih lewat `--backend` atau di pengaturan.

### OpenRouter

Isi API key di **Pengaturan → Backend & model → OpenRouter API key**. Key
disimpan terpisah dari config di `~/.config/pochi-desktop/openrouter.key`
dengan izin `0600`, dan tidak pernah ditampilkan lagi (hanya 4 karakter
terakhir). Urutan pembacaan key:

1. environment `OPENROUTER_API_KEY` (atau `POCHI_OPENROUTER_KEY`)
2. `OPENROUTER_API_KEY` di `~/.hermes/.env`
3. file `~/.config/pochi-desktop/openrouter.key`

Contoh memakai env saja (tanpa menyimpan file):

```bash
OPENROUTER_API_KEY=sk-or-v1-... /usr/bin/python3 main.py --backend openrouter
```

Model gratis di OpenRouter memakai akhiran `:free` (daftar terbaru di
<https://openrouter.ai/models?max_price=0>); tanpa akhiran itu pemakaian
mengikuti harga model dan kredit akun. Error 402 (kredit habis) dan 401 (key
salah) ditampilkan sebagai pesan yang bisa ditindaklanjuti, bukan data mentah.

## Menghentikan

```bash
systemctl --user stop pochi-desktop.service
# atau
pkill -f "pochi_desktop/main.py"
```

## Keamanan

- Tidak ada key di source. Key model dibaca runtime dari environment,
  `~/.hermes/.env`, atau `openrouter.key` (0600); tidak pernah masuk
  `config.json`, log, atau tampilan UI.
- Komunikasi 9Router hanya ke `localhost:20128` (private). OpenRouter hanya
  dipanggil lewat HTTPS bila backend-nya dipilih/punya key. Tidak ada port publik.
- Tidak ada eksekusi shell dari teks pengguna.
