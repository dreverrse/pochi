# Audit Repo Android — Pochi

**Tanggal audit:** 2026-10-27
**Lokasi:** `app/` (monorepo `pochi/`)
**Modul:** `com.pochi.assistant` (17 file Kotlin)
**UI:** 100% Jetpack Compose (Material 3)
**Versi:** v1.0.0 (versionCode 1, targetSdk 34, minSdk 26)

---

## 1. Ringkasan Eksekutif

- Aplikasi Android **berjalan sebagai overlay Dynamic Island** (foreground service `specialUse`) + AI Agent lokal/generatif + pelacak produktivitas (fokus, hidrasi, tugas, pengeluaran, jadwal sholat/cuaca/hardm).
- Model data **lengkap** (SQLite non-Room + SharedPreferences), namun **banyak fitur PRD yang baru ada di lapisan data/UI parsial, belum lengkap tuntas**.
- Temuan penting:
  - **Bukan Room**: `PochiDb.kt` memakai `SQLiteOpenHelper` mentah (SQL string), tanpa migration (`onUpgrade` kosong).
  - **Penyimpanan ganda**: tugas/pengeluaran/deck disimpan **dua kali** — di SharedPreferences (JSON) dan di tabel SQLite, dengan `PochiPrefs` sebagai sumber utama UI. Risiko divergensi data.
  - **Deck & Today/tugas**: lapisan data + parser ada, tetapi **UI manajemen lengkap belum muncul** di navigasi v2 (4 tab: Beranda/Pochi/Memori/Pengaturan).
  - **4.3 lirik, 4.8 volume, 4.9 baterai: belum ada sama sekali**.
  - **4.5 pengingat minum**: pelacakan ada, mekanisme pengingat/notifikasi belum ada.
  - **4.4 Focus timer**: berjalan, tapi phase Pomodoro (break 5m/15m) tidak pernah digunakan; timer hanya in-memory.
  - Tidak ada **OkHttp** (pakai `HttpURLConnection`) dan **tidak ada NsdManager**.
  - Status koneksi MCP/Agent (`AgentSessionState`) masih **state placeholder/stub**, belum ada klien WebSocket sungguhan.

---

## 2. Struktur Proyek

```
app/
├── build.gradle.kts
├── src/main/
│   ├── AndroidManifest.xml
│   ├── kotlin/com/pochi/assistant/
│   │   ├── PochiApp.kt                      (Application: crash handler)
│   │   ├── MainActivity.kt                   (aktivitas utama + seluruh UI Compose v2)
│   │   ├── BiometricGateActivity.kt          (gate biometrik utk aksi berisiko)
│   │   ├── DynamicIslandService.kt           (FGS overlay Dynamic Island)
│   │   ├── PochiAccessibilityService.kt      (baca konteks layar)
│   │   ├── PochiNotificationService.kt       (listener notifikasi + media)
│   │   ├── DynamicIslandUI.kt                (PochiIslandBot, activity rings)
│   │   ├── PochiMascot.kt                   (36 ekspresi, BotState)
│   │   ├── PochiAgent.kt                    (parser aksi lokal + eksekusi)
│   │   ├── AiClient.kt                      (4 provider AI)
│   │   ├── ApprovalManager.kt               (antrian persetujuan)
│   │   ├── ApprovalCardUI.kt                (kartu persetujuan Compose)
│   │   ├── PochiPrefs.kt                    (SharedPreferences + parser bahasa alam)
│   │   ├── PochiDb.kt                       (SQLiteOpenHelper non-Room)
│   │   ├── PochiSignals.kt                  (StateFlow global: layar/media/download/dll)
│   │   ├── PochiContacts.kt                 (resolusi nama→nomor)
│   │   └── UpdateChecker.kt                 (update APK dari GitHub Releases)
│   └── res/
│       ├── drawable/ic_pochi_launcher.xml
│       ├── drawable/ic_pochi_notification.xml
│       ├── values/strings.xml, styles.xml
│       └── xml/file_paths.xml, pochi_accessibility_service.xml
```

---

## 3. Aktivitas, Service & Receiver Utama

