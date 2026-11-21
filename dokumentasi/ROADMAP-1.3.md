# Nexora Cloud 1.3.0-alpha

## Fokus rilis
Kontrol antrean transfer: pembatalan pekerjaan, pelacakan ID pekerjaan WorkManager, dan pembaruan status yang lebih konsisten.

## Perubahan
- Skema katalog lokal naik ke versi 4 dengan kolom `work_id` untuk menghubungkan catatan transfer dan pekerjaan WorkManager.
- Transfer upload/download cloud menyimpan UUID pekerjaan sehingga dapat dibatalkan dari halaman Transfer.
- Tombol **Batalkan transfer** muncul untuk pekerjaan berstatus Menunggu atau Berjalan.
- Worker menangani pembatalan coroutine dan memperbarui riwayat menjadi Dibatalkan.
- Status transfer tetap tersimpan di SQLite meskipun layar aplikasi ditutup.
- Versi aplikasi/backend menjadi 1.3.0-alpha (versionCode 13).

## Batasan yang diketahui
- Pembatalan meminta WorkManager menghentikan pekerjaan; operasi jaringan yang sedang berlangsung dapat memerlukan waktu untuk berhenti.
- Progress upload masih diperbarui setelah unggahan selesai, belum berupa persentase byte real-time. Progress download juga belum menampilkan byte berjalan selama streaming.
- Tombol batalkan tersedia untuk transfer yang memakai WorkManager dan memiliki UUID pekerjaan; riwayat lama dari versi sebelumnya tidak memiliki UUID.
- Belum ada multi-akun Telegram. Backend saat ini masih menggunakan satu konfigurasi bot dan satu chat penyimpanan dari environment server. Token bot tetap tidak boleh disimpan di APK.
- Retry otomatis untuk gangguan jaringan tetap mengikuti backoff WorkManager; tombol retry manual belum tersedia.

## Pengujian
1. Build APK melalui GitHub Actions.
2. Jalankan satu upload dan satu download cloud.
3. Buka Transfer, tekan Batalkan saat status Menunggu/Berjalan.
4. Pastikan status berubah menjadi Dibatalkan dan pekerjaan tidak terus berjalan.
5. Upgrade dari versi 1.2 untuk memastikan katalog lama tetap terbaca.
