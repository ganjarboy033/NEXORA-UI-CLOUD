# Nexora Cloud 2.0.0-alpha

## Fokus
Menghubungkan kontrol pencarian, kategori, dan pengurutan Android ke katalog server-side serta memperbaiki query agregasi backend.

## Perubahan
1. Pencarian nama file dari Android dikirim ke `GET /api/v1/files` melalui parameter `q`.
2. Filter gambar, video, audio, dan dokumen dikirim ke backend (`type`).
3. Pengurutan tanggal terbaru atau ukuran terbesar dikirim ke backend (`sort`).
4. Tombol pencarian memuat ulang hasil dari server; pagination berikutnya mempertahankan parameter pencarian/filter/pengurutan yang sama.
5. Memperbaiki query hitung total katalog agar menggunakan parameter pencarian yang benar. Pada 1.9, query COUNT memakai variabel `values` yang tidak didefinisikan sehingga endpoint daftar dapat gagal saat menghitung total.
6. Versi Android/backend diselaraskan ke `2.0.0-alpha`, versionCode `20`.

## Catatan dan batasan
- UI tetap menampilkan folder virtual berdasarkan timestamp WIB. File Telegram tidak dipindahkan atau diubah.
- Ringkasan jumlah dan ukuran total tersedia dalam respons API; kartu ringkasan Android masih menghitung data yang termuat, bukan keseluruhan katalog.
- Thumbnail/preview cloud, cursor pagination, autentikasi multi-pengguna, dan provider selain Telegram belum menjadi bagian versi ini.
- Deploy ulang backend diperlukan agar perbaikan query aktif.

## Pemeriksaan
- `node --check backend/src/server.js`
- `npm test` pada backend.
- Build APK harus diverifikasi melalui GitHub Actions `Build Nexora Cloud APK`.
