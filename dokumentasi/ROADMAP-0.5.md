# Nexora Cloud 0.5.0-alpha — Transfer Manager Foundation

## Yang ditambahkan
- Tabel riwayat transfer pada SQLite (`transfer`) dengan migrasi database versi 1 ke 2.
- Navigasi Transfer pada aplikasi.
- Riwayat maksimal 100 aktivitas terakhir.
- Status transfer: Menunggu, Berjalan, Selesai, dan Gagal.
- Penyimpanan aktivitas saat file disalin ke folder pilihan melalui Android Storage Access Framework.
- Perbaikan pemanggilan `MenuScreen` dengan mengirim parameter wajib `duplicateGroups`.
- Workflow GitHub Actions menyimpan log `--info` dan `--stacktrace` walaupun build gagal.

## Batasan versi ini
Transfer Manager saat ini mencatat dan menampilkan aktivitas salin/unduh lokal yang dimulai ketika aplikasi sedang berjalan. Ini belum merupakan antrean background tahan-restart, belum memiliki pause/resume byte-level, dan belum mengunggah ke Telegram/cloud. Jangan menampilkan fitur tersebut sebagai sudah aktif.

## Langkah berikutnya
1. Pindahkan transfer ke WorkManager dengan input yang aman dan persistensi status.
2. Implementasikan transfer bertahap dengan progres byte aktual.
3. Tambahkan retry dan pembatalan yang konsisten.
4. Bangun backend Telegram; token bot hanya berada di backend.
5. Tambahkan adapter provider cloud melalui kontrak penyimpanan yang seragam.
