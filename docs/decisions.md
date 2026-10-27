# Keputusan Arsitektur (decisions.md)

Setiap keputusan yang mengubah arsitektur dicatat di sini beserta alasannya.

## 2026-10-27 — Awal rombak total sesuai PRD terbaru

Status: disetujui pemilik (Tuan Andre).

### D1. Stack desktop Linux: Tauri 2 (Rust + UI web)
- Keputusan: Memakai Tauri 2 dengan backend Rust dan UI web (TypeScript/Svelte, atau vanilla bila lebih ringan).
- Alasan: Sesuai rekomendasi PRD; animasi notch mudah dibuat dengan CSS; pemilik kuat di web UI; backend Rust ringan untuk D-Bus & jaringan.
- Catatan lingkungan: X11 + XFCE (xfwm4) di laptop. webkit2gtk-4.1, gtk3, gtk-layer-shell, base-devel sudah terpasang. Rust belum terpasang (dipasang via rustup di level user, tanpa sudo).

### D2. Cakupan fitur Linux v1
- Keputusan: Fitur Agent AI dan Deck TIDAK termasuk di versi Linux 1.0 (tetap ada di Android).
- Alasan: PRD Bagian 13 secara eksplisit menempatkan keduanya di luar cakupan v1.

### D3. Visibilitas repo
- Keputusan: Repo GitHub bersifat privat.
- Alasan: Pilihan pemilik.

### D4. Mirror notifikasi ponsel → laptop
- Keputusan: Mati secara default; hanya aktif bila pengguna menyalakan di Settings.
- Alasan: Privasi dan sesuai PRD (5.6 / 4.10).

### D5. Pemutar musik
- Keputusan: Belum dipilih. Integrasi MPRIS dibangun generik (mendengarkan D-Bus MPRIS, bukan mengikat ke pemutar tertentu).
- Alasan: Pemilik belum menentukan pemutar utama; pendekatan generik tetap valid untuk pemutar mana pun.

### D6. Maskot Pochi
- Keputusan: Ekspresi dan ikon Pochi dipertahankan apa adanya dari repo (tidak digambar ulang, tidak meniru maskot lain). Aset disalin dari repo Android.
- Alasan: Instruksi eksplisit pemilik: "untuk ekspresi atau icon pochi jangan dirubah".

### D7. Struktur monorepo
- Keputusan: Pakai repo pochi yang sudah ada sebagai monorepo. Tambah `desktop/` (Linux), `shared/` (spesifikasi protokol & vektor), `docs/`, `scripts/`. Folder `app/` (Android) tidak dipindahkan.
- Alasan: Sesuai PRD Bagian 3. Jangan pindahkan `app/` tanpa persetujuan.

## Antrian keputusan terbuka
- Pemutar musik final (diperlukan untuk uji M1).
- Batas ukuran file transfer antar perangkat (default PRD 100 MB).
