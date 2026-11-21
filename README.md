# Nexora Cloud 5.1.0-alpha

## Fokus rilis
Integrasi sumber resmi TDLib untuk Android mulai disiapkan sebagai mesin Telegram Client API. Target produk saat ini adalah cloud berbasis akun Telegram pengguna; provider cloud lain ditunda sampai alur Telegram stabil.

## TDLib yang disertakan
Source `third_party/tdlib` berasal dari arsip TDLib yang diberikan untuk proyek ini. Build Android mengikuti contoh resmi `example/android` milik TDLib dan menghasilkan Java binding serta library native `tdjni` untuk `arm64-v8a` dan `x86_64` pada GitHub Actions.

## Konfigurasi GitHub Actions
Tambahkan Repository Secrets:
- `TELEGRAM_API_ID`: API ID numerik dari Telegram.
- `TELEGRAM_API_HASH`: API Hash 32 karakter hex.

Secrets hanya dipakai saat proses build untuk menghasilkan `BuildConfig.TELEGRAM_API_ID` dan `BuildConfig.TELEGRAM_API_HASH`. Jangan commit nilai kredensial ke source. Catatan: nilai yang dikompilasi ke APK dapat diekstrak; API Hash bukan rahasia yang dapat disembunyikan dari pengguna APK. Jangan pernah menanam bot token atau kredensial server di APK.

Jika Secrets belum diisi, build tetap dapat dikompilasi dengan API ID 0 dan API Hash kosong, tetapi autentikasi Telegram tidak akan dapat dimulai sampai konfigurasi valid disediakan.

## Ruang lingkup
Rilis ini menyiapkan toolchain TDLib native di pipeline build. Build APK penuh dan pengujian login nomor/QR harus diverifikasi di GitHub Actions/perangkat Android sebelum menyatakan autentikasi siap produksi. TDLib berbeda dari Telegram Bot API; sistem bot/backend lama tidak otomatis berubah menjadi autentikasi akun pribadi hanya dengan menambahkan library.
# Nexora Cloud 5.0.2-alpha

**Telegram App Configuration Center** — kolom App Title, Short Name, Platform, URL, api_id, api_hash, serta alamat MTProto DC 2 Test/Production dapat diedit, divalidasi, disimpan, dan dikembalikan ke nilai bawaan melalui tombol Reset. API hash disimpan terenkripsi menggunakan Android Keystore. Database tetap versi 8; package ID tetap `dev.gsprasada.nexoracloud`; versionCode 52.

Catatan integrasi: profil ini tersimpan di aplikasi, tetapi backend Telegram Client API belum otomatis membaca nilainya. Lihat `dokumentasi/ROADMAP-5.0.2.md`.

# Nexora Cloud 5.0.0-alpha

**First Build Candidate** — versi kandidat untuk build dan pengujian perangkat pertama. Mempertahankan package ID `dev.gsprasada.nexoracloud`, menaikkan versionCode ke 51, dan melakukan migrasi database 7 → 8 secara non-destruktif.

### Pengelolaan pustaka yang ditingkatkan
- Favorit berkas yang tersimpan di SQLite dan filter khusus Favorit.
- Pilih semua hasil yang sedang terlihat, bagikan banyak berkas, atau pindahkan pilihan ke Sampah sekaligus.
- Pemeriksaan katalog untuk menyelaraskan indeks lokal dengan folder privat Nexora.
- Aksi rename dan pemindahan ke Sampah menjaga konsistensi data favorit.

Lihat `dokumentasi/ROADMAP-5.0.md` untuk fitur, batasan, dan checklist pengujian.

## Nexora Cloud 4.9.0-alpha

Smart File Search & Metadata menambahkan pencarian nama/ekstensi/lokasi, filter pintar berdasarkan ukuran dan waktu modifikasi, pengurutan tambahan, serta informasi MIME dan status file. Lihat `dokumentasi/ROADMAP-4.9.md`.

## Nexora Cloud 4.8.0-alpha

Storage Insights & File Intelligence menambahkan analisis baca-saja untuk pustaka lokal: ringkasan kategori, ukuran berdasarkan ekstensi, lima file terbesar, dan ringkasan kelompok duplikasi. Lihat `dokumentasi/ROADMAP-4.8.md`.

## Versi 1.5.0-alpha

