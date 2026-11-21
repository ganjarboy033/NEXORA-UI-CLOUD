# Nexora Cloud 1.5.0-alpha

## Fokus rilis
Pengelolaan riwayat transfer dan perapian katalog lokal.

## Perubahan
- Menambahkan aksi **Bersihkan riwayat selesai** pada Transfer Manager.
- Penghapusan hanya menyasar status terminal: Selesai, Gagal, dan Dibatalkan. Transfer Menunggu/Berjalan tidak disentuh.
- Menambahkan indeks SQLite berdasarkan status dan waktu untuk mendukung pencarian/penyaringan riwayat berikutnya.
- Database lokal naik ke versi 5 dengan migrasi non-destruktif.
- Versi aplikasi/backend menjadi 1.5.0-alpha (versionCode 15).

## Batasan
- Penghapusan riwayat tidak menghapus file lokal atau file cloud.
- Retry manual, sinkronisasi katalog cloud, dan multi-akun Telegram belum termasuk rilis ini.
- Kredensial Telegram dan database tetap berada di backend, tidak disimpan dalam APK.

## Pengujian yang perlu dilakukan
1. Build APK melalui GitHub Actions.
2. Jalankan satu transfer hingga Selesai, lalu bersihkan riwayat.
3. Pastikan transfer Menunggu/Berjalan tetap ada setelah riwayat dibersihkan.
4. Upgrade dari versi 1.4 untuk memvalidasi migrasi SQLite versi 5.
