# Nexora Cloud 3.0.0-alpha — Fondasi Multi-Provider

## Tujuan tahap
Menyiapkan kontrak identitas, status integrasi, dan kemampuan provider agar penambahan cloud berikutnya tidak mencampur logika provider dengan UI atau Telegram backend.

## Implementasi pada tahap ini
- Menambahkan `CloudProviderId` sebagai identitas baku Telegram, Google Drive, OneDrive, WebDAV, dan S3 Compatible.
- Menambahkan `ProviderIntegrationStatus` agar status implementasi tidak disamakan dengan preferensi aktif pengguna.
- Menambahkan `ProviderCapabilities` untuk mendeskripsikan operasi yang benar-benar tersedia.
- Menambahkan `CloudProviderDescriptor` dan `CloudProviderRegistry` sebagai katalog pusat provider.
- Mempertahankan alur Telegram yang sudah ada; endpoint API dan skema database tidak diubah.
- Menaikkan versi Android menjadi 3.0.0-alpha / versionCode 30.

## Status provider
| Provider | Status | Catatan |
|---|---|---|
| Telegram Cloud | Implemented | Operasi yang sudah tersedia tetap melalui backend Nexora. |
| Google Drive | Planned | Belum ada OAuth atau operasi file. |
| OneDrive | Planned | Belum ada Microsoft Graph/OAuth. |
| WebDAV | Planned | Belum ada konektor atau penyimpanan kredensial. |
| S3 Compatible | Planned | Belum ada konektor atau pengelolaan secret. |

## Keamanan dan batasan
- Registry ini hanya metadata arsitektur; bukan konektor cloud dan tidak membuat provider palsu seolah-olah terhubung.
- Jangan menyimpan access token, refresh token, password WebDAV, atau secret S3 di SharedPreferences biasa, JSON ekspor, log, maupun APK.
- Implementasi OAuth berikutnya harus memakai alur otorisasi resmi dan penyimpanan token yang aman.
- Tidak ada sinkronisasi lintas-provider atau penghapusan otomatis pada tahap ini.

## Pengujian yang perlu dilakukan
1. Build APK melalui GitHub Actions.
2. Pastikan Telegram Cloud masih dapat dites, memuat katalog, upload, download, memulihkan file, dan mengekspor aktivitas.
3. Pastikan provider yang belum tersedia tetap ditandai belum terintegrasi.
4. Pastikan ekspor konfigurasi tidak memasukkan kredensial rahasia.

## Tahap berikutnya
Nexora 3.1 akan memisahkan kontrak operasi file (`list`, `upload`, `download`, `delete`, `restore`) dari implementasi Telegram, kemudian menghubungkan Telegram ke kontrak tersebut tanpa mengubah perilaku API yang sudah berjalan.


## Hotfix 3.0.1-alpha — kompilasi DatePicker

Log build 3.0.0-alpha menunjukkan kompilasi Kotlin berhenti pada pemakaian `rememberDatePickerState`, `DatePickerDialog`, dan `DatePicker` di `StorageScreen`. Ketiganya memerlukan opt-in API eksperimental Material 3. Fungsi `StorageScreen` kini diberi `@OptIn(ExperimentalMaterial3Api::class)`. Tidak ada perubahan perilaku UI atau endpoint backend.