Transfer Manager kini dapat membersihkan riwayat terminal tanpa menghapus file maupun mengganggu transfer aktif. Lihat `dokumentasi/ROADMAP-1.5.md`.

# Nexora Cloud 1.4.0-alpha

Aplikasi Android native Kotlin + Jetpack Compose dengan konsep Telegram-inspired Hybrid Cloud Manager.

## Fitur fondasi
- Galeri universal: tanggal → jam untuk semua tipe file.
- Import file ke pustaka privat aplikasi.
- Katalog metadata SQLite dan deteksi kelompok hash duplikat.
- Pemilihan folder tujuan melalui Storage Access Framework (termasuk SD Card/USB yang diizinkan Android).
- Transfer Manager untuk riwayat penyalinan/unduhan lokal.
- GitHub Actions build APK dengan artefak log diagnostik.

## Build melalui GitHub Actions
1. Unggah seluruh isi folder proyek ke repository.
2. Buka tab Actions.
3. Jalankan workflow **Build Nexora Cloud APK**.
4. Unduh `nexora-cloud-debug-apk` jika berhasil.
5. Jika gagal, unduh `nexora-build-debug-logs` untuk melihat `build-debug.log` dan laporan.

## Status penting
Telegram Cloud terhubung melalui backend untuk katalog, upload, dan download. Upload serta download cloud kini dijadwalkan melalui WorkManager. Google Drive, OneDrive, WebDAV, dan S3 masih berupa rencana provider, belum konektor aktif. Backup/import konfigurasi penuh dan Control Center multi-provider masih tahap berikutnya.

Lihat `dokumentasi/` untuk arsitektur dan roadmap versi.


### Transfer Manager 0.6
Unduhan ke folder pilihan kini dijalankan melalui WorkManager dan dicatat secara persisten. Worker menampilkan progres byte, menangani kegagalan, serta menghapus file parsial jika transfer gagal. Pause/resume byte-level dan transfer cloud belum tersedia.


## Versi 0.7.0-alpha

Transfer Manager kini menyimpan progres berbasis byte ke database dan menampilkan progress bar. Gangguan I/O dapat dicoba ulang oleh WorkManager dengan exponential backoff. Lihat `dokumentasi/ROADMAP-0.7.md` untuk batasan versi.

## Versi 0.8.0-alpha — Telegram Cloud Engine (backend)
Folder `backend/` kini berisi layanan Node.js + PostgreSQL untuk health check, status bot, upload ke Telegram, katalog metadata, dan proxy download. Token bot hanya disimpan pada environment server. APK belum terhubung langsung ke API pada versi ini; integrasi Android dijadwalkan pada versi 0.9. Ikuti `backend/README.md` dan `dokumentasi/ROADMAP-0.8.md`.

## Versi 0.9.0-alpha
Integrasi awal Android ke backend tersedia pada menu **Storage**: simpan URL HTTPS + API key, tes `/health`, periksa status Telegram, lalu upload file dari dialog detail file. Lihat `dokumentasi/ROADMAP-0.9.md` dan `backend/README.md` untuk konfigurasi server serta batasan versi.


## Versi 1.0.0-alpha — Cloud Browser
- Tab Telegram Cloud untuk membaca katalog file dari PostgreSQL melalui backend.
- Unduh file cloud melalui backend langsung ke folder tujuan SAF yang dipilih pengguna (memori internal, SD Card, atau USB OTG jika didukung perangkat).
- Nama file bentrok dibuat unik; file sumber di cloud tidak dihapus.
- Backend tetap menyimpan token Telegram di environment server.

## Batasan 1.0.0-alpha
- Daftar cloud dimuat manual melalui tombol Muat ulang.
- Unduhan cloud saat ini berlangsung di proses layar dan belum memakai WorkManager; jangan tutup aplikasi selama transfer.
- Batas Telegram, backend, dan jaringan tetap berlaku.
- Export/import konfigurasi terenkripsi dan Configuration Center multi-provider menjadi tahap berikutnya; belum diklaim tersedia pada versi ini.


