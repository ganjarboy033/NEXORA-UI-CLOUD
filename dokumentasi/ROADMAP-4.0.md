# Nexora Cloud 4.0.0-alpha — Dasbor Cloud

## Perubahan
- Halaman Chat sebelumnya ditingkatkan menjadi Dasbor Nexora tanpa mengubah tab navigasi lain.
- Kartu ringkasan menampilkan jumlah file lokal, jumlah file cloud, kapasitas cloud, dan total media (foto + video + audio).
- Tombol Perbarui mengambil statistik cloud dan lima aktivitas terbaru melalui endpoint backend yang sudah tersedia (`/api/v1/stats` dan `/api/v1/activity`).
- Daftar aktivitas terbaru menyediakan pintasan ke halaman Aktivitas.
- Pintasan menuju Cloud, Storage, Galeri, dan Berkas tetap tersedia.
- Statistik hanya ditampilkan setelah backend memberikan data; jika belum dimuat, UI menampilkan tanda belum tersedia.

## Kompatibilitas
- Package ID tetap `dev.gsprasada.nexoracloud`.
- Database lokal versi 6 tidak diubah; tidak ada migrasi database.
- API backend yang digunakan sudah ada pada `BackendClient`; tidak menambah endpoint server.
- `versionCode` 41; `versionName` 4.0.0-alpha.

## Catatan pengujian
- Koneksi internet dan backend yang sudah dikonfigurasi diperlukan untuk memuat statistik dan aktivitas.
- Build APK belum dinyatakan berhasil sampai workflow GitHub Actions selesai tanpa error.
