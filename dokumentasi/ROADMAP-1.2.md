# Nexora Cloud 1.2.0-alpha

## Fokus rilis
Configuration Center awal, ekspor/impor konfigurasi non-rahasia, dan backup database katalog lokal.

## Perubahan
- Menu baru **Configuration Center** untuk melihat URL backend, menjalankan tes koneksi, dan mengatur preferensi provider.
- Preferensi provider Telegram, Google Drive, OneDrive, WebDAV, dan S3 disimpan lokal.
- Telegram adalah provider yang sudah terhubung ke alur backend saat ini. Toggle provider lain hanya menyimpan preferensi; integrasinya belum tersedia dan tidak boleh dianggap aktif.
- Ekspor/impor konfigurasi JSON dengan `schema_version` agar format dapat dimigrasikan.
- File konfigurasi tidak membawa API key, token bot, atau kredensial database. API key tetap berada di Android Keystore dan rahasia server tetap di lingkungan backend.
- Ekspor backup database SQLite lokal melalui Android Storage Access Framework, termasuk checkpoint WAL sebelum penyalinan.
- Versi aplikasi menjadi 1.2.0-alpha (versionCode 12).

## Batasan
- Impor konfigurasi memulihkan URL backend, preferensi provider, dan URI folder unduhan. Izin URI folder mungkin perlu dipilih ulang setelah pindah perangkat.
- API key backend tidak dipindahkan oleh JSON; masukkan kembali melalui halaman Storage pada perangkat tujuan.
- Backup database adalah ekspor satu arah pada rilis ini. Restore database belum disediakan agar tidak berisiko menimpa katalog yang sedang digunakan atau merusak file lokal.
- Belum ada pengelolaan multi-akun Telegram, Google Drive, OneDrive, WebDAV, atau S3. Provider non-Telegram masih berstatus preferensi/placeholder.
- Belum ada panel administrasi rahasia backend dari APK. Mengubah token Telegram atau kredensial PostgreSQL dari aplikasi Android tidak aman tanpa sistem admin terautentikasi dan enkripsi server-side.

## Pengujian yang harus dilakukan
1. Build APK melalui GitHub Actions.
2. Buka Menu > Configuration Center, ekspor JSON, lalu impor kembali.
3. Pastikan API key tidak muncul di file JSON.
4. Ekspor `nexora-local-backup.db`, pastikan file tersimpan dan ukurannya bukan nol.
5. Tes koneksi backend dan lakukan upload/download kecil.
