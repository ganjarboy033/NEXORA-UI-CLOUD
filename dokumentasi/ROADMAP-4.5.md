# Nexora Cloud 4.5.0-alpha — Smart File Manager

## Perubahan
- Halaman Galeri kini menyediakan pengurutan Terbaru, Nama, Terbesar, dan Terkecil.
- Ringkasan jumlah dan total ukuran file mengikuti hasil pencarian/filter yang sedang tampil.
- Halaman Berkas menjadi Pengelola Berkas dengan filter kategori Semua, Foto, Video, Audio, Dokumen, dan Arsip.
- Pengurutan daftar Berkas tersedia berdasarkan terbaru, nama, dan ukuran terbesar.
- Menampilkan jumlah kelompok file dengan hash SHA-256 identik berdasarkan indeks katalog lokal.
- Pencarian nama file yang sudah tersedia di bilah pencarian utama tetap diterapkan pada Galeri dan Berkas.

## Batasan dan keamanan
- Pemindaian hanya mencakup file yang sudah masuk katalog lokal Nexora; bukan seluruh penyimpanan Android atau seluruh isi Telegram.
- Kesamaan hash menunjukkan konten identik, tetapi aplikasi tidak menghapus atau menggabungkan file secara otomatis.
- File cloud belum dicampur ke katalog lokal dalam tahap ini.

## Kompatibilitas
- Package: `dev.gsprasada.nexoracloud`
- Version code: 46
- Database tetap versi 6; tidak ada perubahan skema.
