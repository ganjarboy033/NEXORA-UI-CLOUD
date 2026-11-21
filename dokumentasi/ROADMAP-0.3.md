# Nexora Cloud — Catatan versi 0.3.0-alpha

## Selesai pada fondasi ini
- Thumbnail lokal untuk gambar pada galeri.
- Pratinjau gambar pada dialog detail.
- Pengelompokan tanggal → jam tetap berlaku untuk semua file.
- Pemilihan tujuan ekspor melalui Android Storage Access Framework.

## Batasan yang masih ada
- Pustaka saat ini menyimpan salinan privat lokal, bukan katalog cloud universal.
- Telegram, Google Drive, OneDrive, WebDAV, dan S3 belum terhubung.
- Thumbnail video/audio dan preview PDF belum dibuat.
- Tidak ada upload cloud aktif pada versi ini.

## Tahap berikutnya
1. Database metadata Room dan migrasi.
2. Antrean transfer persisten dan progres.
3. Backend API dengan autentikasi dan penyimpanan rahasia di server.
4. Adapter provider Telegram dan provider cloud lainnya.
5. Pengujian akses SD Card/USB OTG di beberapa perangkat Android.
