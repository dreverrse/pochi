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

## Tahap 4 — Dashboard notch mewah + responsivitas

Atas permintaan pemilik: desain panel expanded ("dashboard notch")
dirombak total agar lebih mewah dan UI lebih responsif.

- **Kerangka premium**: badan island kini gradasi vertikal
  `Void -> hitam`, tepi gradasi violet (`FaceRim -> Hairline`), dan
  cahaya radial violet lembut di balik konten panel.
- **Header baru**: wajah blueprint bergelombang suara + wordmark
  "POCHI ✦" (aksen emas `AccentGold`) + label status bot; jam memakai
  `PochiClockText`; tombol tutup berupa pil kaca.
- **Dock kaca bawah**: pemilih kartu lama di bar atas dipindah menjadi
  dock pil kaca (`SurfaceGlass`) di dasar panel; tab aktif mendapat
  gradasi violet penuh. Tab kini: Glance, Call, Musik, Aktivitas,
  Cuaca, Timer.
- **Tab Glance (default)** — layout ala notch Mac dari referensi:
  kartu musik mini di kiri (sampul gradasi, judul/artis, kontrol
  prev/play/next yang berfungsi) dan kartu agenda di kanan (hari +
  tanggal, salat berikutnya, sisa tugas, kota).
- Token premium baru di `PochiDesign`: `SurfaceGlass`,
  `SurfaceGlassHigh`, `AccentGold`, `GlowViolet`. Tinggi panel
  expanded naik 224 -> 252 dp.
- **Responsivitas**: jam header dipisah ke composable kecil
  (`PochiClockText`) — sebelumnya state jam di badan `PochiIslandBot`
  membuat tik 1 detik merekomposisi seluruh island; kini hanya teks
  jam. Spring transisi ukuran island disetel ulang
  (damping 0,82) agar terasa lebih mantap dan premium.
- Semua kartu lama (Call, Musik penuh, Aktivitas, Cuaca, Timer,
  persetujuan, tangkapan layar) tidak berubah perilakunya.

## Tahap 5 — Mode mata-saja + ikon mewah (tanpa emoji)

Atas permintaan pemilik: island bisa mengecil sampai hanya mata yang
terlihat, dan emoji di UI diganti ikon vektor.

- **Mode EYES** (`IslandMode.EYES`): tahan kapsul island ±0,5 detik ->
  island mengecil menjadi pil 68x34dp yang hanya berisi mata Pochi
  (tetap hidup: berkedip, melirik, berekspresi). Ketuk pil mata untuk
  kembali ke kapsul normal; ketuk kapsul untuk membuka panel seperti
  biasa. Animasi memakai transisi spring yang sama.
- **Tanpa emoji di island & layar utama**: semua emoji di
  `DynamicIslandUI.kt` dan `MainActivity.kt` diganti ikon Material
  Rounded beraksen warna/emas — WaterDrop, PlayArrow, Pause, Stop,
  Refresh, Mosque, CheckCircle, WbSunny; sublabel ring menjadi teks
  bersih; lencana bahasa menjadi "ID"/"EN" polos.
- Catatan: data contoh koleksi Deck di `PochiPrefs` masih memakai
  emoji sebagai sistem ikon bawaannya (render Deck terpisah), dan
  simbol ✓/✕ di kartu persetujuan adalah glif teks monokrom.

## Tahap 6 — Semirip mungkin dengan Nusa + perbaikan mengecil

Referensi: Nusa (rama2555.github.io/nusa), "living notch" untuk Windows.
Pola perilakunya diterapkan ulang di kode Pochi:

- **Perbaikan mengecil**: keluhan pemilik — mode mata Tahap 5 sulit
  terpicu karena tekan lama baru dinilai saat jari diangkat. Kini
  gestur kapsul dua fase: tekan lama **menyala saat masih ditahan**
  (450 md, sebelum jari diangkat), dibedakan dari ketuk & seret sejak
  awal. Tekan lama pada header panel juga masuk mode EYES.
- **Halaman geser + titik indikator**: panel kini digeser menyamping
  untuk berpindah halaman (urutan Glance, Musik, Aktivitas, Hidup,
  Fokus, Chat), ada bar titik ala Nusa di atas dock (titik aktif
  memanjang violet, bisa diketuk), dock ikon tetap ada seperti
  "page icon bar" Nusa 1.1. Tinggi panel 252 -> 264dp.
- **Siklus mata pendamping**: saat kapsul diam 15 detik (dan tidak ada
  musik), jam/status memudar dan tinggal sepasang mata di tengah;
  setelah 75 detik mata tertidur (emote SLEEPY); mengetuk untuk
  membuka menyapa dengan emote gembira — meniru siklus jam -> mata ->
  tidur -> sapa milik Nusa. Berlaku juga di mode EYES.
