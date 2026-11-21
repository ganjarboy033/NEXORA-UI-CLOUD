# Nexora Cloud 1.8.0-alpha — Arsip Virtual Bertingkat

## Tujuan
Menyajikan metadata file cloud sebagai navigasi folder virtual Tahun → Bulan → Hari → Jam, tanpa mengubah struktur penyimpanan fisik Telegram.

## Implementasi
- Navigasi bertingkat menggunakan `archive_path` dari backend.
- Breadcrumb dapat digunakan untuk kembali ke tingkat folder sebelumnya.
- Daftar file ditampilkan setelah pengguna mencapai tingkat Jam.
- Ringkasan katalog lokal/cloud tetap tersedia.
- File dapat diunduh dari tampilan folder Jam.

## Batasan
- Folder merupakan tampilan virtual di Nexora, bukan folder Telegram.
- Daftar cloud saat ini dibatasi maksimum 500 item per permintaan; arsip besar memerlukan pagination lanjutan.
- Data arsip lama tanpa `archive_path` waktu lengkap belum masuk ke pohon folder tahun/bulan/hari/jam.
- Pencocokan lokal-cloud masih berdasarkan nama dan ukuran, bukan checksum.
