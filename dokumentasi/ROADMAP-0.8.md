# Nexora Cloud 0.8.0-alpha — Fondasi Telegram Cloud Engine

## Yang ditambahkan
- Backend Node.js 20+ terpisah dari APK.
- Endpoint health check dan pemeriksaan koneksi Telegram Bot.
- Endpoint upload dokumen ke chat/channel privat Telegram.
- Metadata file disimpan di PostgreSQL, termasuk SHA-256, ukuran, MIME, Telegram message ID dan file ID.
- Endpoint daftar file dan proxy unduhan.
- Token Telegram hanya berada pada environment backend.
- Dockerfile, contoh environment, skema SQL, dan tes konfigurasi.
- Workflow Android tetap memakai Gradle 8.9 + JDK 17 dan mengunggah log saat gagal.

## Batas versi ini
- APK Android belum memanggil endpoint backend; integrasi UI dan transfer Android dijadwalkan pada 0.9.
- API key backend masih skema server/client sederhana, bukan autentikasi multi-pengguna untuk rilis publik.
- Upload memakai buffer memori dan batas default 50 MiB; jangan dipakai untuk file besar/traffic tinggi.
- Telegram Bot API memiliki batasan ukuran yang berubah menurut metode dan konfigurasi. `getFile` cloud Bot API standar memiliki batas unduh 20 MB; untuk file lebih besar perlu strategi lain/self-hosted Bot API atau provider storage lain.
- File Telegram dapat berhasil terkirim sebelum penyimpanan metadata database gagal; rekonsiliasi akan ditambahkan kemudian.

## Setup server
Lihat `backend/README.md`. Siapkan PostgreSQL, jalankan `backend/sql/schema.sql`, salin `.env.example` menjadi `.env`, lalu isi token bot, chat ID privat, API key kuat, dan database URL. Jangan commit `.env`.
