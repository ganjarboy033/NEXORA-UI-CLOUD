# Nexora Cloud 2.4.0-alpha — Arsip terhapus aman

## Perubahan
- Backend menambahkan `deleted_at` secara aditif saat startup.
- `DELETE /api/v1/files/:id` memindahkan metadata ke arsip terhapus (soft delete).
- `GET /api/v1/trash` menampilkan maksimal 200 item terhapus terbaru.
- `POST /api/v1/files/:id/restore` memulihkan item ke katalog aktif.
- Daftar file, statistik, ringkasan folder, dan unduhan hanya memproses item aktif.
- File fisik di Telegram tidak dihapus. Ini disengaja untuk mencegah kehilangan data; penghapusan permanen belum disediakan.

## Kompatibilitas dan deployment
Kolom `deleted_at` dibuat otomatis dengan `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` ketika backend dimulai. Akun database perlu izin ALTER TABLE pada tabel `cloud_files`. Jika izin migrasi dibatasi, jalankan perintah berikut oleh administrator database sebelum deploy:

```sql
ALTER TABLE cloud_files ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
```

Endpoint baru memerlukan header `Authorization: Bearer <API_KEY>`. Gunakan ID numerik file.

## Batasan
Kontrol arsip terhapus belum ditambahkan ke UI Android pada rilis ini; endpoint backend sudah tersedia untuk integrasi UI berikutnya. Tidak ada penghapusan fisik dari Telegram.
