package dev.gsprasada.nexoracloud

import android.app.Application
import android.content.Context
import android.webkit.MimeTypeMap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Satu entri di penjelajah terpadu: folder virtual Telegram atau berkas (Telegram/lokal). */
data class NxItem(
    val id: String,
    val isFolder: Boolean,
    val source: String,
    val name: String,
    val folder: String,
    val sizeBytes: Long,
    val mime: String,
    val dateText: String,
    val sha256: String,
    val cloudId: Long,
    val localPath: String?,
    val fileCount: Int = 0
) {
    val kind: String get() = if (isFolder) "folder" else nxKind(mime, name)
    val fullPath: String get() = if (folder.isEmpty()) name else "$folder/$name"
}

sealed interface NxViewerState {
    data object Idle : NxViewerState
    data object Loading : NxViewerState
    data class Ready(val file: File) : NxViewerState
    data object Cancelled : NxViewerState
    data class Failed(val message: String) : NxViewerState
}

/**
 * Penjelajah terpadu: menggabungkan berkas cloud (Telegram lewat [CloudGateway]) dan pustaka lokal.
 * Seluruh operasi cloud memakai gateway/TdCloud yang sudah ada; ViewModel ini tidak mengubah logika autentikasi atau penyimpanan.
 */
class ExplorerViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Context = app.applicationContext
    private val prefs = ctx.getSharedPreferences("nexora_ui_flags", Context.MODE_PRIVATE)
    val db = LocalFileDatabase(ctx)
    val gateway = CloudGateway(ctx, BackendClient(ctx))
    val library: File = File(ctx.filesDir, "berkas").apply { mkdirs() }
    private val trashDir = File(ctx.filesDir, "sampah")
    val sessionCacheDir: File = File(ctx.cacheDir, "session_preview").apply { mkdirs() }

    // ------------------------------------------------------------------ state data

    var cloudItems by mutableStateOf<List<NxItem>>(emptyList()); private set
    var localItems by mutableStateOf<List<NxItem>>(emptyList()); private set
    var emptyFolders by mutableStateOf<List<String>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var cloudNote by mutableStateOf<String?>(null); private set
    var toast by mutableStateOf<String?>(null); private set
    var cacheTick by mutableIntStateOf(0); private set
    private var localHashes: Map<String, String> = emptyMap()
    private var toastJob: Job? = null

    var favTd by mutableStateOf<Set<String>>(prefs.getStringSet("fav", emptySet())?.toSet() ?: emptySet()); private set
    var archivedIds by mutableStateOf<Set<String>>(prefs.getStringSet("archive", emptySet())?.toSet() ?: emptySet()); private set
    var localFavs by mutableStateOf<Set<String>>(emptySet()); private set
    var labelMap by mutableStateOf<Map<String, List<String>>>(loadLabels()); private set

    // ------------------------------------------------------------------ state navigasi

    var filter by mutableStateOf("all")
    var query by mutableStateOf("")
    var filesFolder by mutableStateOf("")
    var galleryFolder by mutableStateOf("")
    var batchMode by mutableStateOf(false); private set
    var selected by mutableStateOf<Set<String>>(emptySet()); private set

    var viewerItem by mutableStateOf<NxItem?>(null); private set
    var viewerState by mutableStateOf<NxViewerState>(NxViewerState.Idle); private set
    private var viewerJob: Job? = null

    fun showToast(message: String) {
        toast = message
        toastJob?.cancel()
        toastJob = viewModelScope.launch { delay(2600); toast = null }
    }

    // ------------------------------------------------------------------ muat data

    fun refresh() {
        viewModelScope.launch {
            loading = true
            val cloud = withContext(Dispatchers.IO) { runCatching { loadCloud() } }
            cloud.onSuccess { cloudItems = it.first; emptyFolders = it.second; cloudNote = null }
                .onFailure { cloudNote = it.message ?: "Cloud tidak dapat dimuat." }
            val local = withContext(Dispatchers.IO) {
                Triple(db.listFiles().map(::toLocalItem), db.favoritePaths(), db.listHashes())
            }
            localItems = local.first; localFavs = local.second; localHashes = local.third
            loading = false
        }
    }

    private fun loadCloud(): Pair<List<NxItem>, List<String>> {
        val out = ArrayList<NxItem>()
        var offset = 0
        while (offset < 20000) {
            val page = gateway.listCloudFiles(100, offset, "", "all", "date", "desc")
            out.addAll(page.map(::toCloudItem))
            if (page.size < 100) break
            offset += page.size
        }
        return out to gateway.emptyFolders()
    }

    private fun toCloudItem(f: CloudFileItem) = NxItem(
        id = "tdlib:${f.id}", isFolder = false, source = "tdlib", name = f.name,
        folder = f.archivePath.trim('/'), sizeBytes = f.byteSize, mime = f.mimeType,
        dateText = f.createdAt, sha256 = f.sha256, cloudId = f.id, localPath = null
    )

    private fun toLocalItem(f: LocalItem): NxItem {
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.ext) ?: "application/octet-stream"
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(f.time))
        return NxItem("lokal:${f.path}", false, "lokal", f.name, "", f.size, mime, date, "", 0L, f.path)
    }

    // ------------------------------------------------------------------ flag, label, cache

    private fun loadLabels(): Map<String, List<String>> = runCatching {
        val obj = JSONObject(prefs.getString("labels", "{}") ?: "{}")
        obj.keys().asSequence().associateWith { key ->
            val arr = obj.getJSONArray(key)
            (0 until arr.length()).map { arr.getString(it) }
        }
    }.getOrDefault(emptyMap())

    private fun persistFlags() {
        val labels = JSONObject()
        labelMap.forEach { (k, v) -> if (v.isNotEmpty()) labels.put(k, JSONArray(v)) }
        prefs.edit().putStringSet("fav", HashSet(favTd)).putStringSet("archive", HashSet(archivedIds))
            .putString("labels", labels.toString()).apply()
    }

    fun isFav(item: NxItem): Boolean = if (item.source == "lokal") item.localPath in localFavs else item.id in favTd
    fun isArchived(item: NxItem): Boolean = item.id in archivedIds
    fun labelsOf(item: NxItem): List<String> = labelMap[item.id].orEmpty()

    fun cacheFile(item: NxItem): File? =
        if (item.source == "tdlib" && !item.isFolder) {
            // Satu folder per ID agar nama berkas tetap asli saat disalin ke folder unduhan.
            File(File(sessionCacheDir, item.cloudId.toString()), item.name.replace(Regex("[^A-Za-z0-9._ -]"), "_").ifBlank { "berkas" })
        } else null

    /** Berkas lokal selalu tersedia offline; berkas Telegram tersedia bila sudah ada di cache sesi. */
    fun isCached(item: NxItem): Boolean {
        if (cacheTick < 0) return false
        if (item.isFolder) return false
        if (item.source == "lokal") return true
        return cacheFile(item)?.let { it.isFile && it.length() > 0 } == true
    }

    fun toggleFavorite(item: NxItem) {
        if (item.source == "lokal") {
            val path = item.localPath ?: return
            viewModelScope.launch {
                val now = withContext(Dispatchers.IO) { db.toggleFavorite(path, library); db.favoritePaths() }
                localFavs = now
                showToast(if (path in now) "Ditambahkan ke Favorit." else "Dihapus dari Favorit.")
            }
        } else {
            val on = item.id !in favTd
            favTd = if (on) favTd + item.id else favTd - item.id
            persistFlags()
            showToast(if (on) "Ditambahkan ke Favorit." else "Dihapus dari Favorit.")
        }
    }

    fun toggleArchive(item: NxItem) {
        val on = item.id !in archivedIds
        archivedIds = if (on) archivedIds + item.id else archivedIds - item.id
        persistFlags()
        showToast(if (on) "Dipindahkan ke Arsip Tersembunyi." else "Dikembalikan dari Arsip.")
    }

    fun setLabels(item: NxItem, labels: List<String>) {
        labelMap = labelMap + (item.id to labels.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(12))
        persistFlags()
    }

    fun sessionCacheFiles(): List<File> {
        if (cacheTick < 0) return emptyList()
        return sessionCacheDir.walkTopDown().filter { it.isFile && !it.name.endsWith(".part") }.sortedByDescending { it.lastModified() }.toList()
    }

    fun clearSessionCache() {
        viewModelScope.launch {
            val removed = withContext(Dispatchers.IO) {
                val count = sessionCacheFiles().size
                sessionCacheDir.listFiles()?.forEach { it.deleteRecursively() }
                count
            }
            cacheTick++
            showToast("$removed file cache sesi dibersihkan.")
        }
    }

    // ------------------------------------------------------------------ daftar terlihat

    fun allFiles(): List<NxItem> = cloudItems + localItems

    /** Pasangan (folder, berkas) yang tampil untuk tab Files ([gallery] = false) atau Gallery. */
    fun visible(gallery: Boolean): Pair<List<NxItem>, List<NxItem>> {
        val folderPath = if (gallery) galleryFolder else filesFolder
        val q = query.trim().lowercase()
        val files = allFiles().filter { f ->
            val archived = isArchived(f)
            if (!gallery && filter == "all" && archived) return@filter false
            when (filter) {
                "fav" -> if (!isFav(f)) return@filter false
                "archive" -> if (!archived) return@filter false
                "offline" -> if (!isCached(f)) return@filter false
                "tdlib", "gdrive", "github", "lokal" -> if (f.source != filter) return@filter false
            }
            if (q.isEmpty()) {
                if ((gallery || filter == "all") && f.folder != folderPath) return@filter false
            } else if (!f.name.lowercase().contains(q)) {
                return@filter false
            }
            true
        }
        val showFolders = q.isEmpty() && (filter == "all" || filter == "tdlib")
        val folders: List<NxItem> = if (!showFolders) emptyList() else {
            val prefix = if (folderPath.isEmpty()) "" else "$folderPath/"
            val names = (cloudItems.map { it.folder } + emptyFolders)
                .filter { it.startsWith(prefix) && it.length > prefix.length }
                .map { it.removePrefix(prefix).substringBefore('/') }.distinct().sortedDescending()
            names.map { n ->
                val path = prefix + n
                NxItem(
                    "folder:$path", true, "tdlib", n, folderPath, 0L, "", "", "", 0L, null,
                    fileCount = cloudItems.count { it.folder == path || it.folder.startsWith("$path/") }
                )
            }
        }
        return folders to files
    }

    /** Seluruh jalur folder Telegram (beserta induknya) untuk pilihan tujuan. Root = "". */
    fun folderChoices(): List<String> {
        val all = HashSet<String>()
        (cloudItems.map { it.folder } + emptyFolders).forEach { path ->
            var current = ""
            path.split('/').filter { it.isNotEmpty() }.forEach { seg ->
                current = if (current.isEmpty()) seg else "$current/$seg"
                all.add(current)
            }
        }
        return listOf("") + all.sorted()
    }

    fun bytesBySource(): Map<String, Long> =
        mapOf("tdlib" to cloudItems.sumOf { it.sizeBytes }, "lokal" to localItems.sumOf { it.sizeBytes })

    // ------------------------------------------------------------------ pilih / batch

    fun setBatch(on: Boolean) { batchMode = on; if (!on) selected = emptySet() }
    fun toggleSelect(id: String) { selected = if (id in selected) selected - id else selected + id }

    fun selectAll(gallery: Boolean) {
        val (folders, files) = visible(gallery)
        val ids = (folders + files).map { it.id }.toSet()
        selected = if (ids.isNotEmpty() && selected.containsAll(ids)) emptySet() else ids
    }

    fun selectedItems(): List<NxItem> = filesOf(selected)

    /** Target operasi dari seleksi batch: folder tetap sebagai folder (untuk pindah/ganti nama), berkas sebagai berkas. */
    fun selectedTargets(): List<NxItem> = selected.mapNotNull { id ->
        if (id.startsWith("folder:")) {
            val path = id.removePrefix("folder:")
            NxItem(id, true, "tdlib", path.substringAfterLast('/'), path.substringBeforeLast('/', ""), 0L, "", "", "", 0L, null)
        } else {
            allFiles().firstOrNull { it.id == id }
        }
    }

    private fun filesOf(ids: Set<String>): List<NxItem> {
        val out = LinkedHashMap<String, NxItem>()
        for (id in ids) {
            if (id.startsWith("folder:")) {
                val path = id.removePrefix("folder:")
                cloudItems.filter { it.folder == path || it.folder.startsWith("$path/") }.forEach { out[it.id] = it }
            } else {
                allFiles().firstOrNull { it.id == id }?.let { out[it.id] = it }
            }
        }
        return out.values.toList()
    }

    private fun filesOf(items: List<NxItem>): List<NxItem> = filesOf(items.map { it.id }.toSet())

    // ------------------------------------------------------------------ operasi

    private fun operate(block: () -> String) {
        viewModelScope.launch {
            try {
                val message = withContext(Dispatchers.IO) { block() }
                showToast(message)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showToast("Gagal: ${e.message ?: e.javaClass.simpleName}")
                AppLog.err("Operasi gagal: ${e.message}")
            }
            setBatch(false)
            refresh()
        }
    }

    fun rename(item: NxItem, newName: String) = operate {
        val clean = newName.trim()
        require(clean.isNotEmpty()) { "Nama tidak boleh kosong." }
        when {
            item.isFolder -> gateway.renameFolder(item.fullPath, (if (item.folder.isEmpty()) "" else item.folder + "/") + clean)
            item.source == "tdlib" -> gateway.renameCloudFile(item.cloudId, clean)
            else -> {
                check(db.renameLocalFile(item.localPath.orEmpty(), clean, library)) { "Nama sudah dipakai atau tidak valid." }
                "Nama berkas diubah menjadi $clean."
            }
        }
    }

    fun trash(targets: List<NxItem>) = operate {
        var ok = 0
        var failed = 0
        for (t in filesOf(targets)) {
            try {
                if (t.source == "tdlib") gateway.softDeleteCloudFile(t.cloudId)
                else check(db.moveToTrash(t.localPath.orEmpty(), library, trashDir)) { "Pemindahan gagal." }
                ok++
            } catch (e: Exception) {
                failed++
            }
        }
        if (failed == 0) "$ok item dipindahkan ke Sampah." else "$ok item ke Sampah, $failed gagal."
    }

    fun purge(targets: List<NxItem>) = operate {
        var ok = 0
        var failed = 0
        for (t in filesOf(targets)) {
            try {
                if (t.source == "tdlib") {
                    gateway.purgeCloudFile(t.cloudId)
                } else {
                    check(File(t.localPath.orEmpty()).delete()) { "Berkas lokal tidak dapat dihapus." }
                    db.reconcile(library)
                }
                cacheFile(t)?.delete()
                ok++
            } catch (e: Exception) {
                failed++
            }
        }
        cacheTick++
        if (failed == 0) "$ok item dihapus permanen." else "$ok item dihapus, $failed gagal."
    }

    fun moveToFolder(targets: List<NxItem>, folder: String) = operate {
        var ok = 0
        var failed = 0
        for (t in targets) {
            try {
                when {
                    t.isFolder -> gateway.renameFolder(t.fullPath, (if (folder.isEmpty()) "" else "$folder/") + t.name)
                    t.source == "tdlib" -> gateway.moveCloudFile(t.cloudId, folder)
                    else -> error("Berkas lokal tidak memiliki folder virtual.")
                }
                ok++
            } catch (e: Exception) {
                failed++
            }
        }
        if (failed == 0) "$ok item dipindahkan." else "$ok item dipindahkan, $failed gagal."
    }

    fun createFolder(parent: String, name: String) = operate {
        val clean = name.trim()
        require(clean.isNotEmpty()) { "Nama folder tidak boleh kosong." }
        gateway.createFolder(if (parent.isEmpty()) clean else "$parent/$clean")
    }

    /** Membuat berkas teks baru lalu mengunggahnya ke folder Telegram. */
    fun createTextFile(parent: String, name: String, body: String) = operate {
        val safe = name.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
        require(safe.isNotEmpty()) { "Nama berkas tidak boleh kosong." }
        val dir = File(File(ctx.cacheDir, "new_files"), System.currentTimeMillis().toString()).apply { mkdirs() }
        val file = File(dir, safe)
        file.writeText(body)
        try {
            gateway.uploadFileToFolder(file, parent)
        } finally {
            dir.deleteRecursively()
        }
        "Berkas $safe dibuat dan diunggah."
    }

    fun saveInlineText(item: NxItem, text: String) = operate {
        val path = item.localPath ?: error("Hanya berkas lokal yang dapat disimpan langsung.")
        val file = File(path)
        file.writeText(text)
        db.index(file)
        "Perubahan teks disimpan."
    }

    /**
     * Mengekstrak ZIP: berkas Telegram diunduh ke cache lalu hasilnya diunggah ke subfolder bernama ZIP di folder yang sama;
     * ZIP lokal diekstrak ke pustaka lokal. Entri berbahaya (zip-slip) diabaikan dan ukuran total dibatasi.
     */
    fun extractZipHere(item: NxItem) = operate {
        val zipFile: File = if (item.source == "lokal") {
            File(item.localPath.orEmpty())
        } else {
            val cache = cacheFile(item) ?: error("Sumber berkas tidak didukung.")
            if (!(cache.isFile && cache.length() > 0)) downloadToCache(item, cache)
            cache
        }
        val stage = File(File(ctx.cacheDir, "upload_stage"), System.currentTimeMillis().toString())
        val entries = NxTransferEngine.extractZip(zipFile, stage)
        require(entries.isNotEmpty()) { "ZIP kosong atau tidak berisi berkas yang aman diekstrak." }
        val base = item.name.substringBeforeLast('.')
        if (item.source == "tdlib") {
            check(TdClient.isReady) { "Login akun Telegram diperlukan untuk mengunggah hasil ekstrak." }
            for ((file, relative) in entries) {
                val target = listOf(item.folder, base, relative).flatMap { it.split('/') }.filter { it.isNotBlank() }.joinToString("/")
                NxTransferEngine.enqueueUpload(ctx, db, file.absolutePath, file.name, target)
            }
            "${entries.size} berkas hasil ekstrak masuk antrean upload ke folder $base."
        } else {
            for ((file, _) in entries) {
                var out = File(library, file.name)
                if (out.exists()) out = File(library, "${System.currentTimeMillis()}_${file.name}")
                file.copyTo(out, overwrite = false)
                db.index(out)
            }
            stage.deleteRecursively()
            "${entries.size} berkas diekstrak ke pustaka lokal."
        }
    }

    /** Mengantre unduhan ke folder SAF pilihan. [useCache] = salin dari cache sesi bila sudah ada (tanpa unduh ulang). */
    fun enqueueDownload(item: NxItem, useCache: Boolean = false) {
        viewModelScope.launch {
            val message = withContext(Dispatchers.IO) {
                val tree = NxTransferEngine.downloadTreeUri(ctx)
                val cached = if (useCache) cacheFile(item)?.takeIf { it.isFile && it.length() > 0 } else null
                when {
                    tree == null -> "Pilih folder unduhan terlebih dahulu."
                    cached != null -> {
                        NxTransferEngine.enqueueLocalSave(ctx, db, cached.absolutePath, item.name, tree)
                        "${item.name} disalin dari cache sesi ke folder unduhan."
                    }
                    item.source == "tdlib" -> {
                        NxTransferEngine.enqueueCloudDownload(ctx, db, item.cloudId, item.name, item.mime, tree)
                        "Unduhan ${item.name} masuk antrean. Pantau di halaman Transfer."
                    }
                    else -> {
                        NxTransferEngine.enqueueLocalSave(ctx, db, item.localPath.orEmpty(), item.name, tree)
                        "Penyalinan ${item.name} masuk antrean."
                    }
                }
            }
            showToast(message)
        }
    }

    /** Menyalin berkas lokal ke Telegram ([folder] tujuan). */
    fun uploadLocalToCloud(item: NxItem, folder: String) {
        viewModelScope.launch {
            val message = withContext(Dispatchers.IO) {
                if (!TdClient.isReady) "Login akun Telegram diperlukan."
                else {
                    NxTransferEngine.enqueueUpload(ctx, db, item.localPath.orEmpty(), item.name, folder)
                    "Upload ${item.name} masuk antrean."
                }
            }
            showToast(message)
        }
    }

    // ------------------------------------------------------------------ viewer

    fun openViewer(item: NxItem) {
        viewerItem = item
        loadViewer(item)
    }

    fun switchViewer(item: NxItem) = openViewer(item)

    fun closeViewer() {
        viewerJob?.cancel()
        viewerItem = null
        viewerState = NxViewerState.Idle
    }

    fun cancelViewerLoad() { viewerJob?.cancel() }

    fun retryViewer() { viewerItem?.let { loadViewer(it) } }

    private fun loadViewer(item: NxItem) {
        viewerJob?.cancel()
        if (item.source == "lokal") {
            val file = File(item.localPath.orEmpty())
            viewerState = if (file.isFile) NxViewerState.Ready(file) else NxViewerState.Failed("Berkas lokal tidak ditemukan.")
            return
        }
        val cache = cacheFile(item) ?: run { viewerState = NxViewerState.Failed("Sumber berkas tidak didukung."); return }
        if (cache.isFile && cache.length() > 0) { viewerState = NxViewerState.Ready(cache); return }
        viewerState = NxViewerState.Loading
        viewerJob = viewModelScope.launch {
            try {
                runInterruptible(Dispatchers.IO) { downloadToCache(item, cache) }
                cacheTick++
                if (viewerItem?.id == item.id) viewerState = NxViewerState.Ready(cache)
            } catch (e: CancellationException) {
                if (viewerItem?.id == item.id) viewerState = NxViewerState.Cancelled
                throw e
            } catch (e: Exception) {
                if (viewerItem?.id == item.id) viewerState = NxViewerState.Failed(e.message ?: "Pratinjau gagal dimuat.")
            }
        }
    }

    private fun downloadToCache(item: NxItem, cache: File) {
        cache.parentFile?.mkdirs()
        val part = File(cache.parentFile, cache.name + ".part")
        try {
            part.outputStream().use { out -> gateway.downloadCloudFile(item.cloudId, out) { _, _ -> } }
            if (!part.renameTo(cache)) throw IOException("Cache sesi tidak dapat disimpan.")
        } finally {
            if (part.exists()) part.delete()
        }
    }

    /** Menyimpan berkas ke cache sesi tanpa membuka viewer (untuk akses offline). */
    fun pinOffline(item: NxItem) {
        if (item.source == "lokal") { showToast("Berkas lokal sudah tersedia offline."); return }
        val cache = cacheFile(item) ?: return
        if (cache.isFile && cache.length() > 0) { showToast("Sudah ada di cache sesi."); return }
        viewModelScope.launch {
            showToast("Menyimpan ke cache sesi…")
            try {
                runInterruptible(Dispatchers.IO) { downloadToCache(item, cache) }
                cacheTick++
                showToast("${item.name} tersimpan di cache sesi (offline).")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showToast("Gagal menyimpan cache: ${e.message}")
            }
        }
    }

    // ------------------------------------------------------------------ duplikat

    /** Kelompok duplikat: "sha256", "name", "cross". Jenis "media" membutuhkan analisis visual dan belum tersedia. */
    fun duplicateGroups(type: String): List<List<NxItem>> {
        val files = allFiles()
        fun shaOf(f: NxItem): String = if (f.source == "lokal") localHashes[f.localPath].orEmpty() else f.sha256
        return when (type) {
            "sha256" -> files.filter { shaOf(it).isNotEmpty() }.groupBy { shaOf(it) }.values.filter { it.size > 1 }
            "name" -> files.groupBy { it.name.lowercase(Locale.ROOT) + "|" + it.sizeBytes }.values.filter { it.size > 1 }
            "cross" -> files.groupBy { if (shaOf(it).isNotEmpty()) shaOf(it) else it.name.lowercase(Locale.ROOT) + "|" + it.sizeBytes }
                .values.filter { g -> g.map { it.source }.distinct().size > 1 }
            else -> emptyList()
        }
    }
}
