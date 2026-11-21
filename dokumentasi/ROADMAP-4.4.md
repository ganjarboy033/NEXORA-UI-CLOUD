# Nexora Cloud 4.4.0-alpha — Smart Archive Rules

## Perubahan
- Menambahkan pengaturan pola arsip tanggal: tahun/bulan/hari, tahun/bulan, tahun saja, atau tanpa folder tanggal.
- Pilihan disimpan di tabel `pengaturan` database lokal dan bertahan setelah aplikasi dibuka ulang.
- Menampilkan contoh struktur folder berdasarkan tanggal saat ini agar pengguna dapat memeriksa pola yang dipilih.
- Tidak mengubah nama file asli dan tidak memindahkan/menghapus berkas lokal.

## Batasan
- Aturan ini saat ini merupakan preferensi lokal/metadata. Pengelompokan folder pada penyimpanan Telegram belum diterapkan karena backend belum menyediakan kontrak API untuk folder tujuan atau metadata arsip.
- Tidak ada pemindahan file otomatis, sehingga tidak ada risiko merusak susunan file yang sudah ada.

## Kompatibilitas
- Package: `dev.gsprasada.nexoracloud`
- Version code: 45
- Database tetap versi 6; preferensi baru menggunakan tabel `pengaturan` yang sudah ada, tanpa migrasi skema.