- **Intipan peristiwa (peek)**: `DynamicIslandService` mendaftarkan
  penerima siaran daya & Bluetooth; pasang/lepas charger dan earbuds
  tersambung memunculkan kapsul berisi ikon + label + persen baterai
  selama ±3,4 detik lewat `PochiSignals.systemPeek`.
- **Grafik air mini**: `PochiPrefs` kini mencatat log waktu minum
  (`waterLogFlow`, dibersihkan lintas hari); halaman Aktivitas
  menampilkan grafik batang asupan per 3 jam (06–21) dari log nyata.
- Menyusul (Tahap 7): lirik tersinkron pil terbelah & rak file —
  dua fitur Nusa yang paling besar.

## Tahap 7 — Rombak motion & identitas (tetap Compose)

Keputusan pemilik: tetap Kotlin/Compose (prototipe Flutter tidak
jadi dibuat) — kekakuan build sebelumnya adalah soal koreografi
animasi, bukan bahasa. Perombakan gerak di `DynamicIslandUI.kt`:

- **Fisika morf**: spring lebar/tinggi kini punya overshoot saat
  membuka (dampingRatio 0,74) dan tegas saat menutup (0,88); setiap
  ganti mode memicu pulsa squash & stretch (Animatable pegas longgar
  0,42) pada kontainer, dan glow violet ikut berdenyut.
- **Transisi halaman sungguhan**: badan panel dibungkus
  `AnimatedContent` — halaman bergeser menyamping mengikuti arah
  urutan halaman + memudar, dengan `SizeTransform` agar perubahan
  tinggi halaman ikut cair. Titik indikator kini menganimasikan
  lebar & warnanya dengan pegas.
- **Mikro-interaksi**: modifier `pochiPress` (mengecil 0,92 lalu
  memantul pulih) diterapkan pada ikon dock, titik halaman, pil
  tutup, pil mata, tombol Minum/Fokus, dan tombol play besar.
  Ring aktivitas, ring kartu Home (`PochiRingProgress`), dan batang
  grafik air kini menganimasikan nilainya dengan pegas.
- **Mata responsif**: lirikan mengikuti titik sentuhan pada kapsul
  lalu kembali netral ±1,5 detik; equalizer statis di kapsul diganti
  `LiveEqualizer` (4 batang Canvas menari saat musik menyala);
  partikel "z/Z" (`SleepZzz`) melayang saat mata tertidur di kapsul
  diam maupun mode EYES.
- **Identitas**: wordmark header diberi letter-spacing 2sp dan label
  status 0,5sp agar tidak terasa templat bawaan.

## Tahap 8 — Navigasi konsisten, sembunyi otomatis, kartu data mewah

Masukan pemilik: navigasi island tidak konsisten & kurang responsif;
island harus ikut mengecil dan bersembunyi setelah 10 detik tidak
dipakai agar tidak menghalangi layar; grafik/data harus tampil mewah
seperti kartu statistik biru bercahaya pada gambar referensi,
termasuk saat data ditampilkan di chat.

- **Navigasi satu pintu**: fungsi `goToPage()` menjadi satu-satunya
  jalan berpindah halaman. Dock kini memakai urutan `pageOrder` yang
  sama dengan titik indikator & geser (sebelumnya dock berurutan
  berbeda — sumber rasa tidak konsisten). Ikon dock diperbesar
  30 -> 34dp, area sentuh titik diperbesar (tinggi 18dp), ambang
  geser diturunkan 60 -> 44px.
- **Mode MINI + sembunyi otomatis**: mode keempat `IslandMode.MINI`
  (pil 48x22dp, alpha 0,72, hanya mata kecil). Setelah 10 detik tanpa
  interaksi: panel terbuka menutup sendiri, lalu kapsul/mata menyusut
  ke MINI; musik, timer fokus, unduhan, intipan, dan kartu persetujuan
  menahannya. `DynamicIslandService` ikut mengecilkan jendela overlay
  ke 54x28dp (`onMiniChange`, dijaga dari tabrakan `collapseJob`) agar
  benar-benar tidak menghalangi sentuhan ke aplikasi di bawahnya.
  Ketuk MINI untuk bangun; musik mulai/intipan sistem juga
  membangunkannya ke kapsul. Halaman Aktivitas kini bisa digulir dan
  tinggi panel 264 -> 288dp.
- **Kartu data mewah** (`PochiDataCard.kt`): komponen kartu kaca biru
  laut dalam sesuai referensi — gradasi navy elektrik, tepi bercahaya,
  grid samar, kurva Bézier mulus dengan isi gradasi, titik ujung &
  puncak menyala, animasi garis "ditulis" saat tampil, chip periode,
  dan angka pahlawan besar. Dipakai untuk grafik hidrasi (kurva
  kumulatif dari `waterLogFlow`, angka pahlawan = persen target)
  menggantikan grafik batang Tahap 6.
