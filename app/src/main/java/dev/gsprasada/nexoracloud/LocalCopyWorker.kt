package dev.gsprasada.nexoracloud

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.Data
import java.io.File

/** Menyalin file pustaka ke folder SAF menggunakan WorkManager agar antrean tahan proses aplikasi ditutup. */
class LocalCopyWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val transferId = inputData.getLong(KEY_TRANSFER_ID, -1L)
        val sourcePath = inputData.getString(KEY_SOURCE_PATH) ?: return fail(transferId, "Lokasi sumber tidak tersedia")
        val treeString = inputData.getString(KEY_TREE_URI) ?: return fail(transferId, "Folder tujuan tidak tersedia")
        val source = File(sourcePath)
        if (!source.isFile) return fail(transferId, "File sumber sudah tidak tersedia")
        val db = LocalFileDatabase(applicationContext)
        return try {
            db.updateTransfer(transferId, "Berjalan", "Menyiapkan transfer")
            val treeUri = Uri.parse(treeString)
            val folder = DocumentFile.fromTreeUri(applicationContext, treeUri)
                ?: return fail(transferId, "Folder tujuan tidak dapat dibuka")
            if (!folder.canWrite()) return fail(transferId, "Folder tujuan tidak memiliki izin tulis")
            val ext = source.extension.lowercase()
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
            val outputName = uniqueName(folder, source.name)
            val output = folder.createFile(mime, outputName) ?: return fail(transferId, "Gagal membuat file tujuan")
            try {
                val total = source.length().coerceAtLeast(1L)
                var copied = 0L
                source.inputStream().buffered().use { input ->
                    applicationContext.contentResolver.openOutputStream(output.uri, "w")?.buffered()?.use { out ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            if (isStopped) throw InterruptedException("Transfer dibatalkan")
                            val count = input.read(buffer)
                            if (count < 0) break
                            out.write(buffer, 0, count)
                            copied += count
                            val percent = ((copied * 100L) / total).toInt().coerceIn(0, 100)
                            setProgress(Data.Builder().putInt(KEY_PROGRESS, percent).putLong(KEY_COPIED, copied).putLong(KEY_TOTAL, total).build())
                            if (percent % 2 == 0 || copied == total) db.updateTransferProgress(transferId, copied, total)
                        }
                        out.flush()
                    } ?: throw IllegalStateException("Tidak dapat membuka aliran keluaran")
                }
                db.updateTransfer(transferId, "Selesai", "Tersimpan sebagai $outputName")
                Result.success()
            } catch (e: Exception) {
                output.delete()
                throw e
            }
        } catch (e: InterruptedException) {
            db.updateTransfer(transferId, "Dibatalkan", e.message ?: "Transfer dibatalkan")
            Result.failure()
        } catch (e: java.io.IOException) {
            db.updateTransfer(transferId, "Menunggu", "Gangguan I/O; WorkManager akan mencoba ulang")
            Result.retry()
        } catch (e: Exception) {
            db.updateTransfer(transferId, "Gagal", e.localizedMessage ?: "Kesalahan transfer")
            Result.failure()
        }
    }

    private fun fail(id: Long, message: String): Result {
        if (id >= 0) LocalFileDatabase(applicationContext).updateTransfer(id, "Gagal", message)
        return Result.failure()
    }

    private fun uniqueName(folder: DocumentFile, original: String): String {
        if (folder.findFile(original) == null) return original
        val base = original.substringBeforeLast('.', original)
        val ext = original.substringAfterLast('.', "")
        var index = 1
        while (true) {
            val candidate = if (ext.isBlank()) "$base ($index)" else "$base ($index).$ext"
            if (folder.findFile(candidate) == null) return candidate
            index++
        }
    }

    companion object {
        const val KEY_TRANSFER_ID = "transfer_id"
        const val KEY_SOURCE_PATH = "source_path"
        const val KEY_TREE_URI = "tree_uri"
        const val KEY_PROGRESS = "progress"
        const val KEY_COPIED = "copied"
        const val KEY_TOTAL = "total"
    }
}
