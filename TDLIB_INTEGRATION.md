# Nexora Cloud — Login Telegram Nyata (TDLib) & Cloud Saved Messages

## Hasil audit singkat (sebelum perubahan)
- Login: `LoginActivity` hanya placeholder (tombol lanjut membuka dialog info, QR kosong). TDLib ada di `third_party/` tetapi **tidak dipakai** satu pun di kode aplikasi.
- Cloud: seluruh daftar/upload/download berjalan lewat backend Node.js + **Bot API** (batas 50 MB upload, 20 MB download getFile), bukan akun pengguna.
- UI folder di-hardcode tepat 4 level tanggal (`Tahun/Bulan/Hari/Jam`); folder kustom tidak bisa tampil.
- Placeholder lain: Google Drive/OneDrive/WebDAV/S3 (status PLANNED), hapus permanen Telegram, daftar sesi perangkat jarak jauh.
- `TelegramAppConfiguration.FACTORY` menyimpan `apiId/apiHash` langsung di source (lihat catatan keamanan).

## Referensi Unlim Cloud (hasil analisis, tanpa menyalin kredensial/kodenya)
TDLib dipakai langsung di app (`org.drinkless.tdlib.Client`), login via `SetTdlibParameters → SetAuthenticationPhoneNumber → CheckAuthenticationCode → CheckAuthenticationPassword/RegisterUser` (+ `RequestQrCodeAuthentication`). File = pesan `InputMessageDocument` di satu chat penyimpanan; metadata (folder, sampah, chunk) di **caption**; daftar via `SearchChatMessages` filter dokumen; file besar dipecah menjadi chunk. Aplikasi referensi memakai api_id/api_hash milik developernya — **tidak dipakai** di sini.

## File baru / diubah
| File | Fungsi |
|---|---|
| `TdClient.kt` | `Client.create`, `SetTdlibParameters`, state login → `StateFlow<TdAuthState>`, `request` (suspend) & `call` (blocking) |
| `TelegramAuthViewModel.kt` | ViewModel login: nomor HP, OTP, 2FA, registrasi, QR, email, pilih chat penyimpanan, logout |
| `LoginActivity.kt` | Layar Compose untuk seluruh alur di atas + panel akun |
| `TdCloud.kt` | Saved Messages/channel: list, upload (chunk >1900 MiB), download, sampah, hapus permanen, folder, statistik, log aktivitas |
| `CloudGateway.kt` | Memakai TDLib bila login, selain itu backend lama (kompatibel mundur) |
| `CloudStorageProvider.kt`, `CloudTransferWorker.kt` | `TdlibCloudProvider`, pembatalan transfer via `runInterruptible` |
| `MainActivity.kt` | Routing ke gateway, UI folder generik (buat/ganti nama/hapus/pindah), hapus permanen |
| `app/build.gradle.kts` | + `lifecycle-viewmodel-compose` |

## Konfigurasi
1. **API ID/Hash**: sudah ada di `TelegramAppConfiguration.FACTORY`, jadi login langsung berjalan. Untuk override build: env `TELEGRAM_API_ID` / `TELEGRAM_API_HASH` (dibaca `build.gradle.kts`, prioritas lebih tinggi).
2. **Build TDLib** (wajib sekali, menghasilkan `TdApi.java` + `libtdjni.so`): jalankan perintah langkah "Build official TDLib for Android" di `.github/workflows/build-apk.yml` (Linux, butuh `gperf php-cli perl ninja-build`), atau cukup push ke GitHub — workflow sudah melakukannya.
3. `gradle assembleDebug`.

## Cara kerja penyimpanan
- Chat penyimpanan: Saved Messages (default), atau channel privat baru / chat pilihan (panel akun di layar Login).
- Caption: `NEXORA CLOUD / Folder: … / File: … / SHA256: … [/ Chunk: … ] [/ Trash: …]` (kompatibel dengan caption backend lama).
- Folder kosong hanya tersimpan di perangkat (disimpan di server Telegram hanya saat berisi file).

## Catatan keamanan
- `apiHash` yang ter-commit di repo publik sebaiknya dipindah ke secret CI/`local.properties` dan dihapus dari `FACTORY`.
- Kunci enkripsi database TDLib dibuat acak dan disimpan terenkripsi lewat Android Keystore (`SecretStore`).

