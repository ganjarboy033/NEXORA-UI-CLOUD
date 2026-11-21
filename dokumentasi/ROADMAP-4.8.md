# Nexora Cloud 4.8.0-alpha — Storage Insights & File Intelligence

## Fitur
- Ringkasan jumlah dan total ukuran file pada pustaka privat Nexora.
- Distribusi kategori: foto, video, audio, dokumen, arsip, dan lainnya.
- Peringkat ekstensi berdasarkan jumlah total ukuran.
- Daftar lima file terbesar untuk membantu menemukan pemakaian ruang.
- Ringkasan jumlah kelompok duplikasi SHA-256 yang sudah dihitung katalog.

## Akses
Buka tab **Berkas** lalu pilih **Analisis penyimpanan**.

## Cakupan data
Analisis menggunakan katalog lokal `filesDir/berkas` yang sudah diimpor ke Nexora. Ini bukan pemindai seluruh penyimpanan Android, bukan laporan ukuran Telegram Cloud, dan tidak menghitung ukuran cache maupun database. Nilai mengikuti data katalog lokal yang sedang tersedia.

## Keamanan
- Analisis bersifat baca-saja.
- Tidak menghapus, memindahkan, atau mengubah file.
- Duplikasi hanya dilaporkan; tidak ada penghapusan otomatis.
- Tidak ada data file yang diunggah ke backend untuk membuat laporan ini.

## Kompatibilitas
- Package: `dev.gsprasada.nexoracloud` (dipertahankan)
- Version name: `4.8.0-alpha`
- Version code: `49`
- Database: versi `7` (tidak ada migrasi baru)
- Build APK: harus diverifikasi melalui GitHub Actions pada repository pengguna.
