# Nexora Cloud 1.6.0-alpha

## Fokus rilis
Perbandingan katalog lokal dan Telegram Cloud secara aman dan non-destruktif.

## Perubahan
- Tab Telegram Cloud menampilkan ringkasan jumlah file lokal dan cloud setelah katalog cloud dimuat.
- Pencocokan indikatif memakai pasangan nama file (case-insensitive) dan ukuran byte.
- Menampilkan jumlah kemungkinan cocok dan file cloud yang belum ditemukan pada katalog lokal.
- Tidak ada penghapusan, pemindahan, atau penimpaan otomatis.
- Version Code 16; versionName 1.6.0-alpha.

## Batasan penting
- Nama + ukuran bukan identitas file yang pasti; file berbeda dapat memiliki nama dan ukuran sama.
- Backend belum menyediakan checksum SHA-256 cloud sehingga hasil hanya indikasi, bukan sinkronisasi konten.
- Retry manual dan multi-akun Telegram belum termasuk rilis ini.

## Pengujian
1. Build APK melalui GitHub Actions.
2. Muat katalog cloud dan pastikan ringkasan tampil.
3. Uji file dengan nama/ukuran sama dan berbeda.
4. Pastikan tidak ada file yang otomatis dihapus atau ditimpa.