| Komponen | Tipe | Status | Catatan |
| :--- | :--- | :--- | :--- |
| `MainActivity` | Aktivitas (exported, LAUNCHER) | ✅ | Satu-satunya aktivitas yang di-export. `enableEdgeToEdge`, memuat `PochiV2App()`. |
| `BiometricGateActivity` | Aktivitas (tidak exported, translucent, excludeFromRecents) | ✅ | Gate `BiometricPrompt` untuk aksi berisiko. `FLAG_SECURE` mencegah screenshot. |
| `DynamicIslandService` | **Foreground Service** (`specialUse: floating_assistant_overlay`) | ✅ | Overlay `TYPE_APPLICATION_OVERLAY` + `ComposeView`. `START_STICKY`. Memakai `PochiPrefs.*` default |
| `PochiAccessibilityService` | Service (BIND_ACCESSIBILITY_SERVICE) | ✅ | Hanya aktif bila `screenAwareness` diaktifkan. Membaca teks layar. |
| `PochiNotificationService` | Service (BIND_NOTIFICATION_LISTENER_SERVICE) | ✅ | Mirror notifikasi + deteksi media & unduhan. |
| — Receiver — | — | ❌ | **Tidak ada BroadcastReceiver sama sekali** di manifest/kode (tidak ada alarm/reminder scheduling). |

Catatan penting:
- Perizinan `FOREGROUND_SERVICE_SPECIAL_USE` + `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` sudah dipasang dengan benar (API 34+).
- `PochiNotificationService` juga memakai `MediaSessionManager.getActiveSessions()` untuk membaca metadata pemutar aktif.

---

## 4. Dynamic Island — Tiga Ukuran & Struktur UI

File: `DynamicIslandUI.kt` (`PochiIslandBot`), dihost oleh `DynamicIslandService`.

### 4.1 Tiga ukuran (mode)
1. **Collapsed (kapsul)** — `196×36dp` (atau `216×36dp` saat media aktif). Konten: maskot + status (musik / timer fokus / unduhan / "Pochi ✦").
2. **Expanded (kartu Live Activity)** — lebar `336–380dp` (portrait) / `320–520dp` (landscape), tinggi `224dp` (UI) / hingga `230–440dp` (service) disesuaikan responsif. Berisi 5 "kartu" + bar switcher atas + jam + tutup.
3. **Peek/Hidden (tepi)** — saat digeser ke tepi layar: `64×64dp`, hanya maskot `36dp`, bisa di-tap untuk memunculkan.

### 4.2 Kartu yang tersedia (expanded)
| Multi | Tab (`PochiTab`) | Konten |
| :--- | :--- | :--- |
| Musik | `WALLET` | Now-playing: sampul album, judul/artis, slider progres, tombol shuffle/prev/play-pause/next/repeat (via `dispatchMediaKeyEvent`) |
| Panggilan | `CHAT` | Panggilan suara + tombol Mic / "Tanya AI" / Tutup; avatar maskot; teks parsial ucapan |
| Aktivitas | `TODAY` | 4 cincin (Air/Tugas/Fokus/Budget) + tombol "+ Minum 250ml" & "▶ Fokus 25m" |
| Cuaca | `LIFE` | Cuaca & UV, equalizer bar pelangi, jadwal sholat, tombol refresh |
| Fokus (default) | (else) | Timer countdown + bar progres + tombol Jeda/Lanjut, Selesai, +5 Menit |

**Temuan:**
- Enum `PochiTab` **mendefinisikan `FOCUS` dan `DECK`**, tetapi hanya 4 tab (`CHAT`, `WALLET`, `TODAY`, `LIFE`) yang muncul di switcher; `FOCUS` dirender lewat cabang `else` (default); **`DECK` tidak pernah dirender**.
- Tinggi kartu expanded di UI (`224dp`) **tidak sinkron** dengan default service (`230`/`440dp`), nilai duplikat di dua tempat.

---

## 5. Maskot — PochiMascot.kt

- **36 ekspresi** (`PochiEmote`): NORMAL, HAPPY, LAUGHING, SMILING, WINK_LEFT, EXCITED, LOVE, BLUSH, CUTE, SURPRISED, SHOCK, CONFUSED, THINKING, HMM, SUSPICIOUS, ANGRY, ANNOYED, TIRED, SLEEPY, CALM, HAPPY_TEARS, CRYING, SCARED, EMBARRASSED, OK, NO, BYE, HI, GOOD_NIGHT, GOOD_MORNING, WORKING, SUCCESS, ERROR, LOADING, FOCUS, PET_POKE.
- **`BotState`** (6): IDLE, WAITING, WORKING, DONE, APPROVAL, ANNOYED — masing-masing punya warna aksen, label, dan emote default.
- **Rendering**: `Canvas` — mata putih menyala di atas **squircle hitam matte** `#0A0A0A` (bukan lingkaran). Jaringan mata digambar per ekspresi (pill, caret `^`, chevron `>`, dash `-`, ring `O`, bintang `✦`, hati `♥`, silang `X`, tetesan air mata).
- **Animasi**: napas 120fps, berkedip periodik (termasuk double-blink), drift pandangan, pelacakan arah sentuh/gesek, squash-and-stretch + hop (spring) saat perubahan ekspresi, tilt mata.
- **Interaksi**: tap (poke), long-press (pet), drag (gaze tracking). Reaksi poke berjenjang (`PochiMood.onPokeReaction`), reaksi pet (`onPetReaction`).
- **`PochiMood`**: mood otonom berdasarkan jam & idle-time, inferensi emote dari teks ([EMOTE:...] / kata kunci).

