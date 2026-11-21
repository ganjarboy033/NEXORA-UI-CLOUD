# Nexora Cloud 2.1.0-alpha

## Fokus
Memberi gambaran katalog cloud secara menyeluruh dan menyediakan panel detail metadata per file.

## Perubahan
1. Backend menambahkan `GET /api/v1/stats` untuk total file, total byte, jumlah gambar/video/audio/dokumen, dan waktu unggahan terbaru.
2. Android menampilkan statistik dari seluruh katalog server, bukan hanya halaman yang sedang dimuat.
3. Kartu file dapat diketuk untuk membuka detail: ID cloud, nama, MIME, ukuran byte, waktu, jalur arsip virtual, dan SHA-256.
4. Tombol Unduh tersedia dari panel detail dan tetap memakai antrean WorkManager yang sudah ada.
5. Versi Android/backend menjadi `2.1.0-alpha`, versionCode `21`.

## Batasan
- Panel detail hanya menampilkan metadata. Pratinjau gambar/video/audio langsung dari cloud belum tersedia.
- Statistik mengikuti seluruh isi tabel `cloud_files`, tidak mengikuti filter pencarian yang sedang aktif.
- Folder virtual masih dibentuk dari metadata waktu; bukan direktori fisik di Telegram.
- Pagination masih berbasis offset.
- Autentikasi masih satu API key backend; belum multi-pengguna.

## Pemeriksaan
- `node --check backend/src/server.js`
- `npm test` pada backend.
- `unzip -t` pada paket ZIP.
- APK harus dibangun dan diverifikasi melalui GitHub Actions `Build Nexora Cloud APK`; build lokal tidak tersedia jika Gradle belum terpasang.
