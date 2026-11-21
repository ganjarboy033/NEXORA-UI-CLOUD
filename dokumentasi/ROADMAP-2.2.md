# Nexora Cloud 2.2.0-alpha

## Fokus: pratinjau berkas cloud

- Tombol **Pratinjau** tersedia pada dialog detail berkas cloud.
- Gambar diambil melalui endpoint unduhan terautentikasi, lalu ditampilkan dalam dialog Compose.
- Dekode gambar memakai `inSampleSize` agar gambar berukuran besar tidak langsung dimuat pada resolusi penuh.
- Audio, video, PDF, dan format lain diteruskan ke aplikasi Android yang dapat menangani MIME type terkait melalui `FileProvider`.
- Berkas sementara disimpan di cache privat aplikasi, bukan folder unduhan publik.
- Tombol **Unduh** tetap memakai antrean WorkManager yang sudah ada.

## Batasan yang diketahui

- Pratinjau saat ini mengunduh seluruh berkas terlebih dahulu; belum memakai HTTP Range/streaming parsial.
- Gambar ditampilkan di dalam aplikasi. Audio/video/dokumen dibuka menggunakan aplikasi eksternal yang tersedia di perangkat.
- Keberhasilan membuka format non-gambar bergantung pada aplikasi pembuka yang terpasang.
- Berkas cache pratinjau dapat dibersihkan Android saat ruang penyimpanan dibutuhkan.
- Pemilihan banyak file, unduhan massal, dan cursor pagination belum termasuk rilis ini.

## Pemeriksaan

- `node --check backend/src/server.js`
- `npm test`
- `unzip -t` pada paket distribusi
- Build APK perlu dijalankan melalui GitHub Actions; lingkungan pengerjaan tidak menyediakan Gradle lokal.