## Versi 1.1.0-alpha — Cloud Transfer & Secret Storage
- Upload dan download cloud dijalankan melalui WorkManager dengan persyaratan koneksi jaringan dan retry eksponensial.
- Download cloud menulis langsung ke folder SAF pilihan termasuk SD Card/USB OTG yang didukung perangkat.
- Riwayat transfer menampilkan status dan diperbarui saat tab Transfer terbuka.
- API key backend di perangkat dienkripsi AES-GCM memakai Android Keystore; nilai lama dimigrasikan saat dibaca.
- Android Auto Backup dinonaktifkan untuk mencegah pemulihan ciphertext tanpa kunci Keystore perangkat asal.
- Token Telegram dan kredensial PostgreSQL tetap hanya disimpan pada backend.

## Batasan 1.1.0-alpha
Progres upload/download belum byte-per-byte pada semua fase; pause/resume cloud dan resumable upload belum tersedia. Import/export konfigurasi penuh belum disediakan pada rilis ini. Pengaturan rahasia backend tetap melalui environment variables hosting, bukan aplikasi Android. Build APK harus diverifikasi melalui GitHub Actions.


## Fitur 1.2.0-alpha
- Configuration Center untuk tes backend dan preferensi provider.
- Ekspor/impor konfigurasi non-rahasia JSON.
- Ekspor backup katalog SQLite lokal.
- Rahasia tidak disertakan dalam file konfigurasi.

## Fitur 1.3.0-alpha
- Pembatalan antrean transfer dari halaman Transfer.
- UUID WorkManager disimpan di katalog lokal untuk menghubungkan riwayat dan pekerjaan.
- Penanganan status Dibatalkan dan migrasi database ke versi 4.

## Pembaruan 1.4.0-alpha
- Upload cloud melaporkan jumlah byte yang sudah dikirim.
- Download cloud melaporkan jumlah byte yang sudah diterima; ukuran total berasal dari header Content-Length bila server menyediakannya.
- Worker menyimpan progres ke SQLite secara berkala agar tab Transfer dapat menampilkan progres selama pekerjaan berlangsung.
- Penulisan progres dibatasi per sekitar 512 KiB untuk mengurangi beban tulis database.
- API callback progres tetap kompatibel dengan pemanggilan lama melalui parameter default.

Lihat `dokumentasi/ROADMAP-1.4.md` untuk batasan dan skenario pengujian.

## Versi 1.6.0-alpha — Ringkasan Katalog Lokal dan Cloud
- Tab Telegram Cloud menampilkan ringkasan perbandingan katalog lokal dengan katalog cloud.
- Pencocokan awal memakai nama (tanpa membedakan huruf besar/kecil) dan ukuran byte.
- Hasil diberi label kemungkinan cocok; tidak melakukan sinkronisasi atau penghapusan otomatis.
- Lihat `dokumentasi/ROADMAP-1.6.md` untuk batasan dan skenario uji.


## Versi 1.7.0-alpha — Arsip Telegram
Unggahan baru diberi caption arsip `Tahun/Bulan/Hari/Jam` (WIB), nama file, dan SHA-256. Tab Telegram Cloud mengelompokkan katalog berdasarkan jalur waktu tersebut. Telegram tidak memiliki folder bertingkat biasa dalam satu chat; karena itu tampilan folder penuh disediakan Nexora, sementara caption membantu penelusuran langsung di Telegram.

## Versi 1.8.0-alpha
Tampilan Telegram Cloud kini memakai navigasi folder virtual Tahun → Bulan → Hari → Jam. Struktur tersebut berasal dari metadata `archive_path` backend; Telegram tetap menyimpan pesan/file di chat atau channel, bukan folder sungguhan. Daftar masih dibatasi 500 item per permintaan.


## Nexora Cloud 1.9.0-alpha

Pembaruan 1.9 melanjutkan fondasi 1.8 dengan fokus pada arsip berukuran besar:

- Daftar cloud memakai pagination server-side (maksimal 100 item per halaman).
- Endpoint daftar mendukung pencarian nama, filter tipe MIME, pengurutan tanggal/nama/ukuran, dan metadata total.
- Endpoint ringkasan folder virtual menyediakan jumlah file dan total byte per tingkat folder.
- Aplikasi Android menyediakan pencarian pada data yang sudah dimuat, filter dasar, pengurutan ukuran/tanggal, dan tombol memuat halaman berikutnya.
- Versi backend dan Android diselaraskan ke 1.9.0-alpha (versionCode 19).

