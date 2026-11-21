package dev.gsprasada.nexoracloud

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject

/** Satu berkas yang dipilih pengguna untuk diunggah. [relativeDir] terisi bila berasal dari pemilihan folder. */
data class NxUploadSource(val uri: Uri, val name: String, val size: Long, val relativeDir: String)

/** Penampung pilihan berkas/folder dari launcher Activity ke halaman Upload. */
object NxUploadPicker {
    val sources = MutableStateFlow<List<NxUploadSource>>(emptyList())

    fun clear() { sources.value = emptyList() }

    /** Menelusuri folder SAF secara rekursif (maksimal 5000 berkas) menjadi daftar sumber. */
    fun collectTree(context: Context, tree: Uri): List<NxUploadSource> {
        val root = DocumentFile.fromTreeUri(context, tree) ?: return emptyList()
        val out = ArrayList<NxUploadSource>()
        fun walk(dir: DocumentFile, relative: String) {
            for (child in dir.listFiles()) {
                if (out.size >= 5000) return
                if (child.isDirectory) walk(child, if (relative.isEmpty()) (child.name ?: "folder") else relative + "/" + (child.name ?: "folder"))
                else if (child.isFile) out.add(NxUploadSource(child.uri, child.name ?: "berkas", child.length(), relative))
            }
        }
        walk(root, root.name.orEmpty())
        return out
    }
}

/** Antrean transfer WorkManager yang dipakai UI baru; memakai worker dan tabel transfer yang sama dengan UI lama. */
object NxTransferEngine {
    /** Naik setiap kali folder unduhan diganti, agar dialog unduhan menyegarkan label folder. */
    val folderTick = MutableStateFlow(0)

    private fun retryPrefs(context: Context) = context.getSharedPreferences("nexora_retry", Context.MODE_PRIVATE)

    private fun networkConstraints() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    private fun saveRetry(context: Context, transferId: Long, json: JSONObject) {
        retryPrefs(context).edit().putString("t$transferId", json.toString()).apply()
    }

    fun downloadTreeUri(context: Context): String? =
        context.getSharedPreferences("nexora", Context.MODE_PRIVATE).getString("folder_uri", null)

    /** Memasukkan unggahan ke antrean. [folder] null = ikuti pola arsip. Mengembalikan ID transfer. */
    fun enqueueUpload(context: Context, db: LocalFileDatabase, path: String, name: String, folder: String?, label: String = "Unggahan cloud"): Long {
        val transferId = db.addTransfer(name, label)
        val data = Data.Builder().putLong(CloudTransferWorker.KEY_TRANSFER_ID, transferId)
            .putString(CloudTransferWorker.KEY_ACTION, CloudTransferWorker.ACTION_UPLOAD)
            .putString(CloudTransferWorker.KEY_SOURCE_PATH, path)
        if (folder != null) data.putString(CloudTransferWorker.KEY_FOLDER, folder)
        val request = OneTimeWorkRequestBuilder<CloudTransferWorker>()
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(data.build()).addTag("nexora-cloud-transfer-$transferId").build()
        db.attachWork(transferId, request.id.toString())
        WorkManager.getInstance(context).enqueue(request)
        saveRetry(context, transferId, JSONObject().put("type", "upload").put("path", path).put("name", name).put("folder", folder ?: JSONObject.NULL))
        return transferId
    }

    fun enqueueCloudDownload(context: Context, db: LocalFileDatabase, cloudId: Long, name: String, mime: String, treeUri: String, label: String = "Unduhan cloud"): Long {
        val transferId = db.addTransfer(name, label)
        val request = OneTimeWorkRequestBuilder<CloudTransferWorker>()
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(
                Data.Builder().putLong(CloudTransferWorker.KEY_TRANSFER_ID, transferId)
                    .putString(CloudTransferWorker.KEY_ACTION, CloudTransferWorker.ACTION_DOWNLOAD)
                    .putLong(CloudTransferWorker.KEY_CLOUD_ID, cloudId).putString(CloudTransferWorker.KEY_TREE_URI, treeUri)
                    .putString(CloudTransferWorker.KEY_NAME, name).putString(CloudTransferWorker.KEY_MIME, mime).build()
            ).addTag("nexora-cloud-transfer-$transferId").build()
        db.attachWork(transferId, request.id.toString())
        WorkManager.getInstance(context).enqueue(request)
        saveRetry(context, transferId, JSONObject().put("type", "download").put("cloudId", cloudId).put("name", name).put("mime", mime).put("tree", treeUri))
        return transferId
    }

