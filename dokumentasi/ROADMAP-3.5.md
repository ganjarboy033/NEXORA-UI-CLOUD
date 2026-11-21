# Nexora Cloud 3.5.0-alpha — Personalization & Local App Database

## Tujuan
Membuat preferensi tampilan dan perilaku Nexora dapat dikustomisasi dan tersimpan secara lokal pada perangkat, bukan hanya hidup selama layar terbuka.

## Perubahan Android
- Versi aplikasi dinaikkan ke `3.5.0-alpha` (`versionCode 36`).
- Menambah tabel SQLite `pengaturan` melalui migrasi database versi 6.
- Menambah API `getSetting`, `setSetting`, dan `allSettings` pada `LocalFileDatabase`.
- Menambah halaman **Personalisasi** di navigasi utama.
- Pilihan warna aksen (7 preset) tersimpan di SQLite dan diterapkan ke Material 3 `primary`/`secondary`.
- Preferensi mode ringkas, konfirmasi penghapusan, dan urutan file bawaan disimpan di SQLite sebagai fondasi perilaku UI berikutnya.
- Backup database lokal yang sudah tersedia mencakup tabel preferensi baru.

## Apa yang tersimpan di mana
- File impor: `filesDir/berkas`.
- Katalog file, antrean transfer, dan preferensi: database privat `nexora_local.db`.
- Rahasia seperti API key: penyimpanan terenkripsi `SecretStore` dengan Android Keystore.
- Preferensi bertahan saat update APK selama package ID tetap sama dan data aplikasi tidak dihapus.

## Batasan yang sengaja dijelaskan
- Database tidak ditanam ke dalam berkas APK. Database dibuat di ruang privat aplikasi pada perangkat saat pertama kali dijalankan; ini memungkinkan data berubah dan disimpan tanpa membangun ulang APK.
- Menghapus aplikasi atau memilih hapus data aplikasi dapat menghapus database lokal. Gunakan fitur backup untuk menyimpan salinan.
- Mode ringkas, konfirmasi penghapusan, dan urutan file saat ini sudah disimpan; penerapan perilakunya ke seluruh layar akan dilakukan bertahap.
- Autentikasi akun Telegram MTProto belum aktif pada rilis ini. Fitur login Telegram memerlukan implementasi Client API tersendiri; penyimpanan lokal bukan pengganti sesi Telegram.

## Pemeriksaan
- Migrasi SQLite: versi 5 ke 6 menambah tabel pengaturan tanpa menghapus tabel lama.
- ZIP diuji dengan `unzip -t`.
- Build APK Android perlu diverifikasi lewat GitHub Actions.

## Tahap berikutnya 3.6
- Terapkan pilihan urutan file dan mode ringkas pada layar Berkas/Galeri.
- Tambah ekspor/impor preferensi personalisasi ke berkas konfigurasi.
- Tambah pengaturan bahasa dan pengelolaan backup otomatis.
