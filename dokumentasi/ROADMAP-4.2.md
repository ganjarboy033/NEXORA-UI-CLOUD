# Nexora Cloud 4.2.0-alpha — Identitas & Riwayat Perangkat

## Perubahan
- Identitas perangkat lokal dibuat satu kali menggunakan UUID dan disimpan pada tabel `pengaturan`.
- Pengguna dapat memberi nama tampilan perangkat; nama dan ID tetap tersimpan setelah aplikasi dibuka kembali.
- Waktu aktivitas terakhir dicatat saat aplikasi dimulai.
- Halaman Akun & Perangkat menampilkan identitas lokal (ID disamarkan), nama perangkat, waktu aktivitas terakhir, dan lima catatan transfer terbaru.
- Riwayat sinkronisasi yang ditampilkan bersumber dari tabel transfer lokal; status tidak disimpulkan dari server.

## Batasan
- ID lokal belum didaftarkan ke backend dan tidak digunakan sebagai token autentikasi.
- Belum ada sinkronisasi lintas perangkat atau pengelolaan sesi jarak jauh.
- Catatan transfer hanya mencerminkan aktivitas yang tercatat pada perangkat ini.

## Kompatibilitas
- Package ID tetap `dev.gsprasada.nexoracloud`.
- Database lokal tetap versi 6; data baru disimpan di tabel `pengaturan`, tanpa migrasi.
- Backend tidak memerlukan perubahan.
- `versionCode` 43; `versionName` `4.2.0-alpha`.

## Pengujian
- Perlu diuji pada perangkat Android: ubah nama perangkat, tutup dan buka aplikasi, lalu pastikan nama/ID tetap sama.
- Lakukan transfer untuk memastikan riwayat terbaru tampil.
- Build APK belum dinyatakan berhasil sampai workflow CI selesai tanpa error.