---

## 6. Penyimpanan

### 6.1 `PochiDb.kt` — SQLite (bukan Room)
- `SQLiteOpenHelper` mentah, database `pochi_local.db`, versi **1**.
- Tabel: `tasks`, `deck_items`, `expenses`, `chat_messages`, `focus_logs`, `memories`.
- `onUpgrade()` **kosong** — tidak ada migrasi skema.
- Ops: CRUD lengkap utk semua entitas; `getTotalFocusMinutesToday()`, `exportBackupJson()`, `resetDatabase()`, `getMemoriesPrompt()`.
- **Bug potensial**: `saveTask()` memakai `insertWithOnConflict(..., CONFLICT_REPLACE)` — mengganti seluruh baris termasuk `created_at`; subtasks disimpan sebagai JSON string dalam kolom.

### 6.2 `PochiPrefs.kt` — SharedPreferences (sumber utama UI)
- Menyimpan **JSON-serialized** untuk `tasks`, `expenses`, `deck_items` (dibaca/ ditulis via `StateFlow`).
- **Duplikasi data** dengan tabel SQLite (di atas): UI memakai Prefs; `focus_logs` ditulis ke DB via `recordFocusSession()`.
- Menyimpan: API key 4 provider, kota, offset island, mode gelap, bahasa (ID/EN), nama pengguna, budget harian, gelas air, sesi fokus, notes, pinned apps, endpoint MCP.

### 6.3 Parser bahasa alam (dalam `PochiPrefs.kt`)
- `NaturalTaskParser`: memecah multi-klausa (EN/ID), mendeteksi waktu relatif ("in 30m", "30 menit lagi") dan jam ("2pm", "jam 2 siang"), prioritas ("!", urgent/penting).
- `NaturalExpenseParser`: parsing nominal Rupiah (rb/ribu/jt/juta) + kategori otomatis (Makan/Jajan/Transport/Tagihan/Belanjaa).
- `PrayerScheduleHelper` & `WeatherHelper`: jadwal sholat (offset per kota) & snapshot cuaca — **disimulasikan secara lokal**, tidak ada panggilan API cuaca sungguhan.

---

## 7. Jaringan & Penemuan Perangkat

- **OkHttp**: ❌ **tidak digunakan**. Semua HTTP memakai `HttpURLConnection` mentah (`AiClient` untuk 3 provider OpenAI-compatible; `UpdateChecker` untuk GitHub).
- **NsdManager**: ❌ **tidak digunakan** (tidak ada discovery perangkat/peer).
- **Gemini**: memakai SDK `com.google.ai.client.generativeai` (generativeai 0.9.0).
- **MCP/WebSocket**: endpoint `ws://100.64.0.1:8765/mcp` dikonfigurasi di Preferences, tetapi **tidak ada implementasi klien WebSocket**; `AgentSessionState` hanya state/stub default (log "✓ Connected to MCP session" adalah string statis).
- `usesCleartextTraffic="false"` — jaringan non-TLS diblokir (konsisten dgn HTTPS-only).

---

## 8. Izin yang Diminta (Manifest)

