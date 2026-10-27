# Pochi Desktop — Redesign "Glance"

Tanggal: 2026-10-28
Status: menunggu persetujuan pemilik
Cakupan: aplikasi desktop Linux (`desktop/`), belum Android dan belum landing page.

## 1. Intent

Pochi adalah asisten AI pribadi yang hidup sebagai notch/dynamic island di
tepi atas layar Linux. Fitur dan alur yang sudah ada **dipertahankan**;
redesign ini mengganti bahasa visual dan sistem ekspresi maskot agar sesuai
dengan referensi yang diberikan pemilik:

- notch laptop: kartu musik + agenda, tab, gear, latar terang
- logo: squircle hitam dengan rim glow ungu dan mata putih
- blueprint 15 ekspresi
- kartu dashboard gelap dengan angka besar, pill status, dan sparkline

Fokus sekarang: **laptop dulu**. Android menyusul dengan token dan ekspresi
yang sama.

## 2. Kenapa

UI sekarang bersih dan ringan, tetapi masih terasa seperti panel chat generik
dengan mata sederhana. Referensi pemilik mengarah ke karakter yang lebih
hidup: satu identitas ungu, maskot ekspresif, dan panel yang bisa "dilihat
sekilas" sebelum dipakai.

## 3. Goals

1. Bahasa visual baru diterapkan ke seluruh desktop: kapsul, panel glance,
   chat, dashboard, settings, tray.
2. 15 ekspresi blueprint digambar ulang di Qt (`QPainter`), dipetakan ke
   status agen dan interaksi.
3. Panel dibuka dalam mode **Ringkas** (kartu musik + agenda + kolom tanya),
   lalu mekar ke **Chat** saat dipakai.
4. Semua perilaku lama tetap: drag, sembunyi di tepi, expand/collapse,
   streaming chat, lampiran, media/lirik/timer, backend, izin, tray.
5. Tetap ringan: tanpa dependency baru, idle CPU rendah, RSS tidak naik
   berarti dari ±160 MB.
6. Ada unit test + screenshot untuk memverifikasi hasil.

## 4. Non-goals

- Android, APK, GitHub Actions, landing page (dikerjakan setelah desktop).
- Bridge HP–laptop, tool agen baru, atau perubahan backend.
- Ganti toolkit (PyQt6 tetap) atau tulis ulang arsitektur.
- Menyalin kode/aset Nusa.

## 5. Constraints

- EndeavourOS / X11 / XFCE, compositor aktif untuk transparansi.
- RAM 4 GB, storage HDD: jangan tambah runtime berat; build/test lokal
  secukupnya.
- PyQt6 + Poppins yang sudah dibundel; tanpa aset jaringan saat runtime.
- `platform_x11/x11.py` dan logika geometri jendela tetap dipakai.
- Maskot harus terbaca di ukuran 24–96 px, di wallpaper terang maupun gelap.

## 6. Approaches yang dipertimbangkan

- **A. Evolusi UI PyQt6 (dipilih)** — pertahankan service, state, backend,
  dan perilaku; ganti theme, maskot, dan komposisi panel. Risiko kecil,
  bisa dicicil, dan APK/Android tidak terpengaruh.
- **B. Tulis ulang lapisan UI** — modul island baru dari nol. Lebih bersih,
  tetapi lebih lama dan berisiko merusak izin/overlay/media yang sudah jalan.
- **C. UI lintas platform (Compose Multiplatform/Flutter)** — konsisten,
  tetapi berat untuk 4 GB dan overlay X11 rumit. Ditolak.

Pemilik memilih **A** dan mode buka **Glance**.

## 7. Design direction

### Palet

