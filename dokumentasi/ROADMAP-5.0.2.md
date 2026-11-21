# Nexora Cloud 5.0.2-alpha — Telegram App Configuration

## Konfigurasi yang dapat diubah
- App Title, Short Name, Platform, URL aplikasi.
- Telegram App `api_id` dan `api_hash`.
- MTProto DC 2 untuk Test dan Production (`host:port`).
- Tombol simpan, validasi dasar, dan reset ke nilai bawaan Nexora Cloud.

## Penyimpanan dan keamanan
- Nilai konfigurasi disimpan privat di perangkat.
- `api_hash` dienkripsi dengan AES-GCM menggunakan kunci Android Keystore melalui `SecretStore`.
- `api_hash` tidak dimasukkan ke ekspor konfigurasi JSON.
- Reset hanya mengembalikan profil konfigurasi Telegram; tidak menghapus database, file, backend URL, atau akun.

## Batas integrasi
Halaman ini merupakan penyimpanan profil konfigurasi di sisi aplikasi. Backend Telegram Client API belum otomatis membaca nilai ini; konfigurasi runtime backend masih perlu diatur terpisah. Jangan menganggap perubahan DC/API di layar ini telah mengubah koneksi jaringan sebelum adapter autentikasi mengonsumsinya.

## Validasi
- `api_id` harus angka.
- `api_hash` harus 32 karakter heksadesimal.
- URL harus memakai skema HTTP/HTTPS dan memiliki host.
- Alamat DC harus `host:port` dengan port 1–65535.
