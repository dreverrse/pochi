# Pochi Bridge — jembatan agen coding ala Coucou (Tahap 13)

Menghubungkan agen coding di laptop (Claude Code) dengan Pochi di
Android: sesi agen tampil langsung di dynamic island, dan
permintaan izin agen bisa disetujui/ditolak dari HP.

Terinspirasi oleh [Coucou](https://github.com/Louis-CFM/coucou)
(MIT) — diimplementasikan ulang di kode Pochi.

## Cara pakai

1. **Jalankan jembatan di laptop** (Python 3, tanpa dependensi):

   ```bash
   cd desktop/bridge
   python3 pochi_bridge.py            # port 8788
   ```

2. **Pasang hook Claude Code** — gabungkan blok `hooks` dari
   contoh di kepala `pochi_hook.py` ke `~/.claude/settings.json`,
   sesuaikan path menuju `pochi_hook.py` di repo ini.

3. **Samakan jaringan** — HP dan laptop harus bisa saling
   menjangkau. Pochi menurunkan alamat jembatan dari host
   *Pochi Gateway* yang sudah diatur di Pengaturan
   (default `http://100.64.0.1:8000/v1` → jembatan
   `http://100.64.0.1:8788`). Bila memakai Tailscale, pakai IP
   Tailscale laptop sebagai host gateway.

4. **Mulai Claude Code** seperti biasa. Di HP:
   - Kapsul island menampilkan ticker sesi yang sedang bekerja.
   - Halaman **Agen** (halaman pertama panel) menampilkan semua
     sesi + status jembatan.
   - Saat agen meminta izin, panel island membuka sendiri dengan
     kartu **Tolak / Selalu / Izinkan**. Keputusan diteruskan ke
     agen yang menunggu. Tanpa keputusan selama ~110 detik, agen
     kembali ke prompt izin biasa di terminal.

## Batasan saat ini

- Satu permintaan izin menunggu pada satu waktu.
- Hitungan baris +N/−M terisi 0 dari hook standar Claude Code
  (payload hook tidak membawa statistik diff); kolomnya siap
  bila sumber data menyediakan.
- "Selalu" berlaku sebagai Izinkan untuk permintaan itu;
  aturan permanen tetap dikelola Claude Code sendiri.
- Agen selain Claude Code bisa ikut dengan mengirim payload
  berbentuk sama ke `POST /pochi/hook` / `POST /pochi/permission`
  (tandai `pochi_agent` untuk nama tampilannya).