| Token | Nilai | Pakai |
|---|---|---|
| `VOID` | `#05030A` | kapsul, panel |
| `PANEL` | `#0B0812` | badan panel |
| `CARD` | `#17131F` → `#12101A` | gradien kartu |
| `CARD_HI` | `#241B3D` | tab/pill aktif |
| `BORDER` | `rgba(255,255,255,.07)` | hairline |
| `TEXT` | `#F5F3FF` | teks utama |
| `MUTED` | `#9A94AE` | teks sekunder |
| `DIM` | `#6B6380` | caption |
| `VIOLET` | `#7C5CFF` | aksen utama |
| `VIOLET_LIGHT` | `#C4B5FD` | ikon/aksen lembut |
| `CYAN` | `#4CC9F0` | status bekerja |
| `GREEN` | `#3DDC97` | sukses/online |
| `AMBER` | `#FFB547` | menunggu |
| `RED` | `#FF5C7A` | error |

### Bentuk, tipografi, gerak

- Kapsul radius 22; panel radius 28–32; kartu 18–20; pill 999.
- Poppins. Body 10–13 px, judul 18–28 px, weight 500–700.
- Rim 1 px + outer glow ungu lembut; hindari bayangan tebal.
- Animasi 180–260 ms dengan easing `OutCubic`/`OutBack`; hormati
  `reduce_motion`.

## 8. Layout

### 8.1 Kapsul (collapsed) — 236×42 (skala-aware)

- Maskot 28 px (tanpa backing), label `Pochi`, status singkat, indikator
  aktivitas 3 bar.
- Drag bebas; hide-at-edge dan peek tetap.
- Klik → Glance. Hover → rim lebih terang.
- Warna status hanya di dot/bar; teks tetap terang.

### 8.2 Ringkas (Glance) — default saat dibuka — ±560×300

- Header: maskot 26 px (bisa dipoke), segmented tab `Ringkas` / `Chat`
  dengan `Ringkas` aktif, gear, collapse.
- Body 2 kolom:
  - **Music**: cover 48, judul, artis, kontrol prev/play/next/lirik,
    progress + waktu.
  - **Agenda**: bulan + tanggal besar, strip 5 hari, event berikutnya
    (jam, judul, detail).
- Bawah: ask bar `Tanya Pochi…` + tombol kirim.
- Fokus ke ask bar atau mulai mengetik → panel mekar ke Chat.
- Tidak ada media → kartu Agenda melebar / empty state.
- Tidak ada event → tanggal + "Tidak ada agenda".

### 8.3 Chat — ±430×620

- Header: maskot 34, nama, status, segmented tab `Ringkas` / `Chat`
  dengan `Chat` aktif, gear, collapse.
- Empty state: maskot 86, sapaan, 2 chip saran.
- Bubble user ungu transparan; bubble asisten kartu + avatar maskot.
- Composer pill + mic + send; footer model/backend + hint Enter/Esc.
- Tab `Ringkas` kembali ke Glance. `Esc`: Chat → Ringkas → collapsed.

### 8.4 Dashboard, Settings, Tray

- Struktur dan fitur tetap; hanya di-reskin dengan token baru.
- Kartu dashboard memakai gaya kartu baru; sparkline hanya untuk data nyata
  (CPU/RAM/disk), bukan data palsu.

## 9. Ekspresi (15)

| # | Nama | Bentuk | Dipakai saat |
|---|---|---|---|
| 1 | Playful (wink) | pil + caret | sapa, poke |
| 2 | Angry | garis miring + glow merah | error/blocked |
| 3 | Neutral (idle) | dua pil | default |
| 4 | Happy | dua caret | sukses |
| 5 | Excited | oval besar + sparkle | kabar baik |
| 6 | Laughing | `> <` + mulut | bercanda |
| 7 | Sad | mata turun + tint biru | gagal |
| 8 | Crying | mata + air mata | sedih/error |
| 9 | Annoyed | kelopak datar + mulut garis | error berulang |
| 10 | Surprised | mata bulat + mulut kecil | mendengarkan/info baru |
| 11 | Confused | satu besar satu kecil + `?` | input ambigu |
| 12 | Shy | caret tertutup + blush | pujian/pet |
| 13 | Sleepy | kelopak datar + `zZ` | idle lama |
| 14 | Thinking | mata ke atas + titik | menunggu/berpikir |
| 15 | Love | mata hati + hati | sayang/sukses |

