# Nexora Cloud — Catatan pengembangan 0.2.0-alpha

## Yang berubah dari 0.1
1. Menambahkan halaman Chat sebagai beranda hybrid bergaya Telegram.
2. Memperluas daftar tipe file yang dikenali untuk filter dan ikon.
3. Mempertahankan timestamp dokumen sumber saat file diimpor jika metadata tersedia.
4. Menambahkan dialog detail file.
5. Menambahkan proses salin file ke folder unduhan SAF yang dipilih pengguna.
6. Menambahkan penanganan nama file yang sudah ada agar tidak tertimpa.
7. Menambah status dan catatan batas integrasi provider cloud.

## Alur file lokal saat ini
`Pemilih Android → Import → Pustaka privat Nexora → Galeri/Berkas → Simpan ke folder SAF`

Pustaka privat menggunakan direktori `filesDir/berkas`. File tidak otomatis terlihat oleh aplikasi pengelola file lain sampai pengguna menyalinnya ke lokasi bersama melalui aksi simpan.

## Alur tujuan unduhan
1. Pengguna membuka Storage.
2. Pengguna memilih folder melalui Android `ACTION_OPEN_DOCUMENT_TREE`.
3. Aplikasi menyimpan URI permission yang diberikan Android.
4. Pengguna membuka item di Galeri atau Berkas.
5. Aplikasi membuat dokumen baru dalam folder yang dipilih dan menyalin isi file.
6. Jika terjadi kegagalan, dokumen parsial dihapus sebisa mungkin dan sumber di pustaka Nexora tetap dipertahankan.

## Catatan SAF
SAF tidak memberikan akses bebas ke semua jalur filesystem. Android/provider menentukan folder yang dapat dipilih, kemampuan tulis, dan apakah izin persisten didukung. USB OTG dan SD Card akan tersedia hanya jika ditampilkan oleh pemilih dokumen perangkat.

## Tahap berikutnya
- Memecah `MainActivity.kt` menjadi modul UI, model, repository, dan layanan transfer.
- Database Room untuk metadata, lokasi sumber, checksum, dan status sinkronisasi.
- Thumbnail foto/video dan preview file.
- Transfer queue dengan WorkManager dan notifikasi foreground saat diperlukan.
- Adapter backend Telegram dengan autentikasi aman; bot token hanya di server.
- Adapter provider cloud lain melalui kontrak `StorageProvider`.
- Backup terjadwal, retry, checksum, dan resume yang didukung provider.
