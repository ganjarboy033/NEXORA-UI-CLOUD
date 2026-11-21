# Nexora Cloud 2.7.0-alpha — Riwayat Aktivitas Android

## Tujuan
Membawa catatan aktivitas backend ke aplikasi Android agar pengguna dapat memantau peristiwa katalog dari satu tempat.

## Implementasi
- Tab **Aktivitas** pada navigasi bawah.
- Menampilkan waktu server, nama file, jenis aktivitas, dan ID log.
- Filter Semua, Upload, Unduh, Hapus, dan Pulihkan.
- Tombol muat ulang dan indikator proses.
- Endpoint `GET /api/v1/activity` menerima `action` dan `limit` (maksimal 200).

## Batasan
- Halaman menampilkan maksimal 200 aktivitas terbaru; belum ada pagination riwayat.
- Filter tanggal dan ekspor CSV belum tersedia.
- `download_requested` menunjukkan permintaan dimulai, bukan bukti file selesai diterima perangkat.
- Log belum memiliki identitas aktor karena versi ini masih memakai satu API key bersama.
- Perlu deploy ulang backend agar filter `action` dikenali.