- **Data di chat**: `ChatBubble` mengekstrak angka dari balasan Pochi
  (`extractChatData`); balasan kaya data (<=340 karakter, >=2 angka)
  dirender sebagai `PochiDataCard` dengan angka terbesar sebagai
  pahlawan dan kurva dari angka-angkanya, teks lengkap tetap tampil
  sebagai keterangan di bawah kartu.

## Tahap 12 — semua fungsi bekerja (batch 1: data asli)

Atas arahan pemilik ("sekarang buat semua fungsi bekerja"),
berdasarkan dokumen audit internal repo (`docs/audit-android.md`):

- **File baru `PochiLiveData.kt`**: lapisan data sungguhan.
  Cuaca dari **Open-Meteo** (gratis, tanpa API key; suhu kini,
  kelembapan, angin, kode kondisi WMO -> label ID/EN, suhu per
  jam, maks/min 7 hari, indeks UV) dan jadwal salat dari
  **Aladhan timingsByCity metode 20 (Kemenag RI)** untuk 8 kota
  yang didukung. Hasil di-StateFlow + cache SharedPreferences
  (cuaca basi >30 mnt, salat per hari/kota); gagal jaringan tidak
  fatal — cadangan simulasi lama tetap ada di belakangnya.
- **`PrayerScheduleHelper`** kini mendahulukan jadwal live
  (`nextFromSchedule` diekstrak agar bisa dipakai ulang);
  semua pemanggil lama (Glance, Cuaca, banner pintar) otomatis
  mendapat waktu asli.
- **`WeatherHelper.getSnapshot`** mendahulukan data live —
  banner pintar & kartu cuaca ikut asli.
- **Island**: berlangganan `weatherFlow`/`prayerFlow`, refresh
  otomatis saat kota berubah; `liveTemp` yang sebelumnya macet
  di 32 kini suhu asli; tombol Perbarui Cuaca benar-benar
  mengambil data baru (force refresh + status memutar pulih).
- **Halaman Cuaca**: kurva kartu data = suhu per jam asli,
  footer maks/min harian asli, label kondisi & UV asli, strip
  7 hari = prakiraan maksimum 7 hari ke depan dari hari ini.
- **Widget Budget**: bukan lagi 65% tempelan — ring & persen
  dihitung dari `expensesFlow` terhadap `dailyBudgetRp`, nilai
  menampilkan total pengeluaran terformat Rupiah.

Batch 2 (menyusul): lirik LRCLIB, slider volume, fase Pomodoro
+ persistensi timer, pengingat minum, halaman DECK, status MCP
jujur + klien WebSocket.

### Tambahan arahan pemilik (di atas batch 1)

- **Navigasi panel dihapus**: bar dock ikon dan titik indikator
  halaman dicabut dari panel expanded. Berpindah halaman kini
  murni dengan geser (kiri/kanan) plus pintu kontekstual yang
  sudah ada (ketuk widget Glance, tombol Fokus di Aktivitas,
  tombol Selesai di Fokus, musik yang mulai memutar).
- **Jarak tanggal dilonggarkan**: susunan panel tidak lagi
  `SpaceBetween`; kini tumpukan `spacedBy(14.dp)` dari atas,
  padding vertikal 12dp, dan jarak judul kategori -> tanggal
  3dp — tanggal tidak lagi mepet ke isi di bawahnya.

## Tahap 11 — tanpa logo/nama di navigasi + mata mini lebih besar

Atas arahan pemilik:

- **Header panel tanpa logo dan tanpa nama**: wajah maskot,
  wordmark "POCHI ✦", dan label status "Pochi" di header panel
  expanded dihapus. Header kini menampilkan **kategori aktif**
  (`activeTab.titleId/titleEn`: Sekilas, Aktivitas, Panggilan,
  Cuaca, Musik, Timer, Aksi) dengan subjudul tanggal lengkap
  saat diam, atau label status bot saat bekerja/menunggu.
  Tekan-lama header untuk mode mata tetap berfungsi.
- **Kapsul**: teks status diam "Pochi ✦" diganti jam (HH:mm).
  Bilah navigasi aplikasi (PochiFloatingNavBar) memang sudah
  tanpa logo, dan hero Home adalah konten, bukan navigasi —
  keduanya tidak diubah.
- **Mata saat mengecil diperbesar**: mode MINI maskot 12dp ->
  17dp (island 48x24dp, jendela service 58x30dp), mode EYES
  maskot 21dp -> 25dp.

## Tahap 10 — bentuk notch ala iPhone

Atas gambar referensi keynote notch iPhone (kamera inframerah,
flood illuminator, speaker, kamera depan, dot projector), dengan
pendekatan skill `redesign` (audit dulu, token dulu, perilaku
tidak diubah):