    fun enqueueLocalSave(context: Context, db: LocalFileDatabase, path: String, name: String, treeUri: String): Long {
        val transferId = db.addTransfer(name, "Unduhan lokal")
        val request = OneTimeWorkRequestBuilder<LocalCopyWorker>()
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 20, TimeUnit.SECONDS)
            .setInputData(
                Data.Builder().putLong(LocalCopyWorker.KEY_TRANSFER_ID, transferId)
                    .putString(LocalCopyWorker.KEY_SOURCE_PATH, path).putString(LocalCopyWorker.KEY_TREE_URI, treeUri).build()
            ).addTag("nexora-transfer-$transferId").build()
        db.attachWork(transferId, request.id.toString())
        WorkManager.getInstance(context).enqueue(request)
        saveRetry(context, transferId, JSONObject().put("type", "local").put("path", path).put("name", name).put("tree", treeUri))
        return transferId
    }

    /** True bila transfer lama dapat diulang (data pemicunya tersimpan). Transfer sebelum versi ini tidak dapat diulang. */
    fun canRetry(context: Context, record: TransferRecord): Boolean = retryPrefs(context).contains("t${record.id}")

    /** Mengulang transfer gagal sebagai transfer baru dengan parameter yang sama. */
    fun retry(context: Context, db: LocalFileDatabase, record: TransferRecord): Boolean {
        val raw = retryPrefs(context).getString("t${record.id}", null) ?: return false
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return false
        when (json.optString("type")) {
            "upload" -> {
                val path = json.optString("path")
                if (!File(path).isFile) return false
                val folder = if (json.isNull("folder")) null else json.optString("folder")
                enqueueUpload(context, db, path, json.optString("name"), folder, record.direction)
            }
            "download" -> enqueueCloudDownload(context, db, json.optLong("cloudId"), json.optString("name"), json.optString("mime"), json.optString("tree"), record.direction)
            "local" -> {
                val path = json.optString("path")
                if (!File(path).isFile) return false
                enqueueLocalSave(context, db, path, json.optString("name"), json.optString("tree"))
            }
            else -> return false
        }
        retryPrefs(context).edit().remove("t${record.id}").apply()
        return true
    }

    // ------------------------------------------------------------------ pipeline unggah

    private fun safeName(name: String): String = name.replace(Regex("[^A-Za-z0-9._ -]"), "_").ifBlank { "berkas" }

    private fun joinFolder(vararg parts: String): String =
        parts.flatMap { it.replace('\\', '/').split('/') }.map { it.trim() }.filter { it.isNotEmpty() && it != "." && it != ".." }.joinToString("/")

    /** Menyalin satu URI ke pustaka lokal (agar tetap terbaca saat worker berjalan) dan mengindeksnya. */
    private fun importToLibrary(context: Context, db: LocalFileDatabase, source: NxUploadSource): File? = runCatching {
        val library = File(context.filesDir, "berkas").apply { mkdirs() }
        var out = File(library, safeName(source.name))
        if (out.exists()) out = File(library, "${System.currentTimeMillis()}_${safeName(source.name)}")
        context.contentResolver.openInputStream(source.uri)!!.use { input -> out.outputStream().use { input.copyTo(it) } }
        db.index(out)
        out
    }.getOrNull()

    /** Ekstrak ZIP ke [outDir]; mengabaikan entri berbahaya (zip-slip) dan membatasi total ukuran (anti zip-bomb). */
    fun extractZip(zip: File, outDir: File, maxTotalBytes: Long = 4L * 1024 * 1024 * 1024, maxEntries: Int = 5000): List<Pair<File, String>> {
        val result = ArrayList<Pair<File, String>>()
        val rootPath = outDir.apply { mkdirs() }.canonicalPath + File.separator
        var total = 0L
        ZipInputStream(zip.inputStream().buffered()).use { zin ->
            var entry = zin.nextEntry
            while (entry != null && result.size < maxEntries) {
                if (!entry.isDirectory) {
                    val target = File(outDir, entry.name.replace('\\', '/')).canonicalFile
                    if (target.path.startsWith(rootPath)) {
                        target.parentFile?.mkdirs()
                        target.outputStream().buffered().use { out ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val n = zin.read(buffer)
                                if (n < 0) break
                                total += n
                                if (total > maxTotalBytes) throw IllegalStateException("Isi ZIP terlalu besar (melebihi batas aman).")
                                out.write(buffer, 0, n)
                            }
                        }
                        val relDir = target.parentFile?.canonicalPath?.removePrefix(rootPath.trimEnd(File.separatorChar)).orEmpty().trim(File.separatorChar)
                        result.add(target to relDir.replace(File.separatorChar, '/'))
                    }
                }
                zin.closeEntry()
                entry = zin.nextEntry
            }
        }
        return result
    }

    /**
     * Memproses sumber unggahan sesuai pilihan di halaman Upload. Berjalan di thread latar.
     * [provider]: "tdlib" mengantre upload ke Telegram; "lokal" hanya menyalin ke pustaka lokal.
     * Mengembalikan ringkasan untuk ditampilkan ke pengguna.
     */
    fun startUpload(context: Context, db: LocalFileDatabase, sources: List<NxUploadSource>, provider: String, destFolder: String, extract: Boolean): String {
        var queued = 0
        var imported = 0
        var failed = 0
        for (source in sources) {
            val file = importToLibrary(context, db, source)
            if (file == null) { failed++; continue }
            imported++
            if (provider != "tdlib") continue
            val isZip = source.name.lowercase().endsWith(".zip")
            if (extract && isZip) {
                val stage = File(File(context.cacheDir, "upload_stage"), System.currentTimeMillis().toString())
                val zipBase = source.name.substringBeforeLast('.')
                val extracted = runCatching { extractZip(file, stage) }.getOrNull()
                if (extracted == null) { failed++; continue }
                for ((entryFile, relDir) in extracted) {
                    enqueueUpload(context, db, entryFile.absolutePath, entryFile.name, joinFolder(destFolder, source.relativeDir, zipBase, relDir))
                    queued++
                }
            } else {
                enqueueUpload(context, db, file.absolutePath, file.name, joinFolder(destFolder, source.relativeDir))
                queued++
            }
        }
        return buildString {
            append("$imported berkas disiapkan")
            if (provider == "tdlib") append(", $queued masuk antrean upload Telegram")
            if (failed > 0) append(", $failed gagal diproses")
            append(".")
        }
    }
}
