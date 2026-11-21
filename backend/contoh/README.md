# Backend (rancangan, belum diimplementasikan)

Backend akan menjadi perantara autentikasi dan penyimpanan. Endpoint konseptual:
- `POST /api/v1/files/upload-session`
- `POST /api/v1/files/{id}/complete`
- `GET /api/v1/files?cursor=...`
- `GET /api/v1/files/{id}/download`
- `GET /api/v1/providers`
- `POST /api/v1/providers/telegram/connect`

Jangan pernah mengirim Telegram bot token ke aplikasi Android. Simpan secret di environment backend dan batasi akses berdasarkan akun pengguna.