- **Bentuk takik**: sudut atas island dibuat landai
  (0,45x radius), sudut bawah tetap membulat kuat — kapsul dan
  panel terbaca sebagai satu bar perangkat keras, bukan pil
  melayang. Mode EYES/MINI tetap pil simetris.
- **Badan hitam pekat**: gradasi Void dan tepi gradasi violet
  dicabut; kapsul murni hitam tanpa tepi seperti notch asli.
  Panel terbuka memakai tepi garis `WidgetBorder` tipis, glow
  violet diturunkan separuh.
- **Lensa kamera**: `NotchLens` baru — lingkaran konsentris gelap
  dengan pantulan biru kecil di ujung kanan kapsul, tampil saat
  diam; zona kanannya kini dicadangkan (padding status digeser)
  seperti Dynamic Island asli. Musik/intipan/unduhan/fokus
  menyembunyikan lensa.
- **Celah speaker**: slot tipis 38x4dp di tengah atas panel
  terbuka, meniru earpiece pada takik.
- **Posisi tidak diubah**: island tetap di bawah status bar
  (keputusan kode asli agar jam/baterai sistem tidak tertutup)
  dan tetap bisa digeser/dijepit ke tepi seperti sebelumnya.

## Tahap 9 — rombak total: bahasa papan widget hitam

Atas arahan pemilik ("Semuanya rombak total") mengikuti gambar
referensi papan widget hitam pekat (grid kartu hitam bertepi garis
rambut, label mini, angka besar, sparkline/ring mini):

- **Fondasi**: token baru di `PochiDesign` (`WidgetSurface`,
  `WidgetSurfaceHigh`, `WidgetBorder`, `WidgetLabel`, `BoardBlue`,
  `BoardGreen`, `BoardOrange`, `BoardRed`) dan file komponen baru
  `PochiWidgets.kt`: `PochiWidget`, `WidgetLabel`, `WidgetValue`,
  `WidgetFootnote`, `PochiSparkline` (kurva Bézier tipis isi
  gradasi), `WidgetRing` (ring tipis beranimasi), `WidgetTextStack`.
- **Palet island**: permukaan kartu -> `WidgetSurface`, tepi ->
  `WidgetBorder`, dock memakai permukaan widget dengan seleksi
  `BoardBlue` solid, titik indikator biru papan. Halaman Panggilan
  ikut otomatis lewat parameter permukaan.
- **Glance ditulis ulang sebagai grid widget**: widget musik di
  kiri (label, sampul, judul, kontrol berfungsi, equalizer hidup),
  kolom kanan berisi widget kalender dengan strip minggu ini
  (Senin-Minggu, hari ini ter-highlight biru) dan widget agenda
  ringkas (salat berikutnya + sisa tugas).
- **Aktivitas menjadi grid widget 2x2**: Air (nilai ml + ring +
  sparkline kumulatif per ember 3 jam), Tugas (selesai/total +
  ring + tugas berikutnya), Fokus (menit + ring sesi), Budget
  (anggaran harian + ring). Grafik batang per 3 jam dan tombol
  aksi tetap di bawahnya.
- **Cuaca**: widget suhu besar (suhu raksasa + strip suhu
  seminggu) di atas kartu data mewah biru Tahap 8.1.
- **Fokus**: label mini oranye + timer digital 26sp monospace
  putih, jalur progres gradasi oranye->merah.
- **Musik**: halaman dibingkai sebagai satu kartu widget.
- **Aplikasi**: kartu statistik Home memakai permukaan/radius
  papan, hero Home bergaya widget (radius 20, garis widget),
  balon chat asisten memakai `WidgetSurface` bertepi garis widget
  dan biru pengguna disamakan ke `BoardBlue`.

### Koreksi Tahap 8.1 — grafik mewah hanya di cuaca

Atas arahan pemilik ("grafik itu kasih di cuaca aja"): kartu data
mewah kini tampil **hanya** di halaman Hidup/Cuaca sebagai grafik
suhu per jam (angka pahlawan = suhu sekarang dari `liveTemp`,
footer min/maks + UV; halaman Hidup kini bisa digulir). Grafik
hidrasi di Aktivitas dikembalikan ke grafik batang per 3 jam
(Tahap 6), dan render kartu data di chat dicabut — `ChatBubble`
kembali ke balon teks biasa dan `extractChatData` dihapus.

## Sisa pekerjaan

- Push branch dan verifikasi build GitHub Actions untuk Tahap 4
  (Tahap 1-3 sudah terverifikasi: run #14 sukses, artefak
  `pochi-release-apk` 11,5 MB).
- Kunci gateway masih tersimpan dengan mekanisme yang sama seperti
  API key lama (plaintext SharedPreferences, temuan audit lama) —
  migrasi ke penyimpanan terenkripsi adalah pekerjaan terpisah.
