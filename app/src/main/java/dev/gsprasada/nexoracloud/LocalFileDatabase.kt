package dev.gsprasada.nexoracloud

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File
import java.security.MessageDigest

/** Indeks metadata lokal. File tetap disimpan di filesDir/berkas; database hanya menyimpan katalog. */
class LocalFileDatabase(context: Context) : SQLiteOpenHelper(context, "nexora_local.db", null, 8) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE berkas (
                path TEXT PRIMARY KEY,
                nama TEXT NOT NULL,
                ukuran INTEGER NOT NULL,
                waktu_modifikasi INTEGER NOT NULL,
                waktu_impor INTEGER NOT NULL,
                mime TEXT NOT NULL,
                sha256 TEXT NOT NULL,
                sumber TEXT NOT NULL DEFAULT 'lokal'
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_berkas_waktu ON berkas(waktu_modifikasi DESC)")
        db.execSQL("CREATE INDEX idx_berkas_hash ON berkas(sha256)")
        db.execSQL("CREATE TABLE favorit (path TEXT PRIMARY KEY, dibuat INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE sampah (id INTEGER PRIMARY KEY AUTOINCREMENT, nama_asli TEXT NOT NULL, path_sampah TEXT NOT NULL UNIQUE, waktu_hapus INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE pengaturan (kunci TEXT PRIMARY KEY, nilai TEXT NOT NULL, diperbarui INTEGER NOT NULL)")
        db.execSQL("""CREATE TABLE transfer (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            nama TEXT NOT NULL,
            arah TEXT NOT NULL,
            status TEXT NOT NULL,
            dibuat INTEGER NOT NULL,
            selesai INTEGER,
            pesan TEXT NOT NULL DEFAULT '',
            progres INTEGER NOT NULL DEFAULT 0,
            byte_selesai INTEGER NOT NULL DEFAULT 0,
            byte_total INTEGER NOT NULL DEFAULT 0,
            work_id TEXT NOT NULL DEFAULT ''
        )""")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("""CREATE TABLE IF NOT EXISTS transfer (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                nama TEXT NOT NULL,
                arah TEXT NOT NULL,
                status TEXT NOT NULL,
                dibuat INTEGER NOT NULL,
                selesai INTEGER,
                pesan TEXT NOT NULL DEFAULT ''
            )""")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE transfer ADD COLUMN progres INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE transfer ADD COLUMN byte_selesai INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE transfer ADD COLUMN byte_total INTEGER NOT NULL DEFAULT 0")
        }
        if (oldVersion < 4) db.execSQL("ALTER TABLE transfer ADD COLUMN work_id TEXT NOT NULL DEFAULT ''")
        if (oldVersion < 5) db.execSQL("CREATE INDEX IF NOT EXISTS idx_transfer_status ON transfer(status, dibuat DESC)")
        if (oldVersion < 6) db.execSQL("CREATE TABLE IF NOT EXISTS pengaturan (kunci TEXT PRIMARY KEY, nilai TEXT NOT NULL, diperbarui INTEGER NOT NULL)")
        if (oldVersion < 7) db.execSQL("CREATE TABLE IF NOT EXISTS sampah (id INTEGER PRIMARY KEY AUTOINCREMENT, nama_asli TEXT NOT NULL, path_sampah TEXT NOT NULL UNIQUE, waktu_hapus INTEGER NOT NULL)")
        if (oldVersion < 8) db.execSQL("CREATE TABLE IF NOT EXISTS favorit (path TEXT PRIMARY KEY, dibuat INTEGER NOT NULL)")
    }

    /** Preferensi aplikasi disimpan di SQLite privat agar ikut masuk backup database. */
    @Synchronized fun getSetting(key: String, defaultValue: String = ""): String =
        readableDatabase.rawQuery("SELECT nilai FROM pengaturan WHERE kunci=?", arrayOf(key)).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else defaultValue
        }

    @Synchronized fun setSetting(key: String, value: String) {
        val values = ContentValues().apply {
            put("kunci", key); put("nilai", value); put("diperbarui", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict("pengaturan", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    @Synchronized fun allSettings(): Map<String, String> {
        val result = linkedMapOf<String, String>()
        readableDatabase.rawQuery("SELECT kunci,nilai FROM pengaturan ORDER BY kunci", null).use { cursor ->
            while (cursor.moveToNext()) result[cursor.getString(0)] = cursor.getString(1)
        }
        return result
    }

    @Synchronized fun addTransfer(name: String, direction: String): Long {
        val values = ContentValues().apply {
            put("nama", name); put("arah", direction); put("status", "Menunggu")
            put("dibuat", System.currentTimeMillis()); put("pesan", "Dalam antrean")
        }
        return writableDatabase.insert("transfer", null, values)
    }

    @Synchronized fun attachWork(id: Long, workId: String) {
        val values = ContentValues().apply { put("work_id", workId) }
        writableDatabase.update("transfer", values, "id=?", arrayOf(id.toString()))
    }

    @Synchronized fun workId(id: Long): String? = readableDatabase.rawQuery(
        "SELECT work_id FROM transfer WHERE id=?", arrayOf(id.toString())
    ).use { if (it.moveToFirst()) it.getString(0).takeIf { value -> value.isNotBlank() } else null }

    @Synchronized fun updateTransfer(id: Long, status: String, message: String) {
        val values = ContentValues().apply {
            put("status", status); put("pesan", message)
            if (status == "Selesai" || status == "Gagal" || status == "Dibatalkan") put("selesai", System.currentTimeMillis())
        }
        writableDatabase.update("transfer", values, "id=?", arrayOf(id.toString()))
    }

    @Synchronized fun updateTransferProgress(id: Long, copied: Long, total: Long) {
        val safeTotal = total.coerceAtLeast(0L)
        val percent = if (safeTotal == 0L) 0 else ((copied * 100L) / safeTotal).toInt().coerceIn(0, 100)
        val values = ContentValues().apply {
            put("status", "Berjalan"); put("progres", percent)
            put("byte_selesai", copied); put("byte_total", safeTotal)
            put("pesan", "$percent% • $copied / $safeTotal byte")
        }
        writableDatabase.update("transfer", values, "id=?", arrayOf(id.toString()))
    }

    /** Hapus hanya riwayat terminal; pekerjaan aktif tidak disentuh. */
    @Synchronized fun clearFinishedTransfers(): Int = writableDatabase.delete(
        "transfer", "status IN (?, ?, ?)", arrayOf("Selesai", "Gagal", "Dibatalkan")
    )

    @Synchronized fun listTransfers(): List<TransferRecord> {
        val result = mutableListOf<TransferRecord>()
        readableDatabase.rawQuery("SELECT id,nama,arah,status,dibuat,pesan,progres,byte_selesai,byte_total,work_id FROM transfer ORDER BY id DESC LIMIT 100", null).use { c ->
            while (c.moveToNext()) result += TransferRecord(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getLong(4), c.getString(5), c.getInt(6), c.getLong(7), c.getLong(8), c.getString(9))
        }
        return result
    }

    @Synchronized fun index(file: File, importedAt: Long = System.currentTimeMillis()) {
        if (!file.isFile) return
        val values = ContentValues().apply {
            put("path", file.absolutePath)
            put("nama", file.name)
            put("ukuran", file.length())
            put("waktu_modifikasi", file.lastModified())
            put("waktu_impor", importedAt)
            put("mime", mimeFor(file.name))
            put("sha256", sha256(file))
            put("sumber", "lokal")
        }
        writableDatabase.insertWithOnConflict("berkas", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    @Synchronized fun listFiles(): List<LocalItem> {
        val result = mutableListOf<LocalItem>()
        readableDatabase.rawQuery("SELECT nama, ukuran, waktu_modifikasi, path FROM berkas ORDER BY waktu_modifikasi DESC", null).use { cursor ->
            while (cursor.moveToNext()) {
                val file = File(cursor.getString(3))
                if (file.isFile) result += LocalItem(cursor.getString(0), cursor.getLong(1), cursor.getLong(2), cursor.getString(3))
            }
        }
        return result
    }

    @Synchronized fun reconcile(directory: File) {
        val seen = HashSet<String>()
        directory.listFiles()?.filter { it.isFile }?.forEach { file ->
            seen += file.absolutePath
            val exists = readableDatabase.rawQuery("SELECT 1 FROM berkas WHERE path=?", arrayOf(file.absolutePath)).use { it.moveToFirst() }
            if (!exists) index(file)
        }
        writableDatabase.rawQuery("SELECT path FROM berkas", null).use { cursor ->
            val stale = mutableListOf<String>()
            while (cursor.moveToNext()) if (cursor.getString(0) !in seen) stale += cursor.getString(0)
            stale.forEach { writableDatabase.delete("berkas", "path=?", arrayOf(it)); writableDatabase.delete("favorit", "path=?", arrayOf(it)) }
        }
    }

    /** Favorit hanya menyimpan path di pustaka privat Nexora. */
    @Synchronized fun favoritePaths(): Set<String> {
        val result = linkedSetOf<String>()
        readableDatabase.rawQuery("SELECT path FROM favorit", null).use { c -> while (c.moveToNext()) result += c.getString(0) }
        return result
    }

    @Synchronized fun toggleFavorite(path: String, library: File): Boolean {
        val file = File(path).canonicalFile
        require(file.parentFile == library.canonicalFile && file.isFile) { "Hanya berkas di pustaka lokal yang dapat dijadikan favorit." }
        val exists = readableDatabase.rawQuery("SELECT 1 FROM favorit WHERE path=?", arrayOf(file.absolutePath)).use { it.moveToFirst() }
        if (exists) writableDatabase.delete("favorit", "path=?", arrayOf(file.absolutePath))
        else {
            val values = ContentValues().apply { put("path", file.absolutePath); put("dibuat", System.currentTimeMillis()) }
            writableDatabase.insertWithOnConflict("favorit", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        }
        return !exists
    }

    /** Peta path -> SHA-256 seluruh berkas terindeks (untuk deteksi duplikat). */
    @Synchronized fun listHashes(): Map<String, String> {
        val result = HashMap<String, String>()
        readableDatabase.rawQuery("SELECT path, sha256 FROM berkas", null).use { cursor ->
            while (cursor.moveToNext()) result[cursor.getString(0)] = cursor.getString(1) ?: ""
        }
        return result
    }

    fun duplicateGroupCount(): Int = readableDatabase.rawQuery(
        "SELECT COUNT(*) FROM (SELECT sha256 FROM berkas GROUP BY sha256 HAVING COUNT(*) > 1)", null
    ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }


    /** Operasi dibatasi pada direktori privat filesDir/berkas milik Nexora. */
    @Synchronized fun renameLocalFile(path: String, newName: String, library: File): Boolean {
        val source = File(path).canonicalFile
        val base = library.canonicalFile
        require(source.parentFile == base && source.isFile) { "Berkas bukan bagian dari pustaka lokal Nexora." }
        val safe = newName.trim()
        require(safe.isNotBlank() && safe != "." && safe != ".." && !safe.contains('/') && !safe.contains('\\')) { "Nama berkas tidak valid." }
        val target = File(base, safe).canonicalFile
        require(target.parentFile == base && !target.exists()) { "Nama sudah digunakan atau lokasi tidak valid." }
        if (!source.renameTo(target)) return false
        val wasFavorite = readableDatabase.rawQuery("SELECT 1 FROM favorit WHERE path=?", arrayOf(source.absolutePath)).use { it.moveToFirst() }
        writableDatabase.delete("favorit", "path=?", arrayOf(source.absolutePath))
        writableDatabase.delete("berkas", "path=?", arrayOf(source.absolutePath))
        index(target)
        if (wasFavorite) { val favorite = ContentValues().apply { put("path", target.absolutePath); put("dibuat", System.currentTimeMillis()) }; writableDatabase.insertWithOnConflict("favorit", null, favorite, SQLiteDatabase.CONFLICT_REPLACE) }
        return true
    }

    @Synchronized fun moveToTrash(path: String, library: File, trashDirectory: File): Boolean {
        val source = File(path).canonicalFile
        val base = library.canonicalFile
        require(source.parentFile == base && source.isFile) { "Hanya berkas pustaka lokal yang dapat dipindahkan ke Sampah." }
        trashDirectory.mkdirs()
        val target = File(trashDirectory, "${System.currentTimeMillis()}_${source.name}").canonicalFile
        if (!source.renameTo(target)) return false
        val values = ContentValues().apply { put("nama_asli", source.name); put("path_sampah", target.absolutePath); put("waktu_hapus", System.currentTimeMillis()) }
        writableDatabase.insertOrThrow("sampah", null, values)
        writableDatabase.delete("favorit", "path=?", arrayOf(source.absolutePath))
        writableDatabase.delete("berkas", "path=?", arrayOf(source.absolutePath))
        return true
    }

    @Synchronized fun listTrash(): List<TrashRecord> {
        val result = mutableListOf<TrashRecord>()
        readableDatabase.rawQuery("SELECT id,nama_asli,path_sampah,waktu_hapus FROM sampah ORDER BY waktu_hapus DESC", null).use { c ->
            while (c.moveToNext()) if (File(c.getString(2)).isFile) result += TrashRecord(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3))
        }
        return result
    }

    @Synchronized fun restoreFromTrash(id: Long, library: File): Boolean {
        val row = readableDatabase.rawQuery("SELECT nama_asli,path_sampah FROM sampah WHERE id=?", arrayOf(id.toString())).use { c -> if (c.moveToFirst()) c.getString(0) to c.getString(1) else null } ?: return false
        val base = library.canonicalFile.apply { mkdirs() }
        val source = File(row.second).canonicalFile
        require(source.isFile && source.parentFile == File(base.parentFile, "sampah").canonicalFile) { "Lokasi sampah tidak valid." }
        var target = File(base, row.first)
        if (target.exists()) target = File(base, "${System.currentTimeMillis()}_${row.first}")
        if (!source.renameTo(target)) return false
        writableDatabase.delete("sampah", "id=?", arrayOf(id.toString()))
        index(target)
        return true
    }

    private fun sha256(file: File): String = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrDefault("")

    private fun mimeFor(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }
}


data class TransferRecord(val id: Long, val name: String, val direction: String, val status: String, val createdAt: Long, val message: String, val progress: Int, val bytesDone: Long, val bytesTotal: Long, val workId: String)


data class TrashRecord(val id: Long, val originalName: String, val path: String, val deletedAt: Long)
