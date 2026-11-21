# Nexora Cloud 2.3.0-alpha

## Fokus rilis
Seleksi beberapa berkas pada folder arsip virtual dan antrean unduhan massal.

## Perubahan
- Mode **Pilih beberapa** tersedia ketika pengguna berada pada folder jam (tingkat keempat).
- Pengguna dapat mencentang file satu per satu atau memilih semua file yang sedang tampil pada folder tersebut.
- Tombol **Unduh N file terpilih** membuat satu pekerjaan WorkManager untuk setiap file.
- Setiap pekerjaan tetap memiliki catatan transfer tersendiri di menu Transfer: status, progres, dan pembatalan.
- Unduhan tetap memakai tujuan folder yang dipilih melalui Storage.
- Versi Android dan backend dinaikkan ke 2.3.0-alpha.

## Batasan yang diketahui
- Pilih semua hanya mencakup file yang sudah dimuat pada folder aktif di perangkat. Saat ini satu halaman backend berisi maksimal 100 file; muat halaman berikutnya sebelum memilih jika folder memiliki lebih banyak file.
- Unduhan massal membuat pekerjaan terpisah, bukan satu arsip ZIP.
- Penghapusan cloud belum disediakan. Telegram membatasi penghapusan pesan bot berdasarkan kondisi dan usia pesan; endpoint hapus sederhana berisiko membuat metadata dan file fisik tidak sinkron.
- Preview non-gambar masih bergantung pada aplikasi Android lain yang kompatibel.

## Verifikasi
- Jalankan `npm test` dari folder `backend`.
- Jalankan workflow GitHub Actions **Build Nexora Cloud APK** untuk kompilasi Android.
- Uji seleksi pada folder yang berisi beberapa file, lalu pantau tiap pekerjaan di menu Transfer.