Perilaku:

- Referensi bentuk: `/home/kyle/Proyek/Pochi/pochi image/ekspresion.jpg`.
  Mockup strip hanyalah arah kasar; tabel di atas yang jadi spesifikasi.
- Ekspresi dasar dari `Status`; `success` → happy/excited, `error` →
  sad/angry, `thinking` → thinking.
- Idle berganti tiap 20–40 dtk di antara neutral/wink/confused/thinking;
  setelah 90 dtk idle → sleepy.
- Poke/klik maskot: wink → happy → surprised → shy; long-press/pet → love.
- Blink tiap 2,2–4 dtk, kecuali ekspresi mata tertutup/hati.
- `reduce_motion`: tanpa blink, tanpa siklus idle, tanpa bounce.
- Ukuran: 24 (kapsul), 26 (header Ringkas), 34 (header Chat), 86 (empty
  state), 44 (dashboard).

Semua 15 ekspresi harus bisa digambar dan diuji. Yang dipicu state nyata:
neutral, happy, excited, thinking, surprised, sad, annoyed, angry, sleepy.
Sisanya dipicu interaksi/idle: wink, laughing, crying, confused, shy, love.
Tidak ada heuristik teks baru di scope ini.

## 10. Peta implementasi

| File | Perubahan |
|---|---|
| `desktop/app/theme.py` | token palet baru, QSS kartu/pill/tab, helper glow |
| `desktop/ui/mascot.py` | 15 ekspresi QPainter, blink/idle/poke, mapping status |
| `desktop/ui/island.py` | ukuran per mode, Glance + Chat, tab, ask bar, animasi |
| `desktop/ui/activity.py` | kartu musik/lirik/timer gaya baru |
| `desktop/ui/chat.py` | bubble, empty state, avatar |
| `desktop/ui/components.py` | `Card`, `Pill`, `Sparkline`, cover, ikon |
| `desktop/ui/dashboard.py` | gaya kartu + sparkline data nyata |
| `desktop/ui/settings.py`, `tray.py`, `icons.py` | palet/ikon |
| `desktop/tests/test_mascot.py` (baru) | mapping + siklus idle + reduce motion |
| `desktop/tests/smoke.py` | screenshot Glance/Chat/Tray |

Tidak diubah: `services/`, `integration/`, `app/state.py`,
`platform_x11/`, `main.py` (kecuali wiring kecil bila perlu).

## 11. State & transisi

```
collapsed --klik--> ringkas --klik ask bar--> chat
    ^                  |  ^                       |
    |                  |  +------ tab Ringkas -----+
    +---- Esc ---------+<--------- Esc ------------+
```

- Buka selalu ke Ringkas.
- `Esc` dari Chat → Ringkas; `Esc` dari Ringkas → collapsed.
- Kehilangan fokus → collapse (perilaku sekarang), kecuali ada draft chat
  yang belum dikirim (draft disimpan, tidak dibuang).
- Ringkas **tidak** mengambil fokus keyboard saat dibuka. Klik ask bar
  memfokuskan input dan mekar ke Chat. Tidak ada global key hook.
- Ukuran: Ringkas 560×300; Chat 430×620; kapsul 236×42; semua dikali `scale`.
- Posisi kustom tetap; panel harus tetap di dalam work area.

## 12. Accessibility

- Fokus keyboard terlihat di input/tombol.
- `Enter` kirim, `Esc` tutup, tooltip tombol.
- Kontras teks body ≥ 4,5:1.
- Target sentuh/klik ≥ 28 px.
- `reduce_motion` dihormati di maskot dan transisi panel.

## 13. Performance

