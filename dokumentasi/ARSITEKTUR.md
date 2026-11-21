# Arsitektur Nexora Cloud

## Android
- `MainActivity.kt`: navigasi Compose, galeri, berkas, dan pemilihan folder SAF.
- `LocalFileDatabase.kt`: katalog SQLite untuk metadata file privat.
- `filesDir/berkas`: penyimpanan fisik lokal aplikasi.
- Storage Access Framework: pemilihan lokasi ekspor ke folder yang diizinkan pengguna.

## Alur impor
1. Pengguna memilih file melalui Storage Access Framework.
2. Aplikasi menyalin file ke pustaka privat Nexora.
3. Waktu modifikasi sumber dipertahankan jika tersedia.
4. Katalog SQLite mencatat metadata dan SHA-256.
5. Galeri mengelompokkan seluruh ekstensi menurut tanggal lalu jam.

## Prinsip keamanan
- Jangan menyimpan bot token Telegram atau secret provider di APK.
- Koneksi Telegram harus dilakukan melalui backend yang dikendalikan pengguna.
- File duplikat hanya dilaporkan; tidak dihapus tanpa tindakan eksplisit.
- Akses SD Card/USB mengikuti izin Android melalui SAF.

## Telegram Cloud Engine (0.8)
Telegram Bot API diakses hanya dari backend Node.js. Android tidak menyimpan token bot. Backend menyimpan metadata di PostgreSQL dan menyimpan file pada chat/channel Telegram privat. Android-to-backend integration belum aktif pada 0.8.


## Versi 3.8.0-alpha
Tema, notifikasi transfer Android, dan pemulihan database lokal didokumentasikan pada `ROADMAP-3.8.md`.
