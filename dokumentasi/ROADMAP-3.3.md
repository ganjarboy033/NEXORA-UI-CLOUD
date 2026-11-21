# Nexora Cloud 3.3.0-alpha — Device Pairing Backend

## Yang ditambahkan
- Endpoint pembuatan pairing sementara dengan masa berlaku lima menit dan pembatasan awal lima permintaan per alamat IP per menit.
- QR lokal Android menggunakan payload `nexora://pair?id=...&secret=...`. Rahasia pairing tidak disimpan mentah di database; backend menyimpan hash-nya.
- Endpoint persetujuan pairing dilindungi API key admin. Setelah disetujui, backend menerbitkan token perangkat deterministik berbasis HMAC; database hanya menyimpan SHA-256 token.
- Polling mengambil token satu kali. Token perangkat dapat dipakai sebagai Bearer untuk endpoint API dan dapat dicabut.
- Tabel `nexora_devices` dan `nexora_pairings` dibuat otomatis saat backend mulai.
- Android menyimpan token perangkat melalui SecretStore (Android Keystore) dan memeriksa status pairing berkala.

## Cara kerja pairing saat ini
1. Atur URL backend di Pengaturan Nexora, lalu buka Login > Login dengan kode QR pada perangkat baru.
2. QR berisi ID dan rahasia pairing yang berlaku lima menit.
3. Perangkat tepercaya/admin harus membaca payload QR dan memanggil endpoint `POST /api/v1/pairing/{id}/approve` dengan API key admin dan `pair_secret`. Contoh: `curl -X POST https://HOST/api/v1/pairing/ID/approve -H 'Authorization: Bearer API_KEY_ADMIN' -H 'Content-Type: application/json' -d '{"pair_secret":"SECRET_DARI_QR"}'`.
4. Perangkat baru melakukan polling; setelah disetujui, token perangkat diterima sekali dan disimpan terenkripsi.

## Batasan yang disengaja
- Ini adalah fondasi pairing perangkat, belum pengalaman WhatsApp satu-sentuhan: pemindai QR dan layar persetujuan perangkat tepercaya akan dibuat pada tahap berikutnya.
- Login nomor telepon/OTP belum aktif. Tidak ada SMS yang dikirim; integrasi OTP memerlukan penyedia SMS dan konfigurasi kredensial server.
- Endpoint pairing menggunakan API key admin sebagai otoritas persetujuan. Jangan pernah memasukkan API key admin ke APK publik.
- Gunakan HTTPS untuk backend publik. Rotasi API key admin akan membuat token perangkat lama tidak dapat diverifikasi karena token pairing diturunkan dari rahasia server tersebut.

## Pemeriksaan
- Migrasi database bersifat aditif dan otomatis.
- Tes backend Node dijalankan pada paket ini.
- Build APK harus diverifikasi melalui GitHub Actions pada lingkungan pengguna.
