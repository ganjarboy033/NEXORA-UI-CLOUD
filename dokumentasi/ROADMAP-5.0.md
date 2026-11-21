# Nexora Cloud 5.0.0-alpha — First Build Candidate

## Fokus rilis
Versi ini disiapkan sebagai kandidat build pertama yang lebih lengkap, dengan fokus pada pengelolaan pustaka lokal yang aman dan tetap kompatibel dengan identitas aplikasi.

## Fitur baru
- **Favorit lokal**: tandai/lepaskan favorit per berkas; status disimpan permanen di SQLite dan tersedia sebagai filter.
- **Aksi massal**: pilih semua hasil yang terlihat, bagikan beberapa berkas, atau pindahkan pilihan ke Sampah Nexora sekaligus.
- **Pemeriksaan katalog**: sinkronkan ulang indeks SQLite dengan isi direktori pustaka privat; entri yang sudah tidak ada dibersihkan, file yang belum terindeks ditambahkan.
- **Migrasi database 7 → 8**: menambahkan tabel favorit tanpa menghapus tabel atau data sebelumnya.
- **Penyempurnaan keamanan file**: favorit hanya dapat diberikan pada file yang berada langsung di direktori pustaka privat; operasi rename/trash memperbarui indeks favorit.

## Fitur yang dipertahankan
- Integrasi Telegram Cloud melalui backend dan WorkManager.
- Katalog lokal, pencarian pintar, filter kategori/ukuran/waktu, pengurutan, analisis penyimpanan, deteksi duplikasi SHA-256, detail MIME, rename, Sampah dan pemulihan.
- Package `dev.gsprasada.nexoracloud`; min SDK 26; target SDK 35.

## Batasan yang diketahui
- Pemeriksaan katalog hanya mencakup direktori privat `filesDir/berkas`, bukan seluruh penyimpanan Android atau isi Telegram Cloud.
- Favorit melekat pada path file lokal; file yang dipindahkan keluar aplikasi tidak otomatis ikut sebagai favorit.
- Aksi massal ke Sampah memindahkan file satu per satu dan melaporkan jumlah yang berhasil.
- Provider Google Drive, OneDrive, WebDAV, dan S3 tidak dianggap aktif kecuali implementasi konektornya memang tersedia.
- APK belum dinyatakan lolos build sampai workflow GitHub Actions berhasil dijalankan pada repository pengguna.

## Pemeriksaan sebelum rilis
1. Jalankan workflow `Build Nexora Cloud APK`.
2. Instal APK pada perangkat uji dan verifikasi login Telegram/backend.
3. Uji upload, download, preview, rename, favorit, multi-select, Sampah/pulihkan, dan pemeriksaan katalog.
4. Simpan log build jika workflow gagal; jangan menghapus database aplikasi lama sebelum migrasi diuji.
