# Nexora Cloud 0.9.0-alpha — Integrasi Android ke Backend

## Yang ditambahkan
- Pengaturan URL backend dan API key pada layar Storage.
- Tes koneksi backend melalui `GET /health`.
- Pemeriksaan status Bot Telegram melalui `GET /api/v1/telegram/status`.
- Upload file dari pustaka lokal Android ke backend melalui multipart `POST /api/v1/files`.
- API key disimpan pada SharedPreferences privat aplikasi; token bot tidak pernah dimasukkan ke APK.
- Tombol upload cloud tersedia pada dialog detail file.

## Cara konfigurasi
1. Deploy folder `backend` pada server Node.js 20+ dengan PostgreSQL.
2. Terapkan `backend/sql/schema.sql` pada database.
3. Isi environment variable server berdasarkan `.env.example`:
   - `NEXORA_API_KEY` (minimal 32 karakter acak)
   - `DATABASE_URL`
   - `TELEGRAM_BOT_TOKEN`
   - `TELEGRAM_STORAGE_CHAT_ID`
4. Pastikan bot menjadi anggota channel/grup penyimpanan dan memiliki izin mengirim pesan.
5. Di aplikasi, buka Storage, masukkan URL HTTPS backend dan API key yang sama, tekan Simpan, lalu Tes backend dan Periksa koneksi Telegram.
6. Impor file ke pustaka lokal, buka detail file, lalu tekan Upload cloud.

## Batasan versi ini
- Upload Android berjalan sebagai permintaan HTTP sinkron dari layar detail; jangan menutup aplikasi saat upload berlangsung. Transfer Manager background untuk upload cloud akan dikerjakan pada iterasi berikutnya.
- Upload backend memakai buffer RAM dan dibatasi `MAX_UPLOAD_MB` (default 50 MB). Hindari menaikkan batas tanpa meninjau memori server.
- Telegram Bot API standar mempunyai batas ukuran file; implementasi ini bukan penyimpanan tanpa batas. Batas aktual mengikuti API Telegram dan konfigurasi server.
- Daftar file cloud dan unduhan cloud dari aplikasi belum diintegrasikan pada 0.9.
- API key aplikasi disimpan pada preferensi privat, bukan Android Keystore terenkripsi. Gunakan perangkat tepercaya dan HTTPS.
- Backend menggunakan satu API key bersama; belum ada akun pengguna, otorisasi per-user, atau multi-tenant.

## Build
Workflow GitHub Actions tetap memakai JDK 17 dan Gradle 8.9, serta mengunggah APK dan log diagnostik.
