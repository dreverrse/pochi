# Keputusan Arsitektur (decisions.md)

Setiap keputusan yang mengubah arsitektur dicatat di sini beserta alasannya.

## 2026-10-27 — Awal rombak total sesuai PRD terbaru

Status: disetujui pemilik (Tuan Andre).

### D1. Stack desktop Linux: PyQt6 (Python) — dipilih ulang
- Keputusan awal (sebelum M0): Tauri 2 (Rust + UI web) sesuai rekomendasi PRD.
- Keputusan AKHIR (setelah uji M0, tanggal 2026-10-27): **PyQt6 (Python)** untuk desktop Linux.
- Alasan (hasil ukur M0 di laptop pemilik, X11/XFCE, 3,6 GB RAM 2 core):
  - Tauri 2 + WebKitGTK: total RSS **±419 MB** (main 161 + WebProcess 197 + Network 70), CPU idle ±2–5%. Kriteria M0 PRD (<150 MB, <1% CPU) tidak terpenuhi.
  - PyQt6 (python) di laptop sama: RSS ±160 MB, jauh lebih ringan, sudah berfungsi (notch, drag, maskot, systemd). Dev lebih cepat tanpa toolchain berat.
- Implikasi: PRD tetap menjadi sumber kebenaran fitur/perilaku; hanya implementasi desktop beralih dari web UI ke PyQt6. Struktur monorepo tetap (`desktop/`, `shared/`, `docs/`, `scripts/`).

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
