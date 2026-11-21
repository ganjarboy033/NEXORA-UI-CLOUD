# Nexora Cloud 4.7.0-alpha — Advanced File Operations

## Perubahan
- Ganti nama file pada katalog lokal Nexora.
- Pindahkan file ke folder `filesDir/sampah` (Sampah privat aplikasi), bukan menghapus permanen.
- Daftar Sampah menampilkan nama dan waktu pemindahan.
- Pulihkan file dari Sampah ke `filesDir/berkas`.
- Jika nama tujuan saat pemulihan sudah dipakai, Nexora menambahkan awalan timestamp agar file tidak tertimpa.
- Validasi path membatasi rename dan pemindahan hanya pada file langsung di pustaka privat Nexora.

## Database
- Database dinaikkan dari versi 6 ke 7.
- Tabel baru `sampah` menyimpan nama asli, path file di Sampah, dan waktu pemindahan.
- Migrasi membuat tabel baru tanpa menghapus tabel atau data sebelumnya.

## Batasan dan keamanan
- Operasi hanya berlaku untuk file yang disalin ke pustaka privat Nexora (`filesDir/berkas`).
- File sumber di penyimpanan umum/SAF dan file cloud Telegram tidak diubah.
- Memindahkan ke Sampah bersifat dapat dipulihkan; belum ada penghapusan permanen dari UI.
- Folder Sampah berada di penyimpanan privat aplikasi dan dapat ikut terhapus ketika data aplikasi dihapus/uninstall.
- Path divalidasi menggunakan canonical path untuk mencegah keluar dari direktori pustaka.

## Kompatibilitas
- Package: `dev.gsprasada.nexoracloud`
- Version code: 48
- Database: versi 7 (migrasi otomatis dari versi 6)
- Build APK belum diverifikasi pada lingkungan pengembangan.