- Tanpa dependency baru; semua bentuk digambar QPainter.
- Timer blink 50 ms hanya saat widget terlihat; idle tetap ringan.
- Tidak ada polling baru; data media/sistem memakai service yang ada.
- Target: RSS idle ±160 MB, CPU idle ~0%.

## 14. Verifikasi

- Unit: mapping status→ekspresi, siklus idle, poke, reduce motion.
- Smoke X11: screenshot collapsed, glance (ada/tanpa media), chat, tray,
  error; cek drag, edge hide, fullscreen hide.
- Manual: streaming chat, lampiran, kontrol media, lirik, timer, settings,
  tray, autostart.
- Screenshot disimpan di `desktop/tests/shots/` dan dibandingkan dengan
  `docs/mockups/desktop-redesign-v1.png`.

## 15. Rollout

1. Token + mesin ekspresi + screenshot maskot.
2. Glance panel + ask bar + tab.
3. Chat restyle.
4. Dashboard/settings/tray.
5. Test, screenshot, dokumentasi.
6. Commit per milestone; tidak menyentuh Android.

## 16. Keputusan yang diserahkan ke builder

- Jarak/ukuran presisi tiap elemen.
- Apakah Ringkas perlu animasi tinggi saat mekar ke Chat atau crossfade.
- Cara menyimpan draft chat saat panel collapse.
- Apakah agenda mengambil data kalender nyata sekarang atau menampilkan
  event dari state yang ada (tidak ada integrasi kalender baru di scope ini).
- Nama file test dan organisasi helper gambar maskot.

## 17. Klarifikasi setelah builder check

Builder check menemukan tujuh titik abu-abu. Semua diselesaikan di sini;
tidak ada yang perlu ditanyakan lagi ke pemilik.

1. **D6 vs ekspresi baru.** `docs/decisions.md` D6 melarang mengubah
   ekspresi/ikon. Instruksi pemilik 2026-10-28 ("ubah ekspresi dan desain
   UI") menggantikannya. Dicatat sebagai D8; D6 ditandai superseded.
2. **Arti "Tray".** Tab di mockup bernama `Ringkas`, bukan system tray.
   `tray.py` tetap ikon tray sistem. Ringkas = halaman kartu; Chat = percakapan.
3. **Izin tool.** Permintaan izin **membuka Chat otomatis** dan menampilkan
   banner, tanpa auto-collapse selama izin pending. Ini alur blocking dan
   tidak boleh tersembunyi di Ringkas.
4. **Fokus keyboard.** Ringkas tidak mengambil fokus saat dibuka. Klik ask
   bar → fokus + mekar ke Chat. Tidak ada global key hook, jadi "mulai
   mengetik tanpa klik" tidak didukung. `Esc` dari Chat → Ringkas; `Esc`
   dari Ringkas → collapsed.
5. **Timer tidur.** 90 dtk idle → ekspresi sleepy (maskot). Auto-collapse
   panel tetap 30 dtk (`auto_collapse_seconds`). Status aktif
   (thinking/working) mengalahkan idle/sleepy.
6. **Poke.** Maskot hadir di header Ringkas (26 px), header Chat (34 px),
   empty state (86 px), dan dashboard (44 px). Kapsul collapsed hanya
   menampilkan ekspresi status. Poke/pet dilakukan di maskot yang terlihat.
7. **Ekspresi yang reachable.** Lihat §9: 9 ekspresi dipicu status nyata,
   6 dipicu interaksi/idle. Tidak ada analisis teks baru.

Keputusan tambahan:

- Draft chat disimpan saat collapse; tidak dikirim otomatis.
- Agenda memakai event dari state yang ada. Tidak ada integrasi kalender
  baru; bila kosong tampilkan tanggal + "Tidak ada agenda".
- Mekar Ringkas → Chat memakai animasi tinggi + crossfade 200 ms; dengan
  `reduce_motion`, crossfade tanpa animasi tinggi.
- Tidak ada dependency baru.
