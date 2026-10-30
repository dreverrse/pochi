# Redesign Android Pochi — Notch AI Agent

Redesign khusus Android, bertahap, berdasarkan referensi pemilik (2026-10-30):

1. **POCHI — EXPRESSION BLUEPRINT**: 15 ekspresi, wajah squircle navy gelap,
   mata putih bercahaya, aksen violet, gelombang suara lavender di sisi wajah.
2. **Kartu dashboard gelap**: kartu hitam membulat, ikon aksen, ring statistik.
3. **Notch ala Mac**: bar hitam menyatu dengan tepi atas — musik di kiri,
   kalender/agenda di kanan, tray di ujung.
4. **Side bar tepi layar HP**: pill vertikal berisi ring progres melingkar.

Build diverifikasi lewat GitHub Actions (`.github/workflows/build.yml`).

## Tahap 1 — Fondasi + wajah blueprint (branch `redesign/android-tahap-1`)

- `PochiDesign.kt` (baru): token desain tunggal (`PochiDesign`) — hitam
  island `#000000`, permukaan kartu `#141416`, garis rambut `#26262B`,
  wajah `FaceBase #14142B` / tepi `FaceRim #4B3FA6`, aksen violet `#7C5CFF`.
- `PochiFace`: 15 ekspresi kanonis blueprint, dipetakan ke renderer mata
  lama (`PochiEmote.toLegacy()`) agar perilaku 36 emote lama tidak rusak.
  `PochiFace.forState(BotState)` memberi ekspresi default per status bot.
- `PochiFaceMascot` + `PochiSoundWaves`: wajah blueprint lengkap dengan
  gelombang suara `(( ))` di kedua sisi.
- `PochiMascot`: latar squircle kini navy blueprint + tepi violet
  (dulu hitam matte `#0A0A0A` + garis `#26262E`).
- `DynamicIslandUI`: palet lokal diarahkan ke `PochiDesign`; titik status
  idle kapsul kini violet; bar atas panel expanded menampilkan wajah
  blueprint mungil yang mengikuti status bot, di samping jam.

Belum diverifikasi build lokal (tidak ada JDK/Gradle di mesin kerja) —
verifikasi dilakukan GitHub Actions setelah branch di-push.

## Tahap 2 — Dashboard kartu (branch yang sama)

- `PochiCards.kt` (baru): `PochiStatCard` gaya referensi dashboard —
  chip ikon lingkaran beraksen, panah lingkaran di kanan atas, nilai
  besar, pill catatan di bawah, dan varian ring progres melingkar
  (`PochiRingProgress`, bahasa visual dari referensi side bar).
- `PochiFace.fromLegacy()`: pemetaan balik emote lama -> wajah blueprint.
- HomeScreen: kartu hero kini memakai gradasi `FaceBase -> Void`,
  tepi `FaceRim`, dan `PochiFaceMascot` lengkap dengan gelombang suara.
  Bagian "Aktivitas Hari Ini" diganti grid 2x2 `PochiStatCard` dengan
  data nyata yang sudah ada: Tugas (ring selesai/total), Air minum
  (ketuk +1 gelas, ring target 2000ml), Fokus (ring target 4 sesi),
  dan Musik (judul + artis dari `PochiSignals.mediaPlayback`).
  `HomeMetricCard` lama tidak lagi dipakai, dipertahankan sementara.

## Tahap 3 — Side bar tepi + koneksi laptop (branch yang sama)

**3a. Side bar tepi layar** (`PochiSideBar.kt`):
- Mode peek/hidden island (island digeser ke tepi sampai hilang)
  kini menampilkan pill hitam vertikal 58dp: wajah Pochi di atas,
  ring Tugas (dari `tasksFlow`), ring Fokus (sesi berjalan / target
  4 sesi), ring Baterai (dibaca dari sticky intent sistem, disembunyikan
  bila tak terbaca), dan ikon roda gigi di dasar — sesuai referensi.
- Jendela peek di `DynamicIslandService.hideAtEdge` diperbesar dari
  64x64 menjadi 60x252 dp agar pill muat; ketukan tetap memulihkan island.

**3b. Koneksi laptop lewat Pochi Gateway**:
- Jalur praktisnya memakai gateway yang sudah ada di sisi desktop
  (HTTP bergaya OpenAI di `127.0.0.1:8000/v1`, model `pochi-lokal`),
  bukan draf envelope WebSocket `docs/protocol.md` (tetap belum dipakai).
- `AiProvider.LAPTOP` baru ("Laptop", model default `pochi-lokal`):
  otomatis muncul di pemilih penyedia Pengaturan dan mengalir ke chat
  lewat `AiClient.ask` yang sama dengan provider cloud.
- Alamat gateway disimpan di `PochiPrefs` (`gatewayUrlFlow`, default
  `http://100.64.0.1:8000/v1` — alamat Tailscale, selaras default MCP
  yang sudah ada); kunci virtualnya memakai mekanisme API key yang sama
  (`KEY_GATEWAY_KEY`).
- Kartu Pengaturan "Koneksi Laptop (Pochi Gateway)": kolom alamat +
  tombol Tes Koneksi (`AiClient.checkGateway`, GET `/models`).
- Keamanan: manifest membuka cleartext agar HTTP LAN bisa dipakai,
  sebagai gantinya `AiClient.requirePrivateGatewayUrl` menolak semua
  alamat gateway non-privat (hanya loopback, 10/8, 172.16/12,
  192.168/16, CGNAT 100.64/10, `*.local`, atau nama host satu label).

## Sisa pekerjaan

- Push branch `redesign/android-tahap-1` dan verifikasi build GitHub
  Actions (belum dilakukan — menunggu keputusan pemilik).
- Layout expanded ala notch Mac (musik kiri, agenda kanan) belum
  dikerjakan; panel expanded saat ini masih tata letak tab lama.
- Kunci gateway masih tersimpan dengan mekanisme yang sama seperti
  API key lama (plaintext SharedPreferences, temuan audit lama) —
  migrasi ke penyimpanan terenkripsi adalah pekerjaan terpisah.
