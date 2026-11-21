# Nexora Cloud 2.8.0-alpha — Statistik Kapasitas & Ekspor Aktivitas

## Fitur
- `GET /api/v1/stats/storage`: rekap kapasitas dan jumlah file per bulan (24 periode) serta per tahun (10 periode), zona `Asia/Jakarta`. Hanya menghitung file aktif.
- `GET /api/v1/activity`: mendukung filter `since` dan `until` dalam ISO-8601, selain filter jenis aktivitas. Batas hasil tetap maksimal 200. `until` bersifat eksklusif.
- `GET /api/v1/activity/export.csv`: ekspor riwayat ke CSV UTF-8 dengan BOM, filter jenis/tanggal, maksimal 5.000 baris.
- Android `BackendClient` menyediakan pemanggil untuk statistik kapasitas dan ekspor CSV agar dapat dipakai oleh layar berikutnya.

## Contoh
`GET /api/v1/activity?since=2026-11-01T00%3A00%3A00%2B07%3A00&until=2026-12-01T00%3A00%3A00%2B07%3A00`

`GET /api/v1/activity/export.csv?action=upload`

## Catatan
- Semua endpoint membutuhkan `Authorization: Bearer <API_KEY>`.
- Statistik dihitung dari metadata database, bukan kuota akun Telegram.
- Versi ini menyediakan kontrak API dan fungsi client; grafik interaktif serta tombol ekspor pada UI Android belum dipasang.
- Backend harus di-deploy ulang agar endpoint baru tersedia.
