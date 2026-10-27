# Protokol sinkronisasi Pochi (draf)

Versi: 1 (draf). Akan diisi lengkap saat M4.
Lihat PRD Bagian 5.8.

Format pesan JSON:
```json
{ "v": 1, "type": "sync.changes", "id": "uuid", "ts": 1760000000000, "data": { } }
```

Tipe minimal: hello, pair.request, pair.confirm, sync.request, sync.changes, ack,
media.state, media.command, device.status, notify.mirror, file.offer, file.chunk,
file.done, ping, pong, error.
