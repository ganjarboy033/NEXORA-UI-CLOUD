# Nexora Cloud 3.8.0-alpha — Personal Control Center

## Perubahan
- Tema terang/gelap disimpan pada tabel `pengaturan` dan diterapkan ke warna dasar aplikasi, navigasi, serta permukaan kartu.
- Preferensi bahasa Indonesia/Sunda tetap kompatibel; label navigasi dan bagian utama personalisasi kini memiliki terjemahan awal. Terjemahan seluruh pesan dan layar belum selesai.
- Notifikasi hasil transfer memakai kanal Android `Transfer Nexora`. Notifikasi hanya dikirim bila preferensi aktif; Android 13+ meminta izin `POST_NOTIFICATIONS` saat pengguna mengaktifkannya.
- Pusat backup menambahkan pemulihan database melalui pemilih dokumen. File diperiksa dengan header SQLite sebelum database lokal diganti.
- Package ID tetap `dev.gsprasada.nexoracloud`; database versi 6 tidak berubah; preferensi lama tetap dipertahankan.

## Catatan
- Jika izin notifikasi ditolak dari dialog Android, pengguna dapat mengaktifkannya dari pengaturan sistem Android.
- Pemulihan database mengganti katalog, pengaturan, dan riwayat transfer lokal dengan isi backup. File media di folder privat aplikasi tidak otomatis dipulihkan oleh backup database.
- Tema gelap masih dalam tahap penyelarasan visual; beberapa dialog/komponen lama mungkin masih perlu penyesuaian.
- Build APK harus diverifikasi melalui GitHub Actions. Jangan menganggap APK sudah berhasil sebelum workflow selesai.
