# Nexora Cloud 4.1.0-alpha — Akun & Perangkat

## Perubahan
- Menambahkan halaman Akun & Perangkat yang dapat dibuka dari Menu > Akun dan koneksi.
- Menampilkan model perangkat, versi Android/API, versi aplikasi, package ID, jumlah file lokal, dan jumlah catatan transfer.
- Menambahkan pemeriksaan koneksi backend melalui endpoint `/health` yang sudah digunakan aplikasi.
- Menambahkan pemeriksaan status autentikasi akun Telegram melalui endpoint `/api/v1/telegram/account-auth/status` yang sudah tersedia di BackendClient.
- Menampilkan pesan status konfigurasi/kesiapan Telegram berdasarkan respons backend.

## Batasan yang disengaja
- Halaman ini bersifat diagnostik dan tidak mengklaim sebagai pengelola sesi jarak jauh.
- Daftar perangkat, pencabutan sesi, dan logout perangkat lain belum dibuat karena backend pada proyek ini belum menyediakan endpoint daftar/revoke sesi perangkat.
- Tidak ada token atau rahasia yang ditampilkan pada halaman.

## Kompatibilitas
- Package ID tetap `dev.gsprasada.nexoracloud`.
- Database lokal versi 6 tidak berubah; tidak ada migrasi.
- Backend tidak memerlukan endpoint baru.
- `versionCode` 42; `versionName` 4.1.0-alpha.

## Pengujian
- Perlu diuji pada perangkat Android dan backend aktif.
- Build APK belum dinyatakan berhasil sampai GitHub Actions selesai tanpa error.