Catatan: pencarian/filter pada layar Android saat ini bekerja pada halaman yang telah dimuat. Parameter pencarian dan filter server tersedia pada API untuk integrasi UI server-side tahap berikutnya. Thumbnail cloud belum diaktifkan pada 1.9; file asli tetap diunduh melalui backend dari Telegram.


# Nexora Cloud 2.0.0-alpha

Pencarian nama, filter tipe file, dan pengurutan kini dikirim dari aplikasi Android ke API katalog server-side. Pagination berikutnya mempertahankan parameter pencarian dan filter. Perbaikan 2.0 juga memperbaiki query agregasi COUNT yang sebelumnya dapat membuat endpoint katalog gagal. Lihat `dokumentasi/ROADMAP-2.0.md`.


## Nexora Cloud 2.1.0-alpha

Dashboard Cloud kini menampilkan statistik seluruh katalog dari backend. Ketuk kartu file untuk melihat metadata lengkap termasuk SHA-256, lalu unduh melalui antrean transfer. Detail fitur dan batasannya ada di `dokumentasi/ROADMAP-2.1.md`. Endpoint statistik baru: `GET /api/v1/stats`.


## Riwayat versi 2.2.0-alpha

Rilis ini menambahkan pratinjau gambar di dalam aplikasi serta membuka audio, video, PDF, dan format lain melalui aplikasi Android yang kompatibel. Lihat `dokumentasi/ROADMAP-2.2.md` untuk batasan fitur dan cara kerjanya.


## Versi 2.4.0-alpha
Mode seleksi beberapa file kini tersedia pada folder arsip tingkat jam. Pilih file satu per satu atau pilih semua file yang sudah dimuat, lalu masukkan masing-masing ke antrean WorkManager. Setiap file memiliki progres transfer tersendiri. Batas pilih-semua mengikuti file yang telah dimuat (maksimal 100 per halaman); lihat `dokumentasi/ROADMAP-2.3.md`.


## 2.4.0-alpha
Rilis ini menambahkan arsip terhapus aman di backend. Penghapusan hanya menandai metadata (`deleted_at`); berkas fisik Telegram tidak dihapus. Lihat `dokumentasi/ROADMAP-2.4.md`.

## Versi 2.5.0-alpha
Rilis ini menambahkan tab **Sampah**, pemulihan file dari aplikasi Android, serta tindakan memindahkan file cloud ke arsip terhapus dengan konfirmasi. File fisik di Telegram tetap tersimpan. Lihat `dokumentasi/ROADMAP-2.5.md`.


## Versi 2.6.0-alpha

Backend mencatat aktivitas upload, pemindahan ke Sampah, pemulihan, dan permintaan unduhan. Riwayat tersedia melalui `GET /api/v1/activity?limit=50` dengan API key. Lihat `dokumentasi/ROADMAP-2.6.md` untuk batasan dan kontraknya. UI Android riwayat aktivitas belum termasuk rilis ini.


## Versi 2.7.0-alpha

Riwayat aktivitas kini tersedia langsung di Android melalui tab **Aktivitas**, dengan filter Semua, Upload, Unduh, Hapus, dan Pulihkan. Backend `GET /api/v1/activity` mendukung filter `action` dan batas hingga 200 catatan. Catatan unduh tetap berarti permintaan dimulai, bukan konfirmasi transfer selesai. Lihat `dokumentasi/ROADMAP-2.7.md`.


## Versi 2.8.0-alpha
- Rekap kapasitas arsip per bulan dan tahun (zona WIB).
- Filter riwayat aktivitas berdasarkan rentang waktu ISO-8601.
- Endpoint ekspor CSV hingga 5.000 catatan dengan UTF-8 BOM.
- BackendClient Android menyediakan fungsi akses statistik dan ekspor.
- Grafik dan tombol ekspor pada UI Android masih menjadi pekerjaan tahap berikutnya.


## Versi 2.9.0-alpha

Pembaruan antarmuka statistik dan ekspor aktivitas:
- Kartu analitik kapasitas cloud dengan rekap file dan byte dari 24 periode bulanan yang dikembalikan backend.
- Visualisasi batang progres kapasitas per bulan (12 bulan terbaru) dan ringkasan tahunan.
- Ekspor aktivitas CSV langsung dari Android melalui pemilih lokasi penyimpanan.
- Filter tanggal opsional `YYYY-MM-DD`; tanggal akhir diperlakukan inklusif pada UI dan dikirim sebagai batas eksklusif hari berikutnya.
- Fungsi client hanya mengirim parameter tanggal yang diisi agar filter tanggal tunggal tetap valid.

