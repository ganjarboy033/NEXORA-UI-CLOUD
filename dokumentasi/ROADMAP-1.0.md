# Nexora Cloud 1.0.0-alpha — Cloud Browser

## Perubahan
1. Tab Telegram Cloud di navigasi utama.
2. Membaca daftar metadata cloud melalui `GET /api/v1/files`.
3. Mengunduh berdasarkan ID melalui `GET /api/v1/files/:id/download`.
4. Menulis stream unduhan ke folder pilihan SAF, termasuk kartu SD/USB OTG selama Android dan perangkat memberikan izin tulis.
5. Penamaan file tujuan otomatis menghindari penimpaan file yang sudah ada.
6. Token Telegram tetap berada di backend; APK hanya memakai URL backend dan Nexora API Key.

## Cara mencoba
1. Deploy backend dan pastikan PostgreSQL serta Telegram terhubung.
2. Di aplikasi buka Storage, isi URL HTTPS dan Nexora API Key, lalu Simpan.
3. Pilih folder tujuan unduhan di Storage.
4. Buka tab Cloud lalu tekan Muat ulang daftar.
5. Tekan ikon unduh pada file yang diinginkan.

## Batasan yang diketahui
- Daftar cloud memuat hingga 100 entri per permintaan; pagination UI belum tersedia.
- Download berjalan sinkron di coroutine layar, bukan WorkManager; jangan tutup aplikasi saat unduhan berlangsung.
- Progress byte untuk unduhan cloud belum ditampilkan.
- Backend masih memakai satu API key bersama, belum multi-user.
- Telegram Bot API dan batas deployment tetap berlaku; ini bukan cloud storage tanpa batas.

## Tahap berikutnya: Configuration Center
- Wizard konfigurasi backend dan status tiap provider.
- Profil konfigurasi yang bisa dinamai dan diganti.
- Export/import konfigurasi dengan enkripsi password (AES-GCM); default ekspor tidak boleh menyertakan rahasia dalam bentuk plaintext.
- Pengelolaan endpoint/provider modular.
- Secret Manager server untuk token Telegram, kredensial PostgreSQL, dan API key provider.
- Backup/restore metadata PostgreSQL dan database lokal dengan validasi versi serta checksum.

Konfigurasi bootstrap yang diperlukan agar backend pertama kali hidup (DATABASE_URL, TELEGRAM_BOT_TOKEN, TELEGRAM_STORAGE_CHAT_ID, NEXORA_API_KEY) tetap disiapkan melalui environment/secret manager hosting. Setelah aktif, pengaturan operasional dapat dikelola dari Control Center tanpa menanam token Telegram ke APK.
