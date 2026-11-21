# Nexora Cloud 0.7.0-alpha

## Fokus: Transfer Manager yang terpantau

- Menambahkan kolom progres, byte selesai, dan total byte pada tabel transfer.
- Migrasi database versi 2 ke versi 3 tanpa menghapus riwayat yang sudah ada.
- Worker menulis progres aktual berdasarkan byte yang telah disalin.
- Tampilan Transfer memperlihatkan progress bar dan jumlah byte.
- Gangguan I/O dapat dijadwalkan ulang oleh WorkManager dengan exponential backoff.
- File tujuan parsial dihapus sebelum percobaan ulang agar tidak dianggap sebagai hasil lengkap.
- Mempertahankan workflow Gradle 8.9 + JDK 17 dan artifact log diagnostik.

## Batasan versi

- Transfer yang didukung masih penyalinan dari pustaka lokal Nexora ke folder SAF yang dipilih pengguna.
- Retry mengulang salinan dari awal; belum ada byte-range resume.
- Tombol pause/resume manual dan transfer cloud belum tersedia.
- UI riwayat membaca snapshot database saat layar dibuat; pembaruan progres live yang reaktif menjadi pekerjaan berikutnya.
- Belum dilakukan build Gradle di lingkungan pengembangan ini. Verifikasi akhir dilakukan melalui GitHub Actions.
