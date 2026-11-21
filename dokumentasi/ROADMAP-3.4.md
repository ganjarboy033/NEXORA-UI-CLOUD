# Nexora Cloud 3.4.0-alpha — Telegram Account Login Preparation

## Koreksi konsep autentikasi
Nexora menyimpan berkas melalui Telegram Bot API saat ini. Login akun Telegram pribadi adalah mekanisme berbeda dan menggunakan Telegram Client API/MTProto. Token bot tidak dapat dipakai untuk menerima kode login akun pribadi atau membuat QR login Telegram.

## Perubahan
- Halaman login diubah label dan penjelasannya menjadi Telegram Login.
- Android memeriksa `GET /api/v1/telegram/account-auth/status`.
- Backend mengenali konfigurasi opsional `TELEGRAM_API_ID` dan `TELEGRAM_API_HASH`.
- Tombol login dinonaktifkan selama mesin autentikasi belum siap; tidak dibuat OTP/QR palsu.
- QR pairing Nexora terdahulu tidak lagi dipresentasikan sebagai QR login Telegram.

## Konfigurasi server
Tambahkan `TELEGRAM_API_ID` dan `TELEGRAM_API_HASH` dari `https://my.telegram.org/apps` ke environment backend. Jangan masukkan ke APK atau repositori publik. Pada rilis 3.4 ini, status hanya memeriksa ketersediaan konfigurasi; alur MTProto phone-code, 2FA, QR, dan penyimpanan sesi belum diimplementasikan.

## Arsitektur saat ini
- Telegram Bot API: upload dan download arsip Nexora yang sudah ada.
- Telegram Client API: target autentikasi akun Telegram pada tahap berikutnya.
- Nexora device pairing: autentikasi perangkat aplikasi yang terpisah, bukan autentikasi Telegram.

## Pemeriksaan
Jalankan `npm test` di folder backend. Build Android perlu diverifikasi melalui GitHub Actions.

## Tahap 3.5
Implementasi mesin Telegram Client API dengan alur nomor telepon, kode Telegram, kata sandi verifikasi dua langkah bila diminta, QR login, penyimpanan sesi terenkripsi, dan logout sesi. Sebelum itu, tentukan apakah sesi Telegram menjadi per pengguna atau satu akun penyimpanan bersama.