Backend 2.8 sudah menyediakan endpoint yang dibutuhkan sehingga tidak ada perubahan skema database atau environment variables pada 2.9. Deploy ulang backend hanya diperlukan bila backend yang sedang berjalan belum memakai kode 2.8.


## Nexora Cloud 3.0.0-alpha — Fondasi Multi-Provider

- Menambahkan registry provider terpusat untuk Telegram, Google Drive, OneDrive, WebDAV, dan S3 Compatible.
- Memisahkan status integrasi dari preferensi provider yang dipilih pengguna.
- Mendefinisikan kemampuan provider agar fitur yang belum tersedia tidak ditampilkan seolah-olah aktif.
- Telegram tetap menggunakan backend yang sudah ada; API dan skema database tidak berubah.
- Google Drive, OneDrive, WebDAV, dan S3 masih berstatus rencana, bukan konektor aktif.

Lihat `dokumentasi/ROADMAP-3.0.md`.


## Nexora Cloud 3.1.0-alpha
Rilis ini menambahkan kontrak `CloudStorageProvider`, adapter Telegram, dan factory provider. Worker upload/download kini melewati kontrak yang sama. Telegram tetap satu-satunya provider aktif; Google Drive, OneDrive, WebDAV, dan S3 belum memiliki konektor operasional. Detail: `dokumentasi/ROADMAP-3.1.md`.

## Nexora 3.2.0-alpha
Menambahkan tombol Login pada header dan `LoginActivity` dengan alur nomor telepon serta halaman tautkan perangkat via QR. Saat ini ini adalah UI/navigasi saja; OTP dan QR belum terhubung ke backend autentikasi. Lihat `dokumentasi/ROADMAP-3.2.md`.


## Nexora 3.3.0-alpha
Fondasi autentikasi perangkat: QR pairing lima menit, persetujuan oleh API key admin, token perangkat sekali ambil, daftar/revoke perangkat, dan penyimpanan token Android melalui Android Keystore. Login OTP SMS belum aktif. Lihat `dokumentasi/ROADMAP-3.3.md` untuk alur pairing dan batasannya.


## 3.4.0-alpha — Telegram Account Login Preparation
Halaman login kini secara eksplisit membedakan login akun Telegram dari koneksi Telegram Bot API. Backend memeriksa kesiapan TELEGRAM_API_ID/HASH; autentikasi MTProto sebenarnya belum aktif pada rilis ini sehingga tombol login tidak berpura-pura berhasil. Lihat `dokumentasi/ROADMAP-3.4.md`.

## Nexora Cloud 3.5.0-alpha
- Menambahkan halaman Personalisasi dengan pilihan warna aksen.
- Preferensi disimpan di tabel SQLite `pengaturan` pada database lokal aplikasi.
- Database lokal mencakup katalog file, riwayat transfer, dan preferensi; backup database ikut membawa preferensi.
- Data berada di penyimpanan privat aplikasi, bukan ditanam di dalam APK. Data bertahan saat pembaruan aplikasi dengan package ID sama, tetapi dapat hilang jika aplikasi dihapus atau data aplikasi dibersihkan.
- Lihat `dokumentasi/ROADMAP-3.5.md` untuk detail dan batasan.

## Versi 3.6.0-alpha — Preferensi UI Aktif
- Urutan file bawaan kini diterapkan pada katalog lokal (terbaru, nama A–Z, ukuran terbesar).
- Mode ringkas kini memadatkan baris Berkas dan grid Galeri.
- Preferensi tetap tersimpan di database SQLite privat dan dapat dibawa melalui ekspor/impor konfigurasi non-rahasia.
- Lihat `dokumentasi/ROADMAP-3.6.md` untuk batasan dan uji manual.

### 3.7.0-alpha
- Preferensi bahasa Indonesia/Sunda dan notifikasi transfer ditambahkan ke penyimpanan pengaturan lokal.
- Kedua preferensi dimuat kembali saat aplikasi dibuka.
- Penerjemahan menyeluruh dan integrasi notifikasi sistem masih menjadi pekerjaan lanjutan.