## Batasan yang perlu Anda ketahui
- Kode **belum dikompilasi** di lingkungan pembuatan (tanpa Gradle/SDK/TDLib); signature TDLib diverifikasi otomatis terhadap `td_api.tl` proyek ini, tetapi uji build & login pertama di perangkat tetap diperlukan.
- File baru yang diunggah backend (bot) dan akun pengguna ke chat yang sama saling terbaca; edit caption pesan milik bot dari akun pengguna dapat ditolak Telegram.

## Pembaruan (rilis kedua)
- Daftar file kini memakai `TdApi.GetChatHistory` dan mengenali `MessageDocument`, `MessageVideo`, `MessageAudio`, `MessagePhoto` (foto dipilih ukuran terbesar).
- `TdCloud.transfers: StateFlow<List<TdTransfer>>` menampilkan progres upload/download real-time; UI: panel "Transfer Telegram" di layar Cloud.
- `TdCloud.selfUserId()` memakai `TdApi.GetMe()`; chat Saved Messages = user ID sendiri (via `CreatePrivateChat`).
- API ID/Hash dapat diisi dari layar Login ("Ubah API ID / API Hash"). Nilai `TELEGRAM_API_ID/HASH` saat build tetap diprioritaskan.
- Semua panggilan TDLib: callback berjalan di thread TDLib; UI memakai `suspend` (`request`), operasi file memakai `call` hanya dari `Dispatchers.IO`/Worker.

## Rilis ketiga — UI Nexora v12 (dari nexora-cloud-v12-ultimate.html)
Cangkang UI diganti total; layar lama tetap hidup sebagai subhalaman ("Mode Klasik" + halaman Sampah, Aktivitas, Storage, Konfigurasi, Akun, Personalisasi).

| Elemen desain | Implementasi Compose |
|---|---|
| Top bar, drawer geser, 3 tab (Gallery/Files/Setting), FAB Upload, banner status | `NexoraRoot.kt` |
| Daftar item, grid media 3 kolom (thumbnail nyata), chip filter, batch | `NexoraTabs.kt` |
| Viewer imersif (zoom cubit, HUD buffering + batal, filmstrip, info/label) | `NexoraViewer.kt` |
| Sheet, dialog, toast, tombol, field (animasi) | `NexoraUi.kt`, token warna `NexoraTheme.kt` |
| Data terpadu Telegram + lokal, favorit/arsip/label, operasi file | `ExplorerViewModel.kt` |
| Upload (file/folder batch, ekstrak ZIP aman), retry transfer | `NexoraTransfers.kt`, `NexoraPages.kt` |
| Konfigurasi Telegram (proxy nyata), Multi-Cloud, Debug, Google, Link | `NexoraPages2.kt` |

Fungsional penuh: login & penyimpanan Telegram (tidak diubah), daftar terpadu, pratinjau, unduh ke folder SAF (opsi cache sesi), pindah/ganti nama/hapus/sampah/hapus permanen, folder virtual, favorit/arsip/label, batch, ekstrak ZIP, upload file/folder, retry upload gagal, duplikat (SHA-256, nama+ukuran, lintas sumber), cache sesi, proxy SOCKS5/MTProto (TDLib AddProxy), mode Test Server, verifikasi token GitHub, panel debug (log, ping TDLib, SQLite, state).

Kerangka sengaja tidak aktif (dijelaskan di layarnya): Login Google/Drive (butuh OAuth Client ID), provider GitHub/S3/WebDAV/MEGA sebagai penyimpanan, duplikat "media serupa" (butuh perceptual hash), pilihan DC manual (TDLib mengelola DC otomatis), pemutar video/audio internal (dibuka lewat aplikasi lain), dan **Nexora Link** (mengirim sesi Telegram lewat relay pihak ketiga setara menyerahkan kunci akun; hanya desain layar, tidak ada sesi yang dikirim).
Tidak diimplementasikan: tombol "Simulasikan Jaringan Terputus" dari desain (hanya alat demo).

Perubahan pada kode inti (semua aditif, perilaku default sama): `TdClient` (+log, +opsi Test Server default mati), `TdCloud` (+log, +renameFile), `CloudGateway` (+rename/upload ke folder), `CloudTransferWorker` (+KEY_FOLDER opsional), `LocalFileDatabase` (+listHashes), `file_paths.xml` (+files-path agar berkas lokal dapat dibagikan).
