# Nexora Cloud 3.7.0-alpha — Preferensi Personal

## Perubahan
- Preferensi bahasa `id` (Indonesia) dan `su` (Sunda) ditambahkan ke tabel `pengaturan`.
- Preferensi notifikasi transfer ditambahkan dan disimpan lokal.
- Nilai preferensi dimuat saat aplikasi dibuka dan dapat diubah dari Personalisasi.
- Skema database tidak berubah; migrasi database tidak diperlukan.

## Batasan yang dinyatakan
- Pilihan bahasa pada rilis ini baru menyimpan preferensi; seluruh teks layar belum diterjemahkan.
- Sakelar notifikasi saat ini menyimpan pilihan pengguna; belum mengubah konfigurasi notifikasi WorkManager/Android.
- Backup database penuh dan ekspor konfigurasi tetap menjadi jalur cadangan; jangan memasukkan token Telegram atau API key ke berkas konfigurasi yang dapat dibagikan.

## Pemeriksaan
- Periksa struktur ZIP dan konsistensi versi.
- Build Gradle harus diverifikasi melalui GitHub Actions sebelum dianggap lulus.


Tahap berikutnya: 3.8.0-alpha — tema, notifikasi transfer Android, dan pusat pemulihan database.
