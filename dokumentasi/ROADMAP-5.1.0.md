# Nexora Cloud 5.1.0-alpha — Telegram Client Foundation

## Tujuan
Telegram menjadi provider cloud pertama. Pengguna diarahkan untuk masuk dengan akun Telegram pribadi; provider cloud lain ditunda.

## Komponen yang dimasukkan
- Source TDLib resmi yang diberikan, berada di `third_party/tdlib`.
- Android build scripts resmi TDLib.
- Pipeline GitHub Actions untuk SDK/NDK, OpenSSL, TDLib Java/JNI, lalu APK.
- ABI target: `arm64-v8a` dan `x86_64`.
- BuildConfig dari GitHub Actions Secrets (`TELEGRAM_API_ID`, `TELEGRAM_API_HASH`).

## Batas implementasi versi ini
Ini adalah integrasi source + toolchain native. Belum boleh dianggap login sudah selesai hanya karena `tdjni` berhasil dibangun. Login production masih memerlukan implementasi dan pengujian state machine TDLib (parameter, nomor, kode Telegram, 2FA, QR confirmation, sesi, logout) serta pengaitan akun Telegram dengan indeks/pustaka Nexora.

## Alur target autentikasi
1. Inisialisasi satu instance TDLib pada direktori privat aplikasi.
2. Kirim `SetTdlibParameters` otomatis ketika `AuthorizationStateWaitTdlibParameters`.
3. Tangani `AuthorizationStateWaitPhoneNumber`, `AuthorizationStateWaitCode`, dan `AuthorizationStateWaitPassword`.
4. Untuk QR, panggil `RequestQrCodeAuthentication`; tampilkan link `tg://login?...` sebagai QR.
5. Pantau `UpdateAuthorizationState` dan arahkan ke beranda setelah `AuthorizationStateReady`.
6. Sesi disimpan TDLib di penyimpanan privat aplikasi; jangan mengekspor database sesi.
7. Sediakan logout melalui `LogOut` lalu `Close`.

## Catatan keamanan dan operasional
- Minta pengguna hanya memasukkan nomor sendiri dan kode yang dikirim Telegram.
- Jangan pernah meminta atau mengirim OTP/2FA ke backend Nexora.
- API Hash yang dikompilasi ke APK dapat diekstrak; gunakan API ID/Hash aplikasi sendiri dan patuhi ketentuan Telegram.
- Jangan log nomor lengkap, OTP, password 2FA, auth key, atau isi database TDLib.
- Build CI memakai source yang disertakan, bukan mengambil versi terbaru tanpa pinning.
