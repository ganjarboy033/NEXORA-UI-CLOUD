# Nexora Cloud 3.2.0-alpha — Login & Device Linking UI

## Yang ditambahkan
- Tombol **Login** pada header aplikasi.
- `LoginActivity` terpisah dengan tampilan gelap yang mengikuti referensi layar login perangkat tertaut.
- Form nomor telepon dengan kode negara awal +62 dan validasi input dasar.
- Halaman **Login dengan kode QR** sebagai alur tautkan perangkat.
- Penjelasan status yang jujur: OTP dan QR belum melakukan autentikasi karena backend belum menyediakan endpoint sesi, OTP, dan pairing.

## Batasan penting
Versi ini adalah implementasi antarmuka dan navigasi login, bukan sistem autentikasi aktif. Tombol lanjut tidak mengirim nomor telepon, tidak mengirim OTP, dan tidak membuat sesi. Halaman QR tidak membuat QR palsu. Ini mencegah pengguna mengira akun sudah terhubung padahal belum.

## Tahap berikutnya: 3.3
- Desain skema akun dan sesi perangkat pada backend.
- Endpoint permintaan/verifikasi OTP dengan pembatasan percobaan dan masa berlaku.
- Pairing QR berumur pendek, sekali pakai, dan konfirmasi dari perangkat yang sudah login.
- Penyimpanan token sesi aman dan logout/revoke perangkat.
- Uji keamanan dan integrasi end-to-end sebelum tombol login diaktifkan.

## Pemeriksaan
- ZIP diperiksa setelah dibuat.
- Build Gradle harus dijalankan melalui GitHub Actions; build APK belum dinyatakan berhasil sampai workflow selesai.
