# Nexora Cloud 1.9.0-alpha

## Fokus
Peningkatan navigasi arsip Telegram virtual agar lebih siap untuk katalog besar.

## Perubahan
1. API daftar file menggunakan pagination `limit` dan `offset` dengan batas maksimal 100 item.
2. API daftar mendukung parameter `q`, `type`, `sort`, dan `direction`.
3. Respons daftar menyertakan metadata pagination, total file, dan total ukuran.
4. Endpoint `/api/v1/archive/folders` menyediakan jumlah file dan total byte untuk folder virtual pada tingkat berikutnya.
5. Android menampilkan pencarian lokal untuk file yang sudah dimuat, filter dasar gambar/video/audio/dokumen, pengurutan tanggal/ukuran, dan tombol memuat halaman berikutnya.
6. Version Name `1.9.0-alpha`, Version Code `19`.

## Batasan yang diketahui
- Pencarian dan filter yang tampak di Android saat ini berlaku pada data yang sudah dimuat; parameter API server-side telah tersedia tetapi belum seluruhnya dihubungkan ke kontrol Android.
- Thumbnail gambar cloud belum tersedia.
- Pagination memakai offset; untuk katalog sangat besar, cursor pagination dapat menjadi peningkatan berikutnya.
- Folder merupakan pengelompokan virtual berdasarkan waktu WIB, bukan folder fisik Telegram.
- Backend harus di-deploy ulang agar endpoint dan versi baru tersedia.

## Pemeriksaan
- `node --check backend/src/server.js`
- `node --test backend/tests/config.test.js`
- Build APK belum dapat diverifikasi pada lingkungan ini; jalankan GitHub Actions `Build Nexora Cloud APK`.
