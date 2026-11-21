# Nexora Cloud 1.4.0-alpha

## Fokus rilis
Visibilitas progres transfer cloud yang lebih baik saat upload dan download berlangsung.

## Perubahan
- `BackendClient.upload()` menerima callback progres dan melaporkan byte file yang telah ditulis ke koneksi.
- `BackendClient.downloadCloudFile()` menerima callback progres serta membaca `Content-Length` bila tersedia.
- `CloudTransferWorker` menyimpan progres ke SQLite secara berkala (ambang sekitar 512 KiB) agar halaman Transfer dapat memperbarui indikator tanpa menulis database untuk setiap potongan 64 KiB.
- Progres akhir ditetapkan 100% setelah operasi selesai dan worker menandai transfer Selesai.
- Versi aplikasi/backend menjadi 1.4.0-alpha (versionCode 14).

## Batasan yang diketahui
- Progres upload mengukur byte konten file yang sudah diserahkan ke stream HTTP, bukan konfirmasi bahwa server sudah menyimpan seluruh file. Status Selesai tetap hanya diberikan setelah respons sukses backend diterima.
- Progres download memerlukan header `Content-Length` agar persentase selama transfer dapat dihitung. Jika header tidak tersedia, jumlah byte tetap bertambah tetapi persentase dapat tetap 0% sampai selesai.
- Transfer lokal belum mendapat callback progres byte-per-byte.
- Retry manual, pause/resume, upload resumable, dan multi-akun Telegram belum tersedia.
- Multi-akun Telegram tetap harus dikelola aman di backend; token bot tidak boleh dimasukkan ke APK.

## Pengujian
1. Build APK melalui GitHub Actions.
2. Upload file berukuran besar dan amati byte/progres di tab Transfer.
3. Download file cloud dan amati byte/progres.
4. Uji file kecil untuk memastikan status akhir menjadi Selesai.
5. Uji gangguan jaringan dan pastikan WorkManager melakukan retry sesuai backoff.
6. Upgrade dari versi 1.3 untuk memastikan database versi 4 tetap terbaca (rilis ini tidak mengubah skema database).
