# Nexora Cloud 4.9.0-alpha — Smart File Search & Metadata

## Fitur baru
- Pencarian cepat pada katalog lokal berdasarkan nama file, ekstensi, atau teks lokasi.
- Filter pintar: file berukuran minimal 100 MiB, file yang diperbarui dalam 7 hari terakhir, dan file tanpa ekstensi.
- Pengurutan terbaru, nama, ukuran terbesar, dan ukuran terkecil.
- Detail file menampilkan MIME type perkiraan serta status keberadaan dan keterbacaan file.

## Cara menggunakan
1. Buka tab **Berkas**.
2. Ketik kata kunci pada kolom pencarian.
3. Pilih filter pintar bila diperlukan.
4. Atur urutan hasil melalui chip pengurutan.
5. Ketuk baris file untuk melihat detail metadata.

## Batasan dan privasi
- Pencarian hanya memeriksa katalog pustaka privat Nexora (`filesDir/berkas`).
- Filter 7 hari menggunakan waktu modifikasi yang tersimpan pada katalog.
- MIME type ditentukan dari ekstensi, sehingga merupakan perkiraan dan bukan pemeriksaan isi file.
- Status file diperiksa dari file lokal saat dialog detail dibuka.
- Tidak ada file yang dipindahkan, dihapus, atau diunggah saat pencarian.

## Kompatibilitas
- Package: `dev.gsprasada.nexoracloud` (dipertahankan)
- Version name: `4.9.0-alpha`
- Version code: `50`
- Database: versi `7` (tidak ada migrasi)
- Build APK: verifikasi melalui GitHub Actions pada repository pengguna.
