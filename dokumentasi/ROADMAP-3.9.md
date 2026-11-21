# Nexora Cloud 3.9.0-alpha — Privacy Center

## Perubahan
- Menambahkan Pusat Privasi pada halaman Personalisasi.
- Menjelaskan lokasi penyimpanan katalog, preferensi, riwayat, dan API key.
- Menjelaskan bahwa token bot Telegram dan kredensial database server tetap berada di backend.
- Menjelaskan bahwa transfer cloud mengirim file/metadata yang dipilih ke backend yang dikonfigurasi pengguna.
- Menambahkan tindakan membersihkan cache internal dan eksternal aplikasi.
- Tindakan bersih cache tidak menghapus database SQLite, pengaturan, riwayat transfer, maupun file pustaka.
- Tidak menambahkan SDK analitik atau iklan.

## Kompatibilitas
- Package ID tetap `dev.gsprasada.nexoracloud`.
- Database versi 6 tidak berubah dan tidak memerlukan migrasi.
- `versionCode` 40; `versionName` 3.9.0-alpha.

## Batasan
- Pusat Privasi merupakan ringkasan transparansi dan pembersihan cache; belum menyediakan penghapusan akun/data cloud dari server.
- Penghapusan data cloud harus dilakukan melalui fitur backend yang terotorisasi.
- Build APK wajib diverifikasi melalui GitHub Actions.
