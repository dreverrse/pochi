# Pochi Android

Pochi adalah aplikasi Android berbasis Kotlin + Jetpack Compose dengan floating Island dan dashboard alat cepat.

## Fitur saat ini
- Dashboard responsif dengan kartu akses cepat.
- Animasi UI lebih tenang (tanpa efek spring memantul berlebihan).
- Kamera: membuka aplikasi kamera bawaan.
- Senter: mengaktifkan/menonaktifkan flash jika perangkat mendukung dan izin kamera diberikan.
- Cuaca: membuka pencarian prakiraan cuaca di browser.
- Musik: mencoba membuka pemutar musik perangkat, dengan fallback ke YouTube Music.
- Catatan lokal: tersimpan otomatis di perangkat.
- Brainstorming: membuat kerangka pertanyaan ide dan menyimpannya ke Catatan.
- Pochi Island: overlay assistant yang sudah ada.
- API key Gemini dapat disimpan dari halaman Pengaturan.

## Build dengan GitHub Actions
1. Upload/commit seluruh isi repositori ke GitHub.
2. Pastikan branch bernama `main` atau `master`, atau jalankan workflow secara manual dari tab **Actions**.
3. Pilih workflow **Build Pochi APK**.
4. Unduh artifact `Pochi-Android-APK` dari hasil workflow yang berhasil.

Workflow memakai JDK 17 dan Gradle 8.7. APK debug bukan rilis Play Store.

## Catatan batasan Android
- Aplikasi pihak ketiga biasa tidak boleh mematikan atau menyalakan daya perangkat secara langsung. Itu memerlukan hak sistem khusus/perangkat yang dikelola.
- Cuaca saat ini membuka hasil pencarian web; belum menggunakan API cuaca langsung.
- Brainstorming saat ini menyimpan kerangka ide lokal. Untuk jawaban AI, gunakan Pochi Island dan konfigurasi Gemini API key.
- Status Island yang tampil di dashboard adalah status sesi UI, bukan pembacaan status sistem yang persisten.
- Sebelum rilis publik, uji izin kamera/senter dan perilaku overlay pada beberapa versi Android serta ukuran layar.
