# Nexora Cloud 0.6.0-alpha

## Transfer Manager berbasis WorkManager
- Transfer salin lokal dijalankan sebagai pekerjaan persisten WorkManager.
- Antrean tetap dikelola Android ketika Activity ditutup atau proses aplikasi dihentikan.
- Worker mencatat status Menunggu/Berjalan/Selesai/Gagal/Dibatalkan ke database SQLite.
- Worker melaporkan progres byte melalui WorkManager Data.
- File tujuan yang gagal disalin dihapus agar tidak meninggalkan file parsial.
- Nama file tujuan dibuat unik untuk menghindari penimpaan.

## Batasan versi
- Implementasi ini menangani salin file dari pustaka internal Nexora ke folder SAF yang dipilih pengguna.
- Pause/resume pada tingkat byte belum tersedia; WorkManager dapat menjadwalkan ulang pekerjaan, tetapi salinan yang terputus akan dimulai ulang.
- Upload cloud/Telegram belum diaktifkan.
- Antrean cloud dan sinkronisasi multi-provider menjadi tahap berikutnya.