| Izin | Gunaan |
| :--- | :--- |
| `INTERNET` | AI, update, jaringan |
| `SYSTEM_ALERT_WINDOW` | Overlay Dynamic Island |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` | FGS overlay |
| `POST_NOTIFICATIONS` | Notifikasi FGS/status |
| `READ_CONTACTS` | Resolusi nama→nomor (`PochiContacts`) |
| `RECORD_AUDIO` | `SpeechRecognizer` (suara, tidak dipersistenkan) |
| `ACCESS_COARSE_LOCATION` / `ACCESS_FINE_LOCATION` | Cuaca/lokasi (dideklarasikan; implementasi cuaca lokal) |
| `com.android.alarm.permission.SET_ALARM` | Alarm via `AlarmClock` |

`<queries>`: MAIN/LAUNCHER, VIEW https, SENDTO smsto, DIAL tel, SEND text/plain, `RecognitionService`.

---

## 9. Catatan Build (Gradle)

**Root (`build.gradle.kts`):** AGP `8.4.0`, Kotlin `1.9.24`.
**Modul `app/build.gradle.kts`:**
- `namespace`/`applicationId` = `com.pochi.assistant`; `compileSdk=34`, `targetSdk=34`, `minSdk=26`.
- `versionCode=1`, `versionName="1.0.0"`.
- Compose: `buildFeatures.compose=true`, `composeOptions.kotlinCompilerExtensionVersion="1.5.14"`, Java 17 / jvmTarget 17.
- **Signing release**: membaca env `POCHI_KEYSTORE_*` (`POCHI_KEYSTORE_FILE/PASSWORD/ALIAS/KEY_PASSWORD`); jika tidak ada, **fallback ke signing debug**.
- Lint: `abortOnError=false`, `checkReleaseBuilds=false`.

**Dependensi utama:** Compose UI/Foundation 1.6.8, Material3 1.2.1, material-icons-extended 1.6.8, activity-compose 1.9.0, lifecycle 2.8.2, savedstate 1.2.1, biometric 1.1.0, appcompat 1.7.0, generativeai 0.9.0.
- **Statistik:** tidak ada Room, tidak ada OkHttp, tidak ada Coil/Glide (sampul album digambar via gradien), tidak ada Hilt/Koin (DI manual `object`/singleton).

---

## 10. Pemetaan Fitur PRD

| Fitur PRD | Status | Bukti di kode |
| :--- | :--- | :--- |
| **4.1 Notch/status + maskot** | ✅ **Ada (mayoritas)** | `DynamicIslandService` + `PochiIslandBot` (3 ukuran), `PochiMascot` (36 ekspresi). *Catatan:* dukungan notch/cutout via `LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS`. |
| **4.2 Musik** | ⚠️ **Parsial** | Deteksi now-playing via `PochiNotificationService`/`MediaSessionManager`; kontrol tombol via `dispatchMediaKeyEvent` (hardware key). **Belum ada** per-app volume, daftar lagu, atau pemutar sendiri. |
| **4.3 Lirik sinkron** | ❌ **Belum ada** | Tidak ditemukan file/fungsi lirik. |
| **4.4 Timer fokus (Focus)** | ⚠️ **Parsial** | Timer countdown di kartu island + `recordFocusSession()` → `focus_logs` + hitung menit hari ini. **Kurang:** phase Pomodoro (`PomodoroPhase`) tidak dipakai (break 5m/15m tidak ada); `focusTotalSeconds` hardcoded 25m; state timer in-memory (hilang saat service restart); tidak ada notifikasi/reminder saat selesai (hanya haptic + emote). |
| **4.5 Pengingat minum** | ⚠️ **Parsial** | Pelacakan gelas (target 8) + cincin Air + tombol "+250ml". **Kurang:** tidak ada mekanisme pengingat/notifikasi berkala. |
| **4.6 Rak file (Deck/Today)** | ⚠️ **Parsial** | Model `DeckCollectionItem` + `deck_items` (DB/Preƒ) + `addDeckCollection`. **Kurang:** tidak ada layar Deck di UI; `PochiTab.DECK` didefinisikan tapi tak pernah dirender. |
| **4.7 To-do / Today** | ⚠️ **Parsial** | Model `TodayTask` lengkap (subtask, catatan, prioritas, overdue), `NaturalTaskParser`, CRUD. **Kurang:** UI manajemen tugas utuh belum muncul di navigasi v2 (Beranda hanya menampilkan ringkasan metrik). |
| **4.8 Volume** | ❌ **Belum ada** | Tidak ada fitur kendali volume (`AudioManager` hanya utk media key). |
| **4.9 Baterai** | ❌ **Belum ada** | Tidak ada pemantauan/penampil baterai. |

---

## 11. Isu & Temuan Terkait

1. **Data duplikat**: tugas/pengeluaran/deck di SharedPreferences **dan** SQLite; tanpa sinkronisasi jelas → risiko tidak konsisten.
2. **MCP/WebSocket cuma stub**: `AgentSessionState` menampilkan status "terhubung" palsu; belum ada klien sungguhan.
3. **Migrasi DB kosong**: `onUpgrade` tidak menangani perubahan skema (versi 1).
4. **Cuaca/lokasi disimulasikan**: bukan API panggilan sungguhan meski izin lokasi diminta; README mengklaim GPS/IP-fallback.
5. **Hardcoded teks UI**: banyak string bilingual ditulis inline (bukan `strings.xml`); hanya 2 string di resource.
6. **API key disimpan plaintext** di SharedPreferences (README mengklaim "encrypted", faktanya `putString` polos).
7. **Feature creep UI**: island sudah memuat banyak kartu haticuaca/sholat/budget) di luar 4 pilar PRD — berpotensi membebani `DynamicIslandService`.
8. **Update APK**: `UpdateChecker` memakai GitHub Releases + FileProvider; perlu izin instalasi eksternal.
9. **Kontrol media** via `dispatchMediaKeyEvent` tidak universal (tergantung pemutar).
