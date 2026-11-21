package dev.gsprasada.nexoracloud

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

/** Transfer cloud berjalan di WorkManager sehingga antrean tetap tersimpan saat UI ditutup. */
class CloudTransferWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_TRANSFER_ID, -1L)
        val db = LocalFileDatabase(applicationContext)
        val backend = BackendClient(applicationContext)
        return try {
            val provider = runInterruptible(Dispatchers.IO) { CloudStorageProviderFactory.createPreferred(applicationContext, backend) }
            when (inputData.getString(KEY_ACTION)) {
                ACTION_UPLOAD -> {
                    val path = inputData.getString(KEY_SOURCE_PATH) ?: return fail(db, id, "File sumber tidak ditentukan")
                    val file = File(path)
                    if (!file.isFile || !file.canRead()) return fail(db, id, "File sumber tidak tersedia")
                    db.updateTransfer(id, "Berjalan", "Mengunggah ke Nexora Cloud")
                    setProgress(Data.Builder().putInt(KEY_PROGRESS, 5).putString(KEY_MESSAGE, "Menghubungi backend").build())
                    var lastSaved = 0L
                    // runInterruptible: pembatalan WorkManager menginterupsi pemanggilan blocking dan membatalkan transfer.
                    val targetFolder = inputData.getString(KEY_FOLDER)
                    val onBytes: (Long, Long) -> Unit = { copied, total ->
                        if (copied - lastSaved >= 512 * 1024 || copied == total) {
                            db.updateTransferProgress(id, copied, total)
                            lastSaved = copied
                        }
                    }
                    runInterruptible(Dispatchers.IO) {
                        if (provider is TdlibCloudProvider && targetFolder != null) provider.uploadTo(file, targetFolder, onBytes)
                        else provider.upload(file, onBytes)
                    }
                    db.updateTransferProgress(id, file.length(), file.length())
                    db.updateTransfer(id, "Selesai", "Upload cloud selesai")
                    notifyTransfer(db, id, file.name, "Upload selesai")
                    Result.success()
                }
                ACTION_DOWNLOAD -> {
                    val cloudId = inputData.getLong(KEY_CLOUD_ID, -1L)
                    val tree = inputData.getString(KEY_TREE_URI) ?: return fail(db, id, "Folder tujuan belum dipilih")
                    val name = inputData.getString(KEY_NAME) ?: "berkas-cloud"
                    val mime = inputData.getString(KEY_MIME) ?: "application/octet-stream"
                    if (cloudId <= 0) return fail(db, id, "ID file cloud tidak valid")
                    db.updateTransfer(id, "Berjalan", "Mengunduh dari cloud")
                    val folder = DocumentFile.fromTreeUri(applicationContext, Uri.parse(tree))
                        ?: return fail(db, id, "Folder tujuan tidak dapat dibuka")
                    if (!folder.canWrite()) return fail(db, id, "Folder tujuan tidak memiliki izin tulis")
                    val outputName = uniqueName(folder, name)
                    val output = folder.createFile(mime, outputName) ?: return fail(db, id, "Gagal membuat file tujuan")
                    try {
                        var lastSaved = 0L
                        val bytes = applicationContext.contentResolver.openOutputStream(output.uri, "w")!!.use { stream ->
                            runInterruptible(Dispatchers.IO) {
                                provider.download(cloudId, stream) { copied, total ->
                                    if (copied - lastSaved >= 512 * 1024 || (total > 0 && copied == total)) {
                                        db.updateTransferProgress(id, copied, total)
                                        lastSaved = copied
                                    }
                                }
                            }
                        }
                        db.updateTransferProgress(id, bytes, bytes)
                        db.updateTransfer(id, "Selesai", "Tersimpan sebagai $outputName")
                        notifyTransfer(db, id, outputName, "Unduhan selesai")
                        Result.success()
                    } catch (e: Exception) { output.delete(); throw e }
                }
                else -> fail(db, id, "Jenis transfer tidak dikenal")
            }
        } catch (e: CancellationException) {
            db.updateTransfer(id, "Dibatalkan", "Transfer dibatalkan pengguna")
            throw e
        } catch (e: IOException) {
            db.updateTransfer(id, "Menunggu", "Gangguan jaringan; akan dicoba ulang")
            Result.retry()
        } catch (e: Exception) {
            db.updateTransfer(id, "Gagal", e.localizedMessage ?: "Transfer gagal")
            notifyTransfer(db, id, inputData.getString(KEY_NAME) ?: "Berkas", "Transfer gagal")
            Result.failure()
        }
    }

    private fun notifyTransfer(db: LocalFileDatabase, id: Long, fileName: String, title: String) {
        if (db.getSetting("transfer_notifications", "true") != "true") return
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val channelId = "nexora_transfer"
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            val channel = android.app.NotificationChannel(channelId, "Transfer Nexora", android.app.NotificationManager.IMPORTANCE_DEFAULT)
            channel.description = "Pemberitahuan hasil upload dan unduhan Nexora"
            manager.createNotificationChannel(channel)
        }
        val builder = if (android.os.Build.VERSION.SDK_INT >= 26) {
            android.app.Notification.Builder(applicationContext, channelId)
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(applicationContext)
        }
        val notification = builder.setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle(title)
            .setContentText(fileName)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify((id % Int.MAX_VALUE).toInt(), notification) }
    }

    private fun fail(db: LocalFileDatabase, id: Long, message: String): Result {
        if (id >= 0) db.updateTransfer(id, "Gagal", message)
        return Result.failure()
    }

    private fun uniqueName(folder: DocumentFile, original: String): String {
        if (folder.findFile(original) == null) return original
        val base = original.substringBeforeLast('.', original)
        val ext = original.substringAfterLast('.', "")
        var n = 1
        while (true) {
            val candidate = if (ext.isBlank()) "$base ($n)" else "$base ($n).$ext"
            if (folder.findFile(candidate) == null) return candidate
            n++
        }
    }

    companion object {
        const val KEY_TRANSFER_ID = "transfer_id"
        const val KEY_ACTION = "action"
        const val KEY_SOURCE_PATH = "source_path"
        const val KEY_CLOUD_ID = "cloud_id"
        const val KEY_TREE_URI = "tree_uri"
        const val KEY_NAME = "name"
        /** Folder tujuan upload (hanya akun Telegram/TDLib; kosong = pola arsip). */
        const val KEY_FOLDER = "folder"
        const val KEY_MIME = "mime"
        const val KEY_PROGRESS = "progress"
        const val KEY_MESSAGE = "message"
        const val ACTION_UPLOAD = "upload"
        const val ACTION_DOWNLOAD = "download"
    }
}