## Versi 3.9.0-alpha — Privacy Center
- Ringkasan privasi dan lokasi penyimpanan data.
- Penjelasan alur data cloud, penyimpanan API key, dan kredensial Telegram di backend.
- Pembersihan cache sementara tanpa menghapus database atau file pengguna.
- Lihat `dokumentasi/ROADMAP-3.9.md`.


## Versi 4.0.0-alpha — Dasbor Cloud
- Halaman awal kini menjadi dasbor ringkasan dengan jumlah file lokal, jumlah file cloud, kapasitas cloud, dan jumlah media.
- Tombol perbarui mengambil statistik cloud dan lima aktivitas terbaru dari backend.
- Tautan cepat menuju pustaka lokal, Cloud, Storage, Galeri, dan Aktivitas.
- Data cloud tidak dibuat-buat: kartu statistik menampilkan tanda belum dimuat sampai backend berhasil merespons.
- Lihat `dokumentasi/ROADMAP-4.0.md`.

## Nexora Cloud 4.1.0-alpha — Akun & Perangkat

Versi ini menambahkan halaman diagnostik Akun & Perangkat dari Menu. Halaman menampilkan identitas perangkat dan versi aplikasi, jumlah file lokal/catatan transfer, serta tombol pemeriksaan koneksi backend dan status autentikasi akun Telegram. Implementasi menggunakan endpoint yang telah tersedia; belum mencakup daftar atau pencabutan sesi jarak jauh karena API backend tersebut belum tersedia. Lihat `dokumentasi/ROADMAP-4.1.md`.


## Nexora Cloud 4.2.0-alpha — Identitas & Riwayat Perangkat
- ID perangkat lokal UUID dan nama tampilan perangkat disimpan pada database privat aplikasi.
- Waktu aktivitas terakhir dicatat saat aplikasi dimulai.
- Halaman Akun & Perangkat menampilkan lima catatan transfer lokal terbaru.
- ID lokal tidak dikirim ke backend dan bukan kredensial autentikasi. Belum ada sinkronisasi lintas perangkat.
- Lihat `dokumentasi/ROADMAP-4.2.md`.

## Nexora Cloud 4.3.0-alpha — Cloud Sync Center

Halaman Transfer kini menampilkan ringkasan antrean Menunggu, Berjalan, Selesai, dan Gagal berdasarkan maksimal 100 catatan transfer lokal. Pekerjaan upload/download tetap dijalankan WorkManager. Versi ini belum mengaktifkan sinkronisasi dua arah otomatis atau penjadwalan berkala; lihat `dokumentasi/ROADMAP-4.3.md` untuk batasan.

## Nexora Cloud 4.4.0-alpha — Smart Archive Rules
- Pengaturan pola arsip berdasarkan tanggal (tahun/bulan/hari, tahun/bulan, tahun, atau datar) tersedia pada Personalisasi.
- Preferensi tersimpan di database lokal. Penerapan struktur folder Telegram menunggu dukungan API backend; versi ini tidak memindahkan file secara otomatis.

## Nexora Cloud 4.5.0-alpha — Smart File Manager
- Galeri mendukung pengurutan berdasarkan waktu, nama, dan ukuran.
- Pengelola Berkas mendukung filter kategori serta pengurutan daftar.
- Ringkasan jumlah/ukuran file dan peringatan kelompok duplikat berbasis SHA-256.
- Pemeriksaan duplikat bersifat informatif; tidak ada penghapusan otomatis.
- Cakupan katalog tetap file lokal yang sudah diindeks Nexora.


## Nexora Cloud 4.6.0-alpha — File Detail & Batch Operations
- Pengelola Berkas mendukung pemilihan beberapa item dan berbagi melalui Android Sharesheet.
- Detail file menampilkan metadata dasar dan menyediakan salin lokasi.
- Operasi batch tidak menghapus atau memindahkan file.
- Cakupan tetap katalog lokal Nexora; lihat `dokumentasi/ROADMAP-4.6.md`.

## Nexora Cloud 4.7.0-alpha

Versi ini menambahkan operasi file lokal yang dapat dipulihkan: ganti nama, pindahkan ke Sampah privat Nexora, lihat daftar Sampah, dan pulihkan file. Operasi hanya berlaku pada katalog `filesDir/berkas`; file cloud Telegram dan sumber SAF tidak diubah. Database lokal bermigrasi ke versi 7 dengan penambahan tabel `sampah`.
