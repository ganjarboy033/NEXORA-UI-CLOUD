# Nexora Cloud 1.7.0-alpha — Arsip Waktu Telegram

## Tujuan
Merapikan penelusuran unggahan Telegram tanpa mengubah cara penyimpanan file yang sudah berjalan.

## Perubahan
- Caption setiap unggahan baru memuat struktur `YYYY/MM/DD/HH` dalam zona waktu Asia/Jakarta (WIB), nama file, dan SHA-256.
- Metadata API mengembalikan `archive_path` dari `created_at` PostgreSQL dalam zona WIB.
- Tab Telegram Cloud mengelompokkan daftar berkas berdasarkan Tahun/Bulan/Hari/Jam.
- File lama tetap dapat ditampilkan; jika metadata jalur arsip tidak tersedia, aplikasi memberi label arsip lama.
- Tidak membuat folder/topic Telegram secara otomatis. Telegram tidak menyediakan folder bertingkat biasa di dalam satu chat/channel; caption adalah indeks visual, sedangkan pengelompokan lengkap dilakukan di aplikasi Nexora.

## Catatan
- Satu chat/channel privat khusus Nexora tetap menjadi tempat penyimpanan.
- Untuk membuka semua unggahan secara rapi, gunakan tab Telegram Cloud di Nexora; Telegram sendiri menampilkan pesan berdasarkan urutan waktu dan caption.
- Jam pada caption menggunakan WIB.
- Build Android perlu diverifikasi melalui GitHub Actions.
