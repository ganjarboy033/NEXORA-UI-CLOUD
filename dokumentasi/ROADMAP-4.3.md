# Nexora Cloud 4.3.0-alpha — Cloud Sync Center

## Perubahan
- Halaman Transfer ditata ulang menjadi Cloud Sync Center.
- Ringkasan jumlah pekerjaan Menunggu, Berjalan, Selesai, dan Gagal dihitung dari riwayat transfer lokal.
- Keterangan mekanisme WorkManager dan ketergantungan backend ditampilkan agar status tidak disalahartikan sebagai sinkronisasi otomatis penuh.
- Riwayat transfer, progres, pembatalan, dan pembersihan riwayat selesai tetap menggunakan implementasi yang sudah ada.

## Batasan yang diketahui
- Belum ada sinkronisasi dua arah otomatis untuk seluruh pustaka perangkat.
- Belum ada penjadwalan berkala; transfer tetap dibuat oleh tindakan upload/download pengguna.
- Statistik merupakan ringkasan maksimal 100 catatan lokal, bukan statistik akun Telegram secara keseluruhan.
- Pekerjaan transfer diproses oleh WorkManager. Gangguan jaringan pada upload cloud akan memicu mekanisme retry yang sudah ada pada worker.

## Kompatibilitas
- Package: `dev.gsprasada.nexoracloud`
- Version code: 44
- Database tetap versi 6; tidak ada perubahan skema atau migrasi.
