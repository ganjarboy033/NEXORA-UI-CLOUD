# Nexora Cloud 1.1.0-alpha

## Fokus rilis
Transfer cloud melalui Android WorkManager dan penguatan penyimpanan rahasia konfigurasi.

## Perubahan
- Upload cloud dijadwalkan melalui WorkManager, bukan lagi permintaan jaringan yang mengunci dialog UI.
- Download cloud dijadwalkan melalui WorkManager dan menyimpan hasil langsung ke folder SAF pilihan (internal/SD Card/USB OTG yang didukung perangkat).
- Pekerjaan transfer mensyaratkan koneksi jaringan dan memakai retry eksponensial untuk gangguan I/O/jaringan.
- Riwayat transfer lokal mencatat arah, status, progres akhir, dan pesan.
- API key backend di perangkat dienkripsi AES-GCM menggunakan kunci non-exportable Android Keystore.
- API key versi lama dimigrasikan ke penyimpanan terenkripsi ketika dibaca.
- Android Auto Backup dimatikan agar ciphertext rahasia tidak dipulihkan ke perangkat tanpa kunci Keystore asal.
- Token Telegram dan kredensial PostgreSQL tetap hanya berada di backend; tidak pernah dimasukkan ke APK.

## Batasan yang diketahui
- Progres upload jaringan belum berupa byte-per-byte; UI menampilkan status antrean dan hasil akhir.
- Endpoint backend masih memakai satu API key server bersama, belum sistem pengguna/role atau multi-tenant.
- Konfigurasi server (Telegram token, chat ID, PostgreSQL URL, Nexora API key) masih diatur melalui environment variables backend. Control Center dinamis dan backup/import konfigurasi penuh menjadi pekerjaan lanjutan; jangan menyimpan rahasia server di Android.
- Retry WorkManager menangani kegagalan jaringan/I/O yang dilempar sebagai IOException. Kesalahan autentikasi atau konfigurasi akan ditandai gagal.
- Belum ada pembatalan transfer dari UI dan belum ada resumable/chunk upload.

## Verifikasi
Build perlu dijalankan melalui GitHub Actions workflow `Build Nexora Cloud APK`. Pastikan juga pengujian manual: upload file kecil, tutup aplikasi, cek status transfer, refresh Cloud, lalu unduh ke folder lokal/SD Card/OTG.
