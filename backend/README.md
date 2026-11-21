# Nexora Cloud Backend 2.3.0-alpha

Backend Node.js untuk menghubungkan aplikasi ke Telegram Bot API tanpa menaruh token Telegram di APK.

## Komponen
- `GET /health`: pemeriksaan service dan PostgreSQL.
- `GET /api/v1/telegram/status`: memeriksa bot Telegram.
- `POST /api/v1/files`: upload satu file multipart (`file`) ke chat/channel penyimpanan.
- `GET /api/v1/files`: daftar metadata file.
- `GET /api/v1/files/:id/download`: proxy download dari Telegram.

Semua endpoint `/api/v1/*` memerlukan `Authorization: Bearer <NEXORA_API_KEY>`.

## Persiapan
1. Buat bot melalui BotFather.
2. Buat grup/channel privat untuk arsip dan tambahkan bot dengan izin mengirim pesan.
3. Ambil chat ID tujuan penyimpanan.
4. Siapkan PostgreSQL dan jalankan `backend/sql/schema.sql`.
5. Salin `.env.example` menjadi `.env`, isi semua rahasia di server.
6. Jalankan `npm ci` lalu `npm start` dengan Node.js 20+.

Jangan commit `.env`, token bot, atau API key. Gunakan HTTPS di deployment. Jangan menaruh `TELEGRAM_BOT_TOKEN` di aplikasi Android.

## Batas dan catatan
- Batas upload default 50 MiB (konfigurabel); batas aktual juga tunduk pada Telegram Bot API dan konfigurasi bot/server.
- Versi ini menggunakan memory storage untuk menerima file; cocok untuk fondasi uji coba, bukan upload berukuran besar atau trafik tinggi. Produksi sebaiknya memakai streaming/chunking dan batas konkurensi.
- Jika Telegram berhasil menerima file tetapi penyimpanan metadata PostgreSQL gagal, pesan Telegram mungkin sudah terlanjur tersimpan. Perlu proses rekonsiliasi pada versi berikutnya.
- `NEXORA_API_KEY` saat ini merupakan autentikasi server-ke-klien sederhana. Sebelum rilis publik, ganti dengan autentikasi pengguna, rotasi token, rate limit, audit log, dan pembatasan akses per pengguna.
- Unduhan diproxy melalui backend agar file token Telegram tidak terekspos ke aplikasi.


### API katalog arsip (1.9)

`GET /api/v1/files?limit=50&offset=0&q=laporan&type=all&sort=date&direction=desc`

- `limit`: 1–100; `offset`: mulai dari 0.
- `q`: pencarian nama file.
- `type`: `all`, `image`, `video`, `audio`, `document`, atau `archive`.
- `sort`: `date`, `name`, atau `size`; `direction`: `asc` atau `desc`.
- Respons `pagination` berisi `total`, `total_bytes`, `has_more`, dan `next_offset`.

`GET /api/v1/archive/folders?prefix=2026/11` mengembalikan jumlah file dan total byte untuk folder virtual tingkat berikutnya.


## Integrasi Android (2.0)
Aplikasi Android meneruskan parameter `q`, `type`, `sort`, dan `direction` ke endpoint katalog. Tombol **Cari di seluruh cloud** menjalankan pencarian pada seluruh katalog server, bukan hanya item yang sudah dimuat. Saat memuat halaman berikutnya, parameter yang sama dipakai kembali.


### Statistik katalog (2.1)

`GET /api/v1/stats` mengembalikan jumlah seluruh file, total ukuran byte, kategori gambar/video/audio/dokumen, dan waktu unggahan terbaru. Endpoint ini memerlukan header autentikasi yang sama dengan endpoint `/api/v1/files`.


Versi backend paket ini: **2.3.0-alpha**. Rilis ini tidak menambahkan migrasi database; perubahan utama berada pada aplikasi Android untuk seleksi dan antrean unduhan massal.


Versi backend: **2.3.0-alpha**. Tidak ada migrasi database baru pada rilis ini.


## Arsip terhapus (2.4.0-alpha)
- `DELETE /api/v1/files/:id`: soft delete metadata.
- `GET /api/v1/trash`: daftar maksimal 200 item terhapus.
- `POST /api/v1/files/:id/restore`: pulihkan metadata.
File Telegram tidak dihapus permanen. Backend menambahkan kolom `deleted_at` saat startup; pastikan role database memiliki izin ALTER TABLE atau jalankan migrasi SQL manual.

### Versi 2.5.0-alpha
Tidak ada perubahan kontrak endpoint arsip terhapus dari 2.4. Pembaruan versi ini terutama mengintegrasikan daftar, soft-delete, dan restore ke UI Android.


### Riwayat aktivitas (2.7.0-alpha)

`GET /api/v1/activity?limit=50&action=upload` mengembalikan hingga 200 aktivitas terbaru. Endpoint memerlukan `Authorization: Bearer <NEXORA_API_KEY>`. Aksi yang dicatat: `upload`, `delete`, `restore`, dan `download_requested`.


### Statistik kapasitas dan ekspor (2.8.0-alpha)
- `GET /api/v1/stats/storage` — rekap bulanan/tahunan berdasarkan metadata aktif, zona WIB.
- `GET /api/v1/activity?since=<ISO>&until=<ISO>` — filter tanggal; `until` eksklusif.
- `GET /api/v1/activity/export.csv?action=all&since=<ISO>&until=<ISO>` — ekspor maksimal 5.000 baris.
Semua endpoint memerlukan Bearer API key. Statistik bukan indikator kuota Telegram.
