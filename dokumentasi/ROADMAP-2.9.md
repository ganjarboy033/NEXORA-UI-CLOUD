# Nexora Cloud 2.9.0-alpha — UI Statistik & Ekspor CSV

## Android
- Bagian Analitik kapasitas cloud ditambahkan ke Pusat penyimpanan.
- Menampilkan total file dan ukuran dari periode bulanan yang dikembalikan API (maksimal 24 bulan). Angka ini bukan total sepanjang masa bila katalog lebih tua dari rentang tersebut.
- Grafik kapasitas bulanan menggunakan indikator proporsional, maksimal 12 bulan terbaru.
- Ringkasan tahunan menampilkan jumlah file dan ukuran untuk maksimal 10 tahun.
- Ekspor aktivitas CSV menggunakan Storage Access Framework sehingga pengguna menentukan lokasi dan nama file.
- Filter tanggal opsional menggunakan format `YYYY-MM-DD`; tanggal akhir inklusif pada UI dan dikonversi menjadi batas eksklusif hari berikutnya di zona `Asia/Jakarta`.

## Backend/API
- Tetap menggunakan `GET /api/v1/stats/storage` dan `GET /api/v1/activity/export.csv` dari backend 2.8.
- Tidak ada perubahan skema database maupun environment variables.
- Perbaikan client: parameter `since` dan `until` hanya dikirim ketika masing-masing terisi.

## Batasan dan catatan
- Grafik menampilkan ukuran metadata file per periode unggah, bukan kuota Telegram dan bukan ukuran fisik akun Telegram.
- Ekspor maksimal 5.000 aktivitas sesuai batas endpoint.
- Build APK harus diverifikasi melalui GitHub Actions; tidak mengklaim kompilasi lokal tanpa Gradle.
