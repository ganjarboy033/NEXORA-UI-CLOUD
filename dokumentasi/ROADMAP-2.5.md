# Nexora Cloud 2.5.0-alpha — Antarmuka Sampah

## Perubahan Android
- Menambahkan tab **Sampah** pada navigasi bawah.
- Daftar sampah dimuat melalui `GET /api/v1/trash` (maksimal 200 item terbaru sesuai batas backend).
- Tombol **Pulihkan** memanggil `POST /api/v1/files/:id/restore` lalu menyegarkan daftar sampah.
- Dialog detail file cloud memiliki tindakan **Pindahkan ke Sampah** dengan konfirmasi.
- Tindakan tersebut memanggil `DELETE /api/v1/files/:id`; file fisik Telegram tidak dihapus.

## Backend
- Endpoint soft-delete, daftar sampah, dan restore dari 2.4 tetap digunakan.
- Versi health-check dan package dinaikkan ke 2.5.0-alpha.

## Catatan
- Arsip sampah dibatasi 200 item terbaru oleh endpoint saat ini.
- Penghapusan permanen Telegram, pemulihan massal, dan pengosongan sampah belum tersedia.
- Setelah deploy backend, build APK melalui GitHub Actions untuk memverifikasi kompilasi Android.
