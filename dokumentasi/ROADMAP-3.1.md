# Nexora Cloud 3.1.0-alpha — Universal Provider Contract

## Fokus rilis
Membuat kontrak operasi cloud yang seragam sebagai fondasi multi-provider, tanpa mengklaim konektor yang belum dibuat sudah aktif.

## Yang ditambahkan
- `CloudStorageProvider`: kontrak untuk tes koneksi, daftar file, upload, download, hapus, dan pulihkan.
- `TelegramCloudProvider`: adapter nyata yang meneruskan operasi ke `BackendClient` dan endpoint backend Nexora yang sudah ada.
- `CloudStorageProviderFactory`: pembuatan provider terpusat; provider selain Telegram ditolak secara eksplisit sampai konektornya tersedia.
- `CloudTransferWorker` kini menjalankan upload/download melalui kontrak provider, bukan memanggil `BackendClient` secara langsung.
- Versi aplikasi menjadi `3.1.0-alpha` (`versionCode` 32).

## Batasan yang tetap berlaku
- Google Drive, OneDrive, WebDAV, dan S3 masih berstatus rencana; belum ada OAuth, autentikasi, upload, maupun download untuk provider tersebut.
- Layar katalog dan konfigurasi yang sudah ada belum seluruhnya dimigrasikan ke kontrak provider.
- Antrean transfer rilis ini tetap menggunakan Telegram sebagai provider aktif.
- Token bot Telegram tetap berada di backend; jangan memasukkannya ke APK atau repositori.

## Validasi
- Jalankan `npm test` di folder `backend`.
- Build APK melalui workflow GitHub Actions `Build Android APK`.
- Build Gradle belum dapat dinyatakan lulus sebelum workflow menghasilkan APK.

## Tahap berikutnya: 3.2
Migrasi pembacaan katalog dan aksi file UI ke `CloudStorageProvider`, lalu menambahkan status provider aktif dan pesan kemampuan yang konsisten. Setelah jalur Telegram stabil, konektor provider tambahan dapat dibuat satu per satu dengan autentikasi resmi.
