# Nexora Cloud 3.6.0-alpha — Preferensi UI Aktif

## Perubahan
- Versi Android menjadi `3.6.0-alpha` (`versionCode 37`); package ID tetap agar data aplikasi terpasang dapat dipertahankan saat pembaruan.
- Preferensi `default_sort` kini diterapkan pada katalog lokal: terbaru, nama A–Z, atau ukuran terbesar.
- Preferensi `compact_mode` kini memengaruhi daftar Berkas dan kepadatan grid Galeri.
- Pilihan warna aksen dan preferensi lain tetap disimpan di SQLite `nexora_local.db`.
- Skema ekspor/impor konfigurasi JSON yang sudah ada tetap mencakup preferensi personalisasi; API key dan rahasia tidak dimasukkan.

## Batasan
- Urutan katalog Telegram Cloud memiliki kontrol sortirnya sendiri dan tidak otomatis mengikuti sortir katalog lokal.
- Mode ringkas saat ini diterapkan pada layar Berkas dan Galeri; belum semua layar.
- Backup database adalah salinan data lokal aplikasi. Simpan file backup di lokasi aman dan jangan menganggapnya sebagai backup sesi Telegram.
- Build APK harus diverifikasi melalui GitHub Actions pada lingkungan pengguna.

## Uji manual
1. Pilih `Nama A–Z`, buka Berkas, lalu pastikan nama berurutan.
2. Pilih `Ukuran terbesar`, buka Berkas, lalu pastikan file terbesar muncul lebih dahulu.
3. Aktifkan Tampilan ringkas dan buka Berkas serta Galeri.
4. Tutup dan buka ulang aplikasi; pastikan pilihan tetap tersimpan.
5. Ekspor konfigurasi, impor kembali, dan periksa preferensi.
