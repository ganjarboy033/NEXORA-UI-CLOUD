# Nexora Cloud 2.6.0-alpha — Audit Aktivitas Backend

## Tujuan
Mencatat peristiwa penting katalog cloud agar tindakan upload, penghapusan lunak, pemulihan, dan permintaan unduhan dapat ditelusuri.

## Implementasi
- Tabel `activity_logs` dengan waktu server, aksi, ID/nama file, dan detail JSON.
- Indeks waktu untuk pengambilan riwayat terbaru.
- Pencatatan `upload`, `delete`, `restore`, dan `download_requested`.
- Endpoint terautentikasi `GET /api/v1/activity?limit=50` (maksimal 200).
- Migrasi tabel dijalankan otomatis saat backend mulai; definisi juga tersedia di `backend/sql/schema.sql`.

## Kontrak API
Respons sukses berbentuk `{ "activities": [...], "limit": 50 }`. Setiap item berisi `id`, `action`, `file_id`, `file_name`, `details`, dan `created_at`.

## Batasan yang diketahui
- Riwayat belum memiliki UI Android; endpoint dapat digunakan oleh klien terautentikasi.
- `download_requested` berarti permintaan unduhan dimulai, bukan bukti bahwa seluruh transfer sampai ke perangkat selesai.
- Log bersifat append-only melalui API aplikasi; belum ada fitur ekspor, retensi otomatis, atau multi-user actor.
- File fisik Telegram tetap tidak dihapus oleh fitur soft-delete.
