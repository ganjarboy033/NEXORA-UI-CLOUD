# Nexora Cloud 0.4.0-alpha — Katalog Metadata Lokal

## Selesai
- SQLiteOpenHelper sebagai indeks metadata lokal.
- Indeks file menyimpan nama, ukuran, waktu modifikasi, waktu impor, MIME type, SHA-256, sumber, dan path.
- Rekonsiliasi katalog dengan pustaka privat saat aplikasi mulai.
- Indeks diperbarui setelah impor file berhasil.
- Deteksi kelompok duplikat berdasarkan hash; tidak menghapus file otomatis.
- Galeri dan daftar berkas membaca katalog metadata.

## Batasan
- File fisik tetap berada di `filesDir/berkas`.
- Database bukan layanan cloud dan belum melakukan sinkronisasi jaringan.
- Hashing dilakukan secara sinkron di thread impor; untuk file besar, tahap berikutnya perlu dipindah ke worker latar belakang.
- Tidak ada penghapusan otomatis atau deduplikasi otomatis.

## Berikutnya: 0.5.0-alpha
1. Antrean transfer persisten dengan status menunggu, berjalan, selesai, dan gagal.
2. WorkManager untuk pekerjaan salin/backup lokal yang dapat dilanjutkan.
3. Progress dan retry yang transparan.
4. Abstraksi provider cloud; implementasi Telegram tetap melalui backend, bukan token di APK.
