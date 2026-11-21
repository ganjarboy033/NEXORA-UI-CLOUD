# Nexora Cloud 4.6.0-alpha — File Detail & Batch Operations

## Perubahan
- Pengelola Berkas menyediakan pemilihan beberapa file sekaligus.
- Berkas terpilih dapat dibagikan melalui Android Sharesheet menggunakan FileProvider dan izin baca sementara.
- Dialog detail menampilkan nama, ukuran, waktu modifikasi, ekstensi, dan lokasi file.
- Lokasi file dapat disalin ke clipboard.
- Pemilihan dibatalkan saat kategori berubah; tidak ada operasi hapus atau pindah massal.

## Keamanan dan batasan
- Hanya file lokal yang terdaftar pada katalog Nexora yang ditampilkan.
- Berbagi menggunakan URI FileProvider, bukan mengekspos path file sebagai URI publik.
- Aplikasi tujuan menerima akses baca sementara melalui grant URI permission.
- Berbagi beberapa tipe file menggunakan MIME `*/*`; aplikasi penerima menentukan dukungan format.
- Operasi batch bersifat non-destruktif. Tidak ada file yang dihapus atau dipindahkan.

## Kompatibilitas
- Package: `dev.gsprasada.nexoracloud`
- Version code: 47
- Database tetap versi 6; tidak ada perubahan skema.
