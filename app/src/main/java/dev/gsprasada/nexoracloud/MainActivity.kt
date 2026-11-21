package dev.gsprasada.nexoracloud

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Constraints
import androidx.work.NetworkType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONObject

private var Ink = Color(0xFF17212B)
private var Muted = Color(0xFF71808F)
private var Blue = Color(0xFF2787C7)
private var Pale = Color(0xFFE4F3FB)
private var Page = Color(0xFFF4F7FA)
private var SurfaceColor = Color.White

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) Toast.makeText(this, "Izin notifikasi belum diberikan. Notifikasi transfer mungkin tidak tampil.", Toast.LENGTH_LONG).show()
    }
    private var refreshFiles by mutableIntStateOf(0)
    private lateinit var fileDatabase: LocalFileDatabase
    private var selectedFolder by mutableStateOf<Uri?>(null)
    private var pendingConfigJson: String? = null
    private var pendingActivityCsv: ByteArray? = null
    private val createActivityCsv = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) runCatching { contentResolver.openOutputStream(uri)?.use { it.write(pendingActivityCsv ?: byteArrayOf()) } }
            .onSuccess { Toast.makeText(this, "Riwayat aktivitas berhasil diekspor.", Toast.LENGTH_LONG).show() }
            .onFailure { Toast.makeText(this, "Ekspor CSV gagal: ${it.message}", Toast.LENGTH_LONG).show() }
        pendingActivityCsv = null
    }
    private val createConfigFile = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching { contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(pendingConfigJson.orEmpty()) } }
            .onSuccess { Toast.makeText(this, "File konfigurasi berhasil diekspor.", Toast.LENGTH_LONG).show() }
            .onFailure { Toast.makeText(this, "Ekspor gagal: ${it.message}", Toast.LENGTH_LONG).show() }
        pendingConfigJson = null
    }
    private val openConfigFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            val json = contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
            val obj = JSONObject(json)
            require(obj.optInt("schema_version") == 1) { "Versi file konfigurasi tidak didukung." }
            val prefs = getSharedPreferences("nexora_backend", MODE_PRIVATE)
            prefs.edit().putString("base_url", obj.optString("backend_url", "").trim().trimEnd('/')).apply()
            val providers = obj.optJSONObject("providers") ?: JSONObject()
            getSharedPreferences("nexora_providers", MODE_PRIVATE).edit()
                .putBoolean("telegram", providers.optBoolean("telegram", true))
                .putBoolean("google_drive", providers.optBoolean("google_drive", false))
                .putBoolean("onedrive", providers.optBoolean("onedrive", false))
                .putBoolean("webdav", providers.optBoolean("webdav", false))
                .putBoolean("s3", providers.optBoolean("s3", false)).apply()
            obj.optString("download_folder_uri").takeIf { it.isNotBlank() }?.let { getSharedPreferences("nexora", MODE_PRIVATE).edit().putString("folder_uri", it).apply() }
            obj.optJSONObject("personalization")?.let { settings ->
                listOf("accent_color", "compact_mode", "confirm_delete", "default_sort").forEach { key ->
                    if (settings.has(key)) fileDatabase.setSetting(key, settings.optString(key))
                }
            }
        }.onSuccess { Toast.makeText(this, "Konfigurasi berhasil diimpor. API key tidak termasuk dan harus dimasukkan kembali bila diperlukan.", Toast.LENGTH_LONG).show(); recreate() }
            .onFailure { Toast.makeText(this, "Impor gagal: ${it.message}", Toast.LENGTH_LONG).show() }
    }
    private val pickFiles = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) importFiles(uris)
    }
    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                selectedFolder = uri
                getSharedPreferences("nexora", MODE_PRIVATE).edit().putString("folder_uri", uri.toString()).apply()
                NxTransferEngine.folderTick.value = NxTransferEngine.folderTick.value + 1
                Toast.makeText(this, "Lokasi unduhan disimpan", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Izin folder tidak dapat disimpan: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Pilih berkas untuk halaman Upload (UI baru). Hasil ditampung di [NxUploadPicker]. */
    private val pickUploadFiles = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { runCatching { contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
            val picked = uris.map { uri ->
                val doc = DocumentFile.fromSingleUri(this, uri)
                NxUploadSource(uri, doc?.name ?: "berkas", doc?.length() ?: 0L, "")
            }
            NxUploadPicker.sources.value = (NxUploadPicker.sources.value + picked).distinctBy { it.uri }
        }
    }

    /** Pilih folder (batch) untuk halaman Upload; isinya ditelusuri rekursif di thread latar. */
    private val pickUploadFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            lifecycleScope.launch {
                val found = withContext(Dispatchers.IO) { NxUploadPicker.collectTree(this@MainActivity, uri) }
                NxUploadPicker.sources.value = (NxUploadPicker.sources.value + found).distinctBy { it.uri }
                Toast.makeText(this@MainActivity, "${found.size} berkas ditemukan di folder.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun shareFile(file: File, mime: String) {
        runCatching {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mime.ifBlank { "*/*" }
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Bagikan berkas"))
        }.onFailure { Toast.makeText(this, "Tidak dapat membagikan berkas: ${it.message}", Toast.LENGTH_LONG).show() }
    }

    private fun openFile(file: File, mime: String) {
        runCatching {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime.ifBlank { "*/*" })
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        }.onFailure { Toast.makeText(this, "Tidak ada aplikasi untuk membuka berkas ini.", Toast.LENGTH_LONG).show() }
    }

    /** Keluar dari akun Telegram dan menghapus token/proxy tersimpan di perangkat ini. File di Telegram tidak disentuh. */
    private fun logoutEverything() {
        lifecycleScope.launch {
            if (TdClient.isReady) runCatching { TdClient.logOut() }
            SecretStore(this@MainActivity).put("github_pat", "")
            listOf("nexora_accounts", "nexora_proxy").forEach { getSharedPreferences(it, MODE_PRIVATE).edit().clear().apply() }
            Toast.makeText(this@MainActivity, "Sesi Telegram dan token tersimpan dihapus dari perangkat ini.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedFolder = getSharedPreferences("nexora", MODE_PRIVATE).getString("folder_uri", null)?.let(Uri::parse)
        fileDatabase = LocalFileDatabase(this)
        if (TdClient.hasSavedSession(applicationContext)) TdClient.start(applicationContext)
        fileDatabase.setSetting("device_last_active", System.currentTimeMillis().toString())
        val library = File(filesDir, "berkas").apply { mkdirs() }
        setContent { NexoraApp() }
        Thread {
            fileDatabase.reconcile(library)
            runOnUiThread { refreshFiles++ }
        }.start()
    }

    private fun importFiles(uris: List<Uri>) {
        val target = File(filesDir, "berkas").apply { mkdirs() }
        Thread {
            var imported = 0
            uris.forEach { uri ->
                try {
                    val document = DocumentFile.fromSingleUri(this, uri)
                    val originalName = document?.name ?: "berkas_${System.currentTimeMillis()}"
                    val safeName = originalName.replace(Regex("[^A-Za-z0-9._ -]"), "_").ifBlank { "berkas" }
                    var out = File(target, safeName)
                    if (out.exists()) out = File(target, "${System.currentTimeMillis()}_$safeName")
                    val copied = contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { output -> input.copyTo(output) } } != null
                    if (copied) {
                        val originalTime = document?.lastModified()?.takeIf { it > 0L } ?: System.currentTimeMillis()
                        out.setLastModified(originalTime)
                        fileDatabase.index(out)
                        imported++
                    } else out.delete()
                } catch (_: Exception) { }
            }
            runOnUiThread {
                refreshFiles++
                Toast.makeText(this, "$imported file berhasil dimasukkan ke pustaka Nexora", Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    private suspend fun saveToChosenFolder(item: LocalItem): String = withContext(Dispatchers.IO) {
        val treeUri = selectedFolder ?: throw IllegalStateException("Pilih lokasi unduhan terlebih dahulu")
        val folder = DocumentFile.fromTreeUri(this@MainActivity, treeUri)
            ?: throw IllegalStateException("Folder tujuan tidak dapat dibuka")
        if (!folder.canWrite()) throw IllegalStateException("Folder tujuan tidak memiliki izin tulis")
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(item.ext)
            ?: "application/octet-stream"
        var outputName = item.name
        var counter = 1
        while (folder.findFile(outputName) != null) {
            val base = item.name.substringBeforeLast('.', item.name)
            val suffix = item.name.substringAfterLast('.', "")
            outputName = if (suffix.isBlank()) "$base ($counter)" else "$base ($counter).$suffix"
            counter++
        }
        val output = folder.createFile(mime, outputName) ?: throw IllegalStateException("Gagal membuat file di lokasi tujuan")
        try {
            contentResolver.openInputStream(Uri.fromFile(File(item.path)))!!.use { input ->
                contentResolver.openOutputStream(output.uri, "w")!!.use { stream -> input.copyTo(stream) }
            }
            outputName
        } catch (e: Exception) {
            output.delete()
            throw e
        }
    }

    @Composable private fun NexoraApp() {
        var tab by remember { mutableStateOf("Chat") }
        var accentHex by remember { mutableStateOf(fileDatabase.getSetting("accent_color", "#2787C7")) }
        var compactMode by remember { mutableStateOf(fileDatabase.getSetting("compact_mode", "false").toBoolean()) }
        var confirmDelete by remember { mutableStateOf(fileDatabase.getSetting("confirm_delete", "true").toBoolean()) }
        var defaultSort by remember { mutableStateOf(fileDatabase.getSetting("default_sort", "terbaru")) }
        var archivePattern by remember { mutableStateOf(fileDatabase.getSetting("archive_pattern", "yyyy/MM/dd")) }
        var appLanguage by remember { mutableStateOf(fileDatabase.getSetting("app_language", "id")) }
        var transferNotifications by remember { mutableStateOf(fileDatabase.getSetting("transfer_notifications", "true").toBoolean()) }
        var themeMode by remember { mutableStateOf(fileDatabase.getSetting("theme_mode", "light")) }
        var filter by remember { mutableStateOf("Semua") }
        var query by remember { mutableStateOf("") }
        var showFilter by remember { mutableStateOf(false) }
        var selectedItem by remember { mutableStateOf<LocalItem?>(null) }
        val duplicateGroups = remember(refreshFiles) { fileDatabase.duplicateGroupCount() }
        var transferMessage by remember { mutableStateOf<String?>(null) }
        val backendClient = remember { BackendClient(this@MainActivity) }
        val gateway = remember { CloudGateway(this@MainActivity, backendClient) }
        var backendUrl by remember { mutableStateOf(backendClient.baseUrl()) }
        var backendKey by remember { mutableStateOf(backendClient.apiKey()) }
        var backendStatus by remember { mutableStateOf<String?>(null) }
        var backendBusy by remember { mutableStateOf(false) }
        var telegramAccountStatus by remember { mutableStateOf<TelegramAccountAuthStatus?>(null) }
        var accountCheckBusy by remember { mutableStateOf(false) }
        var accountCheckMessage by remember { mutableStateOf<String?>(null) }
        var storageBreakdown by remember { mutableStateOf<StorageBreakdown?>(null) }
        var storageCatalogStats by remember { mutableStateOf<CloudCatalogStats?>(null) }
        var storageRecentActivity by remember { mutableStateOf(emptyList<ActivityLogItem>()) }
        var storageStatsStatus by remember { mutableStateOf<String?>(null) }
        var storageStatsBusy by remember { mutableStateOf(false) }
        var csvSince by remember { mutableStateOf("") }
        var csvUntil by remember { mutableStateOf("") }
        var csvBusy by remember { mutableStateOf(false) }
        var cloudFiles by remember { mutableStateOf(emptyList<CloudFileItem>()) }
        var cloudLoading by remember { mutableStateOf(false) }
        var cloudHasMore by remember { mutableStateOf(false) }
        var cloudOffset by remember { mutableIntStateOf(0) }
        var cloudStatus by remember { mutableStateOf<String?>(null) }
        var cloudEmptyFolders by remember { mutableStateOf(emptyList<String>()) }
        var cloudFolderTools by remember { mutableStateOf(false) }
        var cloudSourceLabel by remember { mutableStateOf("Backend Nexora") }
        var cloudStats by remember { mutableStateOf<CloudCatalogStats?>(null) }
        var dashboardActivity by remember { mutableStateOf(emptyList<ActivityLogItem>()) }
        var dashboardStatus by remember { mutableStateOf<String?>(null) }
        var dashboardBusy by remember { mutableStateOf(false) }
        var trashFiles by remember { mutableStateOf(emptyList<TrashFileItem>()) }
        var trashStatus by remember { mutableStateOf<String?>(null) }
        var trashBusy by remember { mutableStateOf(false) }
        var activityItems by remember { mutableStateOf(emptyList<ActivityLogItem>()) }
        var activityStatus by remember { mutableStateOf<String?>(null) }
        var activityBusy by remember { mutableStateOf(false) }
        var activityFilter by remember { mutableStateOf("all") }
        var cloudPreviewBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
        var cloudPreviewName by remember { mutableStateOf("") }
        var cloudPreviewBusy by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val folderOp: (String, () -> String) -> Unit = { label, op ->
            scope.launch {
                cloudLoading = true; cloudStatus = null
                runCatching {
                    withContext(Dispatchers.IO) {
                        val message = op()
                        Triple(message, gateway.listCloudFiles(100, 0, "", "all", "date"), gateway.emptyFolders())
                    }
                }.onSuccess {
                    cloudFiles = it.second; cloudOffset = it.second.size; cloudHasMore = it.second.size == 100
                    cloudEmptyFolders = it.third; cloudStatus = it.first
                }.onFailure { cloudStatus = "$label gagal: ${it.message}" }
                cloudLoading = false
            }
        }
        val rawFiles = remember(refreshFiles) { fileDatabase.listFiles() }
        val files = remember(rawFiles, defaultSort) {
            when (defaultSort) {
                "nama" -> rawFiles.sortedBy { it.name.lowercase(Locale.ROOT) }
                "terbesar" -> rawFiles.sortedByDescending { it.size }
                else -> rawFiles.sortedByDescending { it.time }
            }
        }
        // Perbarui riwayat selama halaman Transfer terbuka agar status worker terlihat.
        LaunchedEffect(tab) {
            if (tab == "Transfer") {
                while (true) { refreshFiles++; kotlinx.coroutines.delay(1500) }
            }
        }
        val userAccent = remember(accentHex) { runCatching { Color(android.graphics.Color.parseColor(accentHex)) }.getOrDefault(Color(0xFF2787C7)) }
        val isDark = themeMode == "dark"
        val nexoraColors = remember(isDark, accentHex) { buildNexoraColors(isDark, accentHex) }
        run {
            // Layar lama memakai variabel global ini; disamakan dengan token desain v12 agar tampil konsisten.
            Ink = nexoraColors.text
            Muted = nexoraColors.muted
            Blue = nexoraColors.primary
            Pale = nexoraColors.primaryContainer
            Page = nexoraColors.bg
            SurfaceColor = nexoraColors.surface
        }
        val scheme = if (isDark) darkColorScheme(primary = userAccent, secondary = userAccent, background = Color(0xFF101820), surface = Color(0xFF1B2732))
                     else lightColorScheme(primary = userAccent, secondary = userAccent, background = Color(0xFFF4F7FA), surface = Color.White)
        MaterialTheme(colorScheme = scheme) {
            val explorerVm: ExplorerViewModel = viewModel()
            NexoraRoot(
                vm = explorerVm,
                colors = nexoraColors,
                onToggleTheme = { val next = if (isDark) "light" else "dark"; themeMode = next; fileDatabase.setSetting("theme_mode", next) },
                actions = NexoraActions(
                    openLogin = { startActivity(Intent(this@MainActivity, LoginActivity::class.java)) },
                    pickUploadFiles = { pickUploadFiles.launch(arrayOf("*/*")) },
                    pickUploadFolder = { pickUploadFolder.launch(null) },
                    pickDownloadFolder = { pickFolder.launch(null) },
                    logoutAll = { logoutEverything() },
                    shareFile = { file, mime -> shareFile(file, mime) },
                    openFile = { file, mime -> openFile(file, mime) }
                ),
                legacy = { legacyName ->
                var legacyTab by remember(legacyName) { mutableStateOf(legacyName) }
                Column(Modifier.fillMaxSize()) {
                    if (legacyTab == "Galeri" || legacyTab == "Berkas") {
                        OutlinedTextField(
                            value = query, onValueChange = { query = it }, singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                            placeholder = { Text("Cari nama file...") }, leadingIcon = { Icon(Icons.Default.Search, null) },
                            shape = RoundedCornerShape(28.dp)
                        )
                    }
                    when (legacyTab) {
                        "Chat" -> ChatHome(
                            fileCount = files.size,
                            cloudStats = cloudStats,
                            recentActivity = dashboardActivity,
                            status = dashboardStatus,
                            busy = dashboardBusy,
                            onRefresh = { scope.launch {
                                dashboardBusy = true
                                dashboardStatus = "Mengambil ringkasan dari backend..."
                                val results = withContext(Dispatchers.IO) {
                                    Pair(runCatching { gateway.cloudStats() }, runCatching { gateway.listActivity("all", 5) })
                                }
                                results.first.onSuccess { cloudStats = it }
                                results.second.onSuccess { dashboardActivity = it }
                                dashboardStatus = when {
                                    results.first.isSuccess && results.second.isSuccess -> "Ringkasan berhasil diperbarui."
                                    results.first.isFailure -> "Statistik cloud gagal: ${results.first.exceptionOrNull()?.message}"
                                    else -> "Aktivitas terbaru gagal: ${results.second.exceptionOrNull()?.message}"
                                }
                                dashboardBusy = false
                            } },
                            onGallery = { legacyTab = "Galeri" }, onFiles = { legacyTab = "Berkas" }, onStorage = { legacyTab = "Storage" },
                            onCloud = { legacyTab = "Cloud" }, onActivity = { legacyTab = "Aktivitas" }
                        )
                        "Galeri" -> GalleryScreen(files.filter { it.name.contains(query, true) }, filter, compactMode, { showFilter = true }, { selectedItem = it })
                        "Berkas" -> FilesScreen(files.filter { it.name.contains(query, true) }, compactMode, duplicateGroups, fileDatabase, File(filesDir, "berkas"), { refreshFiles++; selectedItem = it })
                        "Storage" -> StorageScreen(
                            selectedFolder?.toString(), backendUrl, { backendUrl = it }, backendKey, { backendKey = it },
                            backendStatus, backendBusy,
                            onSaveBackend = { backendClient.save(backendUrl, backendKey); backendStatus = "Pengaturan backend disimpan di penyimpanan privat aplikasi." },
                            onTestBackend = { scope.launch { backendBusy = true; backendStatus = runCatching { withContext(Dispatchers.IO) { backendClient.testConnection() } }.fold({ it }, { "Gagal: ${it.message}" }); backendBusy = false } },
                            onTestTelegram = { scope.launch { backendBusy = true; backendStatus = runCatching { withContext(Dispatchers.IO) { gateway.telegramStatus() } }.fold({ it }, { "Gagal: ${it.message}" }); backendBusy = false } },
                            onChoose = { pickFolder.launch(null) },
                            breakdown = storageBreakdown, catalogStats = storageCatalogStats, recentActivity = storageRecentActivity,
                            statsStatus = storageStatsStatus, statsBusy = storageStatsBusy,
                            onLoadStats = { scope.launch {
                                storageStatsBusy = true; storageStatsStatus = null
                                val results = withContext(Dispatchers.IO) {
                                    Triple(runCatching { gateway.storageBreakdown() }, runCatching { gateway.cloudStats() }, runCatching { gateway.listActivity("all", 5) })
                                }
                                results.first.onSuccess { storageBreakdown = it }.onFailure { storageStatsStatus = "Statistik kapasitas gagal: ${it.message}" }
                                results.second.onSuccess { storageCatalogStats = it }.onFailure { storageStatsStatus = "Statistik kategori gagal: ${it.message}" }
                                results.third.onSuccess { storageRecentActivity = it }.onFailure { if (storageStatsStatus == null) storageStatsStatus = "Aktivitas terbaru gagal dimuat: ${it.message}" }
                                if (results.first.isSuccess && results.second.isSuccess && results.third.isSuccess) storageStatsStatus = "Statistik dan aktivitas berhasil diperbarui."
                                storageStatsBusy = false
                            } },
                            since = csvSince, onSince = { csvSince = it }, until = csvUntil, onUntil = { csvUntil = it },
                            csvBusy = csvBusy, onExportCsv = { scope.launch {
                                csvBusy = true
                                runCatching {
                                    val since = csvSince.trim().takeIf { it.isNotBlank() }?.let { java.time.LocalDate.parse(it).atStartOfDay(java.time.ZoneId.of("Asia/Jakarta")).format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME) }.orEmpty()
                                    val until = csvUntil.trim().takeIf { it.isNotBlank() }?.let { java.time.LocalDate.parse(it).plusDays(1).atStartOfDay(java.time.ZoneId.of("Asia/Jakarta")).format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME) }.orEmpty()
                                    withContext(Dispatchers.IO) { gateway.exportActivityCsv(since, until) }
                                }.onSuccess { pendingActivityCsv = it; createActivityCsv.launch("nexora-activity-${System.currentTimeMillis()}.csv") }
                                    .onFailure { Toast.makeText(this@MainActivity, "Ekspor gagal: ${it.message}. Format tanggal gunakan YYYY-MM-DD.", Toast.LENGTH_LONG).show() }
                                csvBusy = false
                            } }
                        )
                        "Cloud" -> CloudScreen(
                            cloudFiles = cloudFiles,
                            localFiles = files,
                            loading = cloudLoading,
                            status = cloudStatus,
                            stats = cloudStats,
                            hasMore = cloudHasMore,
                            emptyFolders = cloudEmptyFolders, folderTools = cloudFolderTools, sourceLabel = cloudSourceLabel,
                            onMove = { item, path -> folderOp("Pindahkan") { gateway.moveCloudFile(item.id, path) } },
                            onCreateFolder = { path -> folderOp("Buat folder") { gateway.createFolder(path) } },
                            onRenameFolder = { from, to -> folderOp("Ganti nama folder") { gateway.renameFolder(from, to) } },
                            onDeleteFolder = { path -> folderOp("Hapus folder") { gateway.deleteFolder(path) } },
                            onRefresh = { search, type, sort ->
                                scope.launch {
                                    cloudLoading = true
                                    cloudStatus = null
                                    runCatching { withContext(Dispatchers.IO) { gateway.listCloudFiles(100, 0, search, type, sort) } }
                                        .onSuccess { cloudFiles = it; cloudOffset = it.size; cloudHasMore = it.size == 100; cloudStatus = "${it.size} file dimuat dari $cloudSourceLabel. Gunakan Muat lagi untuk halaman berikutnya." }
                                        .onFailure { cloudStatus = "Gagal memuat cloud: ${it.message}" }
                                    runCatching { withContext(Dispatchers.IO) { gateway.cloudStats() } }.onSuccess { cloudStats = it }
                                    cloudFolderTools = gateway.supportsFolders(); cloudSourceLabel = gateway.sourceLabel()
                                    cloudEmptyFolders = withContext(Dispatchers.IO) { gateway.emptyFolders() }
                                    cloudLoading = false
                                }
                            },
                            onLoadMore = { search, type, sort ->
                                if (!cloudLoading && cloudHasMore) scope.launch {
                                    cloudLoading = true
                                    runCatching { withContext(Dispatchers.IO) { gateway.listCloudFiles(100, cloudOffset, search, type, sort) } }
                                        .onSuccess { cloudFiles = cloudFiles + it; cloudOffset += it.size; cloudHasMore = it.size == 100; cloudStatus = "${cloudFiles.size} file telah dimuat." }
                                        .onFailure { cloudStatus = "Gagal memuat halaman berikutnya: ${it.message}" }
                                    cloudLoading = false
                                }
                            },
                            onPreview = { item ->
                                scope.launch {
                                    cloudPreviewBusy = true
                                    cloudStatus = "Mengambil pratinjau ${item.name}..."
                                    val result = runCatching { withContext(Dispatchers.IO) {
                                        val safe = item.name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(100)
                                        val cached = File(cacheDir, "preview_${item.id}_$safe")
                                        cached.outputStream().use { output -> gateway.downloadCloudFile(item.id, output) }
                                        cached
                                    } }
                                    result.onSuccess { cached ->
                                        if (item.mimeType.startsWith("image/")) {
                                            val bitmap = withContext(Dispatchers.IO) {
                                                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                                BitmapFactory.decodeFile(cached.absolutePath, bounds)
                                                var sample = 1
                                                while (bounds.outWidth / sample > 1200 || bounds.outHeight / sample > 1200) sample *= 2
                                                BitmapFactory.decodeFile(cached.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
                                            }
                                            if (bitmap != null) { cloudPreviewBitmap = bitmap; cloudPreviewName = item.name; cloudStatus = "Pratinjau siap." }
                                            else cloudStatus = "Gambar tidak dapat dibaca atau formatnya tidak didukung."
                                        } else {
                                            val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", cached)
                                            val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, item.mimeType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            runCatching { startActivity(Intent.createChooser(intent, "Buka dengan")) }
                                                .onFailure { cloudStatus = "Tidak ada aplikasi yang dapat membuka format ini: ${it.message}" }
                                        }
                                    }.onFailure { cloudStatus = "Pratinjau gagal: ${it.message}" }
                                    cloudPreviewBusy = false
                                }
                            },
                            onDownload = { item ->
                                val folderUri = selectedFolder
                                if (folderUri == null) cloudStatus = "Pilih lokasi unduhan di menu Storage terlebih dahulu."
                                else {
                                    val transferId = fileDatabase.addTransfer(item.name, "Unduhan cloud")
                                    val request = OneTimeWorkRequestBuilder<CloudTransferWorker>()
                                        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                                        .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, java.util.concurrent.TimeUnit.SECONDS)
                                        .setInputData(Data.Builder().putLong(CloudTransferWorker.KEY_TRANSFER_ID, transferId)
                                            .putString(CloudTransferWorker.KEY_ACTION, CloudTransferWorker.ACTION_DOWNLOAD)
                                            .putLong(CloudTransferWorker.KEY_CLOUD_ID, item.id).putString(CloudTransferWorker.KEY_TREE_URI, folderUri.toString())
                                            .putString(CloudTransferWorker.KEY_NAME, item.name).putString(CloudTransferWorker.KEY_MIME, item.mimeType).build())
                                        .addTag("nexora-cloud-transfer-$transferId").build()
                                    fileDatabase.attachWork(transferId, request.id.toString())
                                    WorkManager.getInstance(this@MainActivity).enqueue(request)
                                    cloudStatus = "Unduhan ${item.name} masuk antrean. Pantau di menu Transfer."
                                    refreshFiles++
                                }
                            },
                            onSoftDelete = { item -> scope.launch {
                                cloudLoading = true
                                runCatching { withContext(Dispatchers.IO) { gateway.softDeleteCloudFile(item.id) } }
                                    .onSuccess { cloudStatus = "${item.name} dipindahkan ke Sampah."; cloudFiles = cloudFiles.filterNot { it.id == item.id }; cloudStats = withContext(Dispatchers.IO) { gateway.cloudStats() }; refreshFiles++ }
                                    .onFailure { cloudStatus = "Gagal memindahkan file: ${it.message}" }
                                cloudLoading = false
                            } },
                            onBulkDownload = { items ->
                                val folderUri = selectedFolder
                                if (folderUri == null) cloudStatus = "Pilih lokasi unduhan di menu Storage terlebih dahulu."
                                else {
                                    val workManager = WorkManager.getInstance(this@MainActivity)
                                    items.forEach { item ->
                                        val transferId = fileDatabase.addTransfer(item.name, "Unduhan cloud (massal)")
                                        val request = OneTimeWorkRequestBuilder<CloudTransferWorker>()
                                            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                                            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, java.util.concurrent.TimeUnit.SECONDS)
                                            .setInputData(Data.Builder().putLong(CloudTransferWorker.KEY_TRANSFER_ID, transferId)
                                                .putString(CloudTransferWorker.KEY_ACTION, CloudTransferWorker.ACTION_DOWNLOAD)
                                                .putLong(CloudTransferWorker.KEY_CLOUD_ID, item.id).putString(CloudTransferWorker.KEY_TREE_URI, folderUri.toString())
                                                .putString(CloudTransferWorker.KEY_NAME, item.name).putString(CloudTransferWorker.KEY_MIME, item.mimeType).build())
                                            .addTag("nexora-cloud-transfer-$transferId").build()
                                        fileDatabase.attachWork(transferId, request.id.toString())
                                        workManager.enqueue(request)
                                    }
                                    cloudStatus = "${items.size} file masuk antrean unduhan. Pantau progres di menu Transfer."
                                    refreshFiles++
                                }
                            }
                        )
                        "Sampah" -> TrashScreen(
                            items = trashFiles, status = trashStatus, busy = trashBusy, canPurge = gateway.supportsFolders(),
                            onPurge = { item -> scope.launch { trashBusy = true
                                runCatching { withContext(Dispatchers.IO) { gateway.purgeCloudFile(item.id); gateway.listTrash() } }
                                    .onSuccess { trashFiles = it; trashStatus = "${item.name} dihapus permanen dari Telegram." }
                                    .onFailure { trashStatus = "Hapus permanen gagal: ${it.message}" }
                                trashBusy = false
                            } },
                            onRefresh = { scope.launch { trashBusy = true; trashStatus = null
                                runCatching { withContext(Dispatchers.IO) { gateway.listTrash() } }
                                    .onSuccess { trashFiles = it; trashStatus = "${it.size} item di arsip terhapus (maksimal 200)." }
                                    .onFailure { trashStatus = "Gagal memuat sampah: ${it.message}" }
                                trashBusy = false
                            } },
                            onRestore = { item -> scope.launch { trashBusy = true
                                runCatching { withContext(Dispatchers.IO) { gateway.restoreTrashFile(item.id) } }
                                    .onSuccess { trashStatus = "${item.name} berhasil dipulihkan."; trashFiles = withContext(Dispatchers.IO) { gateway.listTrash() }; refreshFiles++ }
                                    .onFailure { trashStatus = "Pemulihan gagal: ${it.message}" }
                                trashBusy = false
                            } }
                        )
                        "Aktivitas" -> ActivityScreen(
                            items = activityItems, status = activityStatus, busy = activityBusy,
                            selectedFilter = activityFilter,
                            onFilter = { activityFilter = it; scope.launch {
                                activityBusy = true; activityStatus = null
                                runCatching { withContext(Dispatchers.IO) { gateway.listActivity(it, 200) } }
                                    .onSuccess { activityItems = it; activityStatus = "${it.size} aktivitas dimuat." }
                                    .onFailure { activityStatus = "Gagal memuat aktivitas: ${it.message}" }
                                activityBusy = false
                            } },
                            onRefresh = { scope.launch {
                                activityBusy = true; activityStatus = null
                                runCatching { withContext(Dispatchers.IO) { gateway.listActivity(activityFilter, 200) } }
                                    .onSuccess { activityItems = it; activityStatus = "${it.size} aktivitas dimuat." }
                                    .onFailure { activityStatus = "Gagal memuat aktivitas: ${it.message}" }
                                activityBusy = false
                            } }
                        )
                        "Transfer" -> TransferScreen(fileDatabase.listTransfers(), onClearHistory = {
                            val removed = fileDatabase.clearFinishedTransfers(); transferMessage = "$removed riwayat selesai dihapus."; refreshFiles++
                        }, onCancel = { record ->
                            val workId = fileDatabase.workId(record.id)
                            if (workId != null) runCatching {
                                WorkManager.getInstance(this@MainActivity).cancelWorkById(java.util.UUID.fromString(workId))
                                fileDatabase.updateTransfer(record.id, "Dibatalkan", "Pembatalan diminta")
                                refreshFiles++
                            }
                        })
                        "Personalisasi" -> PersonalizationScreen(
                            accentHex = accentHex,
                            onAccent = { accentHex = it; fileDatabase.setSetting("accent_color", it) },
                            compactMode = compactMode,
                            onCompact = { compactMode = it; fileDatabase.setSetting("compact_mode", it.toString()) },
                            confirmDelete = confirmDelete,
                            onConfirmDelete = { confirmDelete = it; fileDatabase.setSetting("confirm_delete", it.toString()) },
                            defaultSort = defaultSort,
                            onDefaultSort = { defaultSort = it; fileDatabase.setSetting("default_sort", it) },
                            archivePattern = archivePattern,
                            onArchivePattern = { archivePattern = it; fileDatabase.setSetting("archive_pattern", it) },
                            appLanguage = appLanguage,
                            onLanguage = { appLanguage = it; fileDatabase.setSetting("app_language", it) },
                            transferNotifications = transferNotifications,
                            onTransferNotifications = {
                                transferNotifications = it
                                fileDatabase.setSetting("transfer_notifications", it.toString())
                                if (it && android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                    notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            themeMode = themeMode,
                            onThemeMode = { themeMode = it; fileDatabase.setSetting("theme_mode", it) },
                            onExportBackup = { exportDatabaseBackup() },
                            onRestoreBackup = { restoreLauncher.launch(arrayOf("application/vnd.sqlite3", "application/octet-stream", "*/*")) },
                            settingCount = fileDatabase.allSettings().size,
                            onClearCache = {
                                val internalCleared = cacheDir.listFiles()?.count { it.deleteRecursively() } ?: 0
                                val externalCleared = externalCacheDir?.listFiles()?.count { it.deleteRecursively() } ?: 0
                                Toast.makeText(this@MainActivity, "Cache sementara dibersihkan ($internalCleared + $externalCleared folder). Database dan file pengguna tidak dihapus.", Toast.LENGTH_LONG).show()
                            }
                        )
                        "Akun" -> AccountDeviceScreen(
                            backendUrl = backendUrl,
                            localFileCount = files.size,
                            transferCount = fileDatabase.listTransfers().size,
                            deviceId = fileDatabase.getSetting("device_local_id").ifBlank {
                                java.util.UUID.randomUUID().toString().also { fileDatabase.setSetting("device_local_id", it) }
                            },
                            savedDeviceName = fileDatabase.getSetting("device_display_name", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim()),
                            lastActive = fileDatabase.getSetting("device_last_active"),
                            recentTransfers = fileDatabase.listTransfers().take(5),
                            onSaveDeviceName = { fileDatabase.setSetting("device_display_name", it.trim()) },
                            telegramStatus = telegramAccountStatus,
                            status = accountCheckMessage,
                            busy = accountCheckBusy,
                            onCheckBackend = { scope.launch {
                                accountCheckBusy = true; accountCheckMessage = "Memeriksa backend..."
                                accountCheckMessage = runCatching { withContext(Dispatchers.IO) { backendClient.testConnection() } }
                                    .fold({ it }, { "Backend tidak terhubung: ${it.message}" })
                                accountCheckBusy = false
                            } },
                            onCheckTelegram = { scope.launch {
                                accountCheckBusy = true; accountCheckMessage = "Memeriksa status akun Telegram..."
                                val result = withContext(Dispatchers.IO) { runCatching { gateway.telegramAccountAuthStatus() } }
                                result.onSuccess { telegramAccountStatus = it; accountCheckMessage = it.message.ifBlank { if (it.ready) "Akun Telegram siap digunakan." else "Akun Telegram belum siap." } }
                                    .onFailure { accountCheckMessage = "Pemeriksaan Telegram gagal: ${it.message}" }
                                accountCheckBusy = false
                            } }
                        )
                        "Konfigurasi" -> ConfigurationScreen(
                            backendUrl = backendUrl,
                            onExport = {
                                val providers = getSharedPreferences("nexora_providers", MODE_PRIVATE)
                                val obj = JSONObject().put("app", "Nexora Cloud").put("schema_version", 1)
                                    .put("backend_url", backendUrl.trim().trimEnd('/'))
                                    .put("download_folder_uri", selectedFolder?.toString().orEmpty())
                                    .put("providers", JSONObject().put("telegram", providers.getBoolean("telegram", true))
                                        .put("google_drive", providers.getBoolean("google_drive", false)).put("onedrive", providers.getBoolean("onedrive", false))
                                        .put("webdav", providers.getBoolean("webdav", false)).put("s3", providers.getBoolean("s3", false)))
                                    .put("security_note", "API key, token Telegram, dan kredensial database tidak diekspor.")
                                pendingConfigJson = obj.toString(2); createConfigFile.launch("nexora-config.json")
                            },
                            onImport = { openConfigFile.launch(arrayOf("application/json", "text/json")) },
                            onBackup = { exportDatabaseBackup() },
                            onTest = { scope.launch { backendBusy = true; backendStatus = runCatching { withContext(Dispatchers.IO) { backendClient.testConnection() } }.fold({ it }, { "Gagal: ${it.message}" }); backendBusy = false } },
                            status = backendStatus, busy = backendBusy
                        )
                        else -> MenuScreen(
                            onChooseFolder = { pickFolder.launch(null) }, folder = selectedFolder?.toString(), count = files.size, duplicateGroups = duplicateGroups,
                            onConfiguration = { legacyTab = "Konfigurasi" }, onPersonalization = { legacyTab = "Personalisasi" }, onBackup = { exportDatabaseBackup() }, onAccount = { legacyTab = "Akun" }
                        )
                    }
                }
            })
            if (cloudPreviewBusy) AlertDialog(onDismissRequest = { }, title = { Text("Menyiapkan pratinjau") }, text = { Column { Text("File sedang diambil dari cloud."); LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 10.dp)) } }, confirmButton = {})
            cloudPreviewBitmap?.let { bitmap ->
                AlertDialog(onDismissRequest = { cloudPreviewBitmap = null }, title = { Text(cloudPreviewName, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    text = { Image(bitmap.asImageBitmap(), contentDescription = cloudPreviewName, modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp), contentScale = ContentScale.Fit) },
                    confirmButton = { TextButton(onClick = { cloudPreviewBitmap = null }) { Text("Tutup") } })
            }
            if (showFilter) AlertDialog(onDismissRequest = { showFilter = false }, title = { Text("Filter jenis file") }, text = {
                Column { listOf("Semua", "Foto", "Video", "Audio", "Dokumen", "Arsip", "Aplikasi", "Lainnya").forEach { value ->
                    Row(Modifier.fillMaxWidth().clickable { filter = value; showFilter = false }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = filter == value, onClick = { filter = value; showFilter = false }); Text(value)
                    }
                } }
            }, confirmButton = { TextButton(onClick = { showFilter = false }) { Text("Tutup") } })

            selectedItem?.let { item ->
                AlertDialog(
                    onDismissRequest = { selectedItem = null },
                    title = { Text(item.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    text = {
                        Column {
                            if (item.ext in imageExt) {
                                val preview = remember(item.path) { runCatching {
                                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                    BitmapFactory.decodeFile(item.path, bounds)
                                    var sample = 1
                                    while (bounds.outWidth / sample > 900 || bounds.outHeight / sample > 900) sample *= 2
                                    BitmapFactory.decodeFile(item.path, BitmapFactory.Options().apply { inSampleSize = sample })
                                }.getOrNull() }
                                preview?.let { Image(it.asImageBitmap(), contentDescription = item.name, modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp), contentScale = ContentScale.Fit) }
                            }
                            Text("Ukuran: ${sizeText(item.size)}")
                            Text("Waktu: ${dateTime(item.time)}")
                            Text("Sumber: Pustaka lokal Nexora")
                            Spacer(Modifier.height(8.dp))
                            Text("File akan disalin ke folder unduhan yang sudah kamu pilih. File asli di pustaka Nexora tidak dihapus.", color = Muted, fontSize = 12.sp)
                        }
                    },
                    confirmButton = {
                        Row {
                            TextButton(enabled = !backendBusy, onClick = {
                                val current = item
                                selectedItem = null
                                scope.launch {
                                    val transferId = withContext(Dispatchers.IO) { fileDatabase.addTransfer(current.name, "Unggahan cloud") }
                                    val request = OneTimeWorkRequestBuilder<CloudTransferWorker>()
                                        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                                        .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, java.util.concurrent.TimeUnit.SECONDS)
                                        .setInputData(Data.Builder().putLong(CloudTransferWorker.KEY_TRANSFER_ID, transferId)
                                            .putString(CloudTransferWorker.KEY_ACTION, CloudTransferWorker.ACTION_UPLOAD)
                                            .putString(CloudTransferWorker.KEY_SOURCE_PATH, current.path).build())
                                        .addTag("nexora-cloud-transfer-$transferId").build()
                                    fileDatabase.attachWork(transferId, request.id.toString())
                                    WorkManager.getInstance(this@MainActivity).enqueue(request)
                                    transferMessage = "Upload ${current.name} masuk antrean cloud. Pantau progres di menu Transfer."
                                    refreshFiles++
                                }
                            }) { Text("Upload cloud") }
                            TextButton(onClick = {
                                val current = item
                                selectedItem = null
                                scope.launch {
                                    val folderUri = selectedFolder
                                    if (folderUri == null) { transferMessage = "Pilih lokasi unduhan terlebih dahulu."; return@launch }
                                    val transferId = withContext(Dispatchers.IO) { fileDatabase.addTransfer(current.name, "Unduhan lokal") }
                                    val request = OneTimeWorkRequestBuilder<LocalCopyWorker>()
                                        .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 20, java.util.concurrent.TimeUnit.SECONDS)
                                        .setInputData(Data.Builder().putLong(LocalCopyWorker.KEY_TRANSFER_ID, transferId)
                                            .putString(LocalCopyWorker.KEY_SOURCE_PATH, current.path).putString(LocalCopyWorker.KEY_TREE_URI, folderUri.toString()).build())
                                        .addTag("nexora-transfer-$transferId").build()
                                    fileDatabase.attachWork(transferId, request.id.toString())
                                    WorkManager.getInstance(this@MainActivity).enqueue(request)
                                    transferMessage = "Unduhan ditambahkan ke antrean."; refreshFiles++
                                }
                            }) { Text("Simpan lokal") }
                        }
                    },
                    dismissButton = { TextButton(onClick = { selectedItem = null }) { Text("Tutup") } }
                )
            }
            transferMessage?.let { message ->
                AlertDialog(onDismissRequest = { transferMessage = null }, confirmButton = { TextButton(onClick = { transferMessage = null }) { Text("OK") } }, text = { Text(message) })
            }
        }
    }

    @Composable private fun Header(query: String, onQuery: (String) -> Unit, onLogin: () -> Unit) {
        Column(Modifier.fillMaxWidth().background(SurfaceColor).padding(horizontal = 18.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).background(Pale, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.Cloud, null, tint = Blue) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Nexora Cloud", fontWeight = FontWeight.Bold, color = Ink, fontSize = 19.sp)
                    Text("Cloud pribadi • hybrid Telegram", color = Muted, fontSize = 11.sp)
                }
                TextButton(onClick = onLogin) {
                    Icon(Icons.Default.AccountCircle, contentDescription = null, tint = Blue)
                    Spacer(Modifier.width(4.dp))
                    Text("Login", color = Blue, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(value = query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text("Cari nama file...") }, leadingIcon = { Icon(Icons.Default.Search, null) }, shape = RoundedCornerShape(28.dp))
        }
    }

    @Composable private fun ChatHome(
        fileCount: Int, cloudStats: CloudCatalogStats?, recentActivity: List<ActivityLogItem>,
        status: String?, busy: Boolean, onRefresh: () -> Unit,
        onGallery: () -> Unit, onFiles: () -> Unit, onStorage: () -> Unit,
        onCloud: () -> Unit, onActivity: () -> Unit
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Dasbor Nexora", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text("Ringkasan penyimpanan dan aktivitas", color = Muted, fontSize = 12.sp)
                }
                IconButton(onClick = onRefresh, enabled = !busy) { Icon(Icons.Default.Refresh, "Perbarui ringkasan", tint = Blue) }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DashboardMetric("File lokal", fileCount.toString(), Icons.Default.Folder, Modifier.weight(1f))
                DashboardMetric("File cloud", cloudStats?.totalFiles?.toString() ?: "—", Icons.Default.CloudQueue, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DashboardMetric("Kapasitas cloud", cloudStats?.let { sizeText(it.totalBytes) } ?: "Belum dimuat", Icons.Default.Storage, Modifier.weight(1f))
                DashboardMetric("Media", cloudStats?.let { (it.images + it.videos + it.audios).toString() } ?: "—", Icons.Default.PermMedia, Modifier.weight(1f))
            }
            status?.let { Text(it, color = Muted, fontSize = 11.sp, modifier = Modifier.padding(vertical = 8.dp)) }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 4.dp))
            Card(Modifier.fillMaxWidth().padding(top = 10.dp), colors = CardDefaults.cardColors(containerColor = SurfaceColor)) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Aktivitas terbaru", color = Ink, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        TextButton(onClick = onActivity) { Text("Lihat semua") }
                    }
                    if (recentActivity.isEmpty()) Text("Belum ada data aktivitas. Tekan tombol perbarui untuk mengambil data backend.", color = Muted, fontSize = 11.sp)
                    else recentActivity.take(5).forEach { item ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(when (item.action) { "upload" -> Icons.Default.CloudUpload; "delete" -> Icons.Default.DeleteOutline; "restore" -> Icons.Default.Restore; else -> Icons.Default.History }, null, tint = Blue)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.fileName.ifBlank { item.action }, color = Ink, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${item.action} • ${item.createdAt}", color = Muted, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            ChatRow("Saved Messages", "Simpan dan temukan kembali file pribadimu", Icons.Default.Bookmark, "$fileCount file", onFiles)
            ChatRow("Nexora Cloud", "Pusat penyimpanan dan transfer file", Icons.Default.Cloud, "Buka cloud", onCloud)
            ChatRow("Storage", "Statistik kapasitas dan pengaturan backend", Icons.Default.Storage, "Buka storage", onStorage)
            ChatRow("Galeri Media", "Foto, video, audio, dan dokumen berdasarkan waktu", Icons.Default.PhotoLibrary, "Lihat galeri", onGallery)
        }
    }

    @Composable private fun DashboardMetric(title: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
        Card(modifier, colors = CardDefaults.cardColors(containerColor = SurfaceColor)) {
            Column(Modifier.padding(13.dp)) {
                Icon(icon, null, tint = Blue)
                Spacer(Modifier.height(8.dp))
                Text(value, color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(title, color = Muted, fontSize = 11.sp)
            }
        }
    }

    @Composable private fun ChatRow(title: String, subtitle: String, icon: ImageVector, trailing: String, onClick: () -> Unit) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).background(Pale, CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Blue, modifier = Modifier.size(25.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { Text(title, color = Ink, fontWeight = FontWeight.SemiBold); Text(subtitle, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Text(trailing, color = Blue, fontSize = 10.sp)
        }
        HorizontalDivider(color = Color(0xFFE8EDF1))
    }

    @Composable private fun GalleryScreen(items: List<LocalItem>, filter: String, compactMode: Boolean, onFilter: () -> Unit, onOpen: (LocalItem) -> Unit) {
        var sortMode by remember { mutableStateOf("Terbaru") }
        var showSort by remember { mutableStateOf(false) }
        val sortedItems = when (sortMode) {
            "Nama" -> items.sortedBy { it.name.lowercase(Locale.ROOT) }
            "Terbesar" -> items.sortedByDescending { it.size }
            "Terkecil" -> items.sortedBy { it.size }
            else -> items.sortedByDescending { it.time }
        }
        val visible = sortedItems.filter { item -> when (filter) {
            "Foto" -> item.ext in imageExt
            "Video" -> item.ext in videoExt
            "Audio" -> item.ext in audioExt
            "Dokumen" -> item.ext in documentExt
            "Arsip" -> item.ext in archiveExt
            "Aplikasi" -> item.ext in setOf("apk", "aab")
            "Lainnya" -> item.ext !in knownExt
            else -> true
        } }
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Galeri universal", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink); Text("Tanggal → jam • semua jenis file", fontSize = 11.sp, color = Muted) }
                AssistChip(onClick = onFilter, label = { Text(filter) }, leadingIcon = { Icon(Icons.Default.FilterList, null) })
                Spacer(Modifier.width(6.dp))
                Box {
                    AssistChip(onClick = { showSort = true }, label = { Text(sortMode) }, leadingIcon = { Icon(Icons.Default.Sort, null) })
                    DropdownMenu(expanded = showSort, onDismissRequest = { showSort = false }) {
                        listOf("Terbaru", "Nama", "Terbesar", "Terkecil").forEach { mode ->
                            DropdownMenuItem(text = { Text(mode) }, onClick = { sortMode = mode; showSort = false })
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${visible.size} file • ${sizeText(visible.sumOf { it.size })}", color = Muted, fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                Text("Urut: $sortMode", color = Muted, fontSize = 11.sp)
            }
            if (visible.isEmpty()) EmptyState("Belum ada file", "Tekan Unggah untuk memilih file dari perangkat. Semua tipe file akan dikelompokkan berdasarkan tanggal dan jam.")
            else {
                val grouped = visible.groupBy { dayKey(it.time) }
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                    grouped.entries.sortedByDescending { entry -> entry.value.maxOfOrNull { it.time } ?: 0L }.forEach { (day, dayItems) ->
                        Text(day, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.padding(top = 12.dp, bottom = 5.dp))
                        dayItems.groupBy { hourKey(it.time) }.entries.sortedByDescending { it.key }.forEach { (hour, hourItems) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(hour, fontWeight = FontWeight.Medium, color = Ink); Spacer(Modifier.width(8.dp)); Text("${hourItems.size} file", color = Muted, fontSize = 12.sp)
                            }
                            LazyVerticalGrid(columns = GridCells.Fixed(if (compactMode) 4 else 3), modifier = Modifier.fillMaxWidth().height((((hourItems.size + (if (compactMode) 3 else 2)) / (if (compactMode) 4 else 3)) * (if (compactMode) 88 else 112)).coerceAtLeast(1).dp), userScrollEnabled = false,
                                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(hourItems.sortedByDescending { it.time }) { item -> FileTile(item, compactMode) { onOpen(item) } }
                            }
                        }
                    }
                    Spacer(Modifier.height(90.dp))
                }
            }
        }
    }

    @Composable private fun FileTile(item: LocalItem, compactMode: Boolean, onClick: () -> Unit) {
        val icon = fileIcon(item.ext)
        val thumbnail = remember(item.path, item.time) {
            if (item.ext in imageExt) runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(item.path, bounds)
                var sample = 1
                while (bounds.outWidth / sample > 480 || bounds.outHeight / sample > 480) sample *= 2
                BitmapFactory.decodeFile(item.path, BitmapFactory.Options().apply { inSampleSize = sample })
            }.getOrNull() else null
        }
        Column(Modifier.fillMaxWidth().clickable(onClick = onClick).background(SurfaceColor, RoundedCornerShape(12.dp)).padding(if (compactMode) 5.dp else 9.dp)) {
            Box(Modifier.fillMaxWidth().height(if (compactMode) 54.dp else 78.dp).background(Pale, RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
                if (thumbnail != null) Image(bitmap = thumbnail.asImageBitmap(), contentDescription = item.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(icon, null, tint = Blue, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.height(if (compactMode) 3.dp else 6.dp)); Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = if (compactMode) 9.sp else 11.sp, color = Ink); Text(sizeText(item.size), fontSize = if (compactMode) 8.sp else 10.sp, color = Muted)
        }
    }

    @Composable private fun FilesScreen(items: List<LocalItem>, compactMode: Boolean, duplicateGroups: Int, db: LocalFileDatabase, library: File, onOpen: (LocalItem) -> Unit) {
        val context = androidx.compose.ui.platform.LocalContext.current
        var category by remember { mutableStateOf("Semua") }
        var sortMode by remember { mutableStateOf("Terbaru") }
        var smartQuery by remember { mutableStateOf("") }
        var smartFilter by remember { mutableStateOf("Semua") }
        var selectedPaths by remember { mutableStateOf(setOf<String>()) }
        var detailItem by remember { mutableStateOf<LocalItem?>(null) }
        var renameItem by remember { mutableStateOf<LocalItem?>(null) }
        var renameText by remember { mutableStateOf("") }
        var trashItem by remember { mutableStateOf<LocalItem?>(null) }
        var showTrash by remember { mutableStateOf(false) }
        var showInsights by remember { mutableStateOf(false) }
        var favoriteOnly by remember { mutableStateOf(false) }
        var confirmBulkTrash by remember { mutableStateOf(false) }
        val favoritePaths = remember(refreshFiles) { db.favoritePaths() }
        var trashRows by remember { mutableStateOf(emptyList<TrashRecord>()) }
        val now = System.currentTimeMillis()
        val categorized = items.filter { item ->
            val categoryMatch = when (category) {
                "Foto" -> item.ext in imageExt; "Video" -> item.ext in videoExt; "Audio" -> item.ext in audioExt
                "Dokumen" -> item.ext in documentExt; "Arsip" -> item.ext in archiveExt; else -> true
            }
            val queryMatch = smartQuery.isBlank() || item.name.contains(smartQuery.trim(), true) || item.ext.contains(smartQuery.trim().removePrefix("."), true) || item.path.contains(smartQuery.trim(), true)
            val quickMatch = when (smartFilter) {
                "Besar >100 MB" -> item.size >= 100L * 1024L * 1024L
                "7 hari terakhir" -> item.time >= now - 7L * 24L * 60L * 60L * 1000L
                "Tanpa ekstensi" -> item.ext.isBlank()
                else -> true
            }
            categoryMatch && queryMatch && quickMatch && (!favoriteOnly || item.path in favoritePaths)
        }
        val visible = when (sortMode) {
            "Nama" -> categorized.sortedBy { it.name.lowercase(Locale.ROOT) }
            "Terbesar" -> categorized.sortedByDescending { it.size }
            "Terkecil" -> categorized.sortedBy { it.size }
            else -> categorized.sortedByDescending { it.time }
        }
        val selected = visible.filter { it.path in selectedPaths }
        Column(Modifier.fillMaxSize().padding(18.dp)) {
            Text("Pengelola berkas", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text("${visible.size} file • ${sizeText(visible.sumOf { it.size })} • katalog lokal", color = Muted)
            OutlinedTextField(
                value = smartQuery, onValueChange = { smartQuery = it }, modifier = Modifier.fillMaxWidth(),
                singleLine = true, label = { Text("Cari nama, ekstensi, atau lokasi") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = { if (smartQuery.isNotEmpty()) IconButton(onClick = { smartQuery = "" }) { Icon(Icons.Default.Close, "Hapus pencarian") } }
            )
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Semua", "Besar >100 MB", "7 hari terakhir", "Tanpa ekstensi").forEach { value ->
                    FilterChip(selected = smartFilter == value, onClick = { smartFilter = value }, label = { Text(value) })
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { runCatching { db.reconcile(library) }.onSuccess { refreshFiles++; Toast.makeText(context, "Pemeriksaan katalog selesai.", Toast.LENGTH_SHORT).show() }.onFailure { Toast.makeText(context, it.message ?: "Pemeriksaan gagal", Toast.LENGTH_LONG).show() } }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(5.dp)); Text("Periksa katalog")
                }
                OutlinedButton(onClick = { showInsights = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.BarChart, null); Spacer(Modifier.width(6.dp)); Text("Analisis penyimpanan")
                }
                OutlinedButton(onClick = { trashRows = db.listTrash(); showTrash = true }) {
                    Icon(Icons.Default.DeleteOutline, null); Spacer(Modifier.width(4.dp)); Text("Sampah (${db.listTrash().size})")
                }
            }
            if (duplicateGroups > 0) Row(Modifier.fillMaxWidth().padding(top = 8.dp).background(Pale, RoundedCornerShape(10.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ContentCopy, null, tint = Blue); Spacer(Modifier.width(8.dp))
                Text("${duplicateGroups} kelompok file memiliki hash SHA-256 identik. Periksa sebelum menghapus salinan.", color = Ink, fontSize = 11.sp)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Semua", "Foto", "Video", "Audio", "Dokumen", "Arsip").forEach { value -> FilterChip(selected = category == value, onClick = { category = value; selectedPaths = emptySet() }, label = { Text(value) }) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Terbaru", "Nama", "Terbesar", "Terkecil").forEach { value -> FilterChip(selected = sortMode == value, onClick = { sortMode = value }, label = { Text(value) }) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = favoriteOnly, onClick = { favoriteOnly = !favoriteOnly; selectedPaths = emptySet() }, label = { Text("★ Favorit (${favoritePaths.size})") })
                TextButton(onClick = { selectedPaths = if (selected.size == visible.size) emptySet() else visible.map { it.path }.toSet() }) { Text(if (selected.size == visible.size && visible.isNotEmpty()) "Batal pilih semua" else "Pilih semua") }
            }
            if (selected.isNotEmpty()) {
                Card(Modifier.fillMaxWidth().padding(vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = Pale)) {
                    Row(Modifier.padding(10.dp).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                        Text("${selected.size} dipilih", color = Ink, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = {
                            runCatching {
                                val uris = ArrayList<Uri>()
                                selected.forEach { item -> uris += FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(item.path)) }
                                val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply { type = "*/*"; putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                                context.startActivity(Intent.createChooser(intent, "Bagikan berkas terpilih"))
                            }.onFailure { Toast.makeText(context, "Tidak dapat membagikan: ${it.message}", Toast.LENGTH_LONG).show() }
                        }) { Text("Bagikan") }
                        TextButton(onClick = { confirmBulkTrash = true }) { Text("Ke Sampah") }
                        TextButton(onClick = { selectedPaths = emptySet() }) { Text("Batal") }
                    }
                }
            }
            if (visible.isEmpty()) EmptyState("Tidak ada file", "Pilih kategori lain atau gunakan tombol Unggah.") else LazyColumn(verticalArrangement = Arrangement.spacedBy(if (compactMode) 3.dp else 7.dp)) {
                items(visible.size) { index ->
                    val item = visible[index]
                    val isSelected = item.path in selectedPaths
                    Row(Modifier.fillMaxWidth().background(if (isSelected) Pale else SurfaceColor, RoundedCornerShape(12.dp)).padding(if (compactMode) 7.dp else 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isSelected, onCheckedChange = { checked -> selectedPaths = if (checked) selectedPaths + item.path else selectedPaths - item.path })
                        Icon(fileIcon(item.ext), null, tint = Blue, modifier = Modifier.clickable { detailItem = item })
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f).clickable { detailItem = item }) {
                            Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Ink, fontSize = if (compactMode) 12.sp else 14.sp)
                            Text("${sizeText(item.size)} • ${dateTime(item.time)}", color = Muted, fontSize = if (compactMode) 9.sp else 11.sp)
                        }
                        IconButton(onClick = { runCatching { db.toggleFavorite(item.path, library) }.onSuccess { refreshFiles++ }.onFailure { Toast.makeText(context, it.message ?: "Gagal", Toast.LENGTH_SHORT).show() } }) { Icon(if (item.path in favoritePaths) Icons.Default.Star else Icons.Default.StarBorder, "Favorit", tint = if (item.path in favoritePaths) Blue else Muted) }
                        IconButton(onClick = { onOpen(item) }) { Icon(Icons.Default.Visibility, "Buka", tint = Muted) }
                    }
                }
            }
        }
        if (confirmBulkTrash) AlertDialog(
            onDismissRequest = { confirmBulkTrash = false },
            title = { Text("Pindahkan ${selected.size} berkas ke Sampah?") },
            text = { Text("Berkas terpilih akan dipindahkan ke Sampah privat Nexora dan masih dapat dipulihkan.") },
            confirmButton = { TextButton(onClick = {
                val results = selected.map { item -> runCatching { db.moveToTrash(item.path, library, File(library.parentFile, "sampah")) }.getOrDefault(false) }
                val moved = results.count { it }
                selectedPaths = emptySet(); confirmBulkTrash = false; refreshFiles++
                Toast.makeText(context, "$moved dari ${results.size} berkas dipindahkan ke Sampah.", Toast.LENGTH_LONG).show()
            }) { Text("Pindahkan") } },
            dismissButton = { TextButton(onClick = { confirmBulkTrash = false }) { Text("Batal") } }
        )

        detailItem?.let { item ->
            AlertDialog(onDismissRequest = { detailItem = null }, title = { Text("Detail berkas") },
                text = { Column {
                    Text(item.name, fontWeight = FontWeight.SemiBold)
                    Text("Ukuran: ${sizeText(item.size)}")
                    Text("Diubah: ${dateTime(item.time)}")
                    Text("Jenis: ${item.ext.ifBlank { "tanpa ekstensi" }}")
                    val file = File(item.path)
                    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(item.ext)
                    Text("MIME: ${mime ?: "tidak diketahui"}", fontSize = 12.sp)
                    Text("Status: ${if (file.exists()) "tersedia" else "tidak ditemukan"} • ${if (file.canRead()) "dapat dibaca" else "tidak dapat dibaca"}", fontSize = 12.sp)
                    Text("Lokasi: ${item.path}", fontSize = 11.sp)
                } },
                confirmButton = { TextButton(onClick = {
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Lokasi berkas", item.path))
                    Toast.makeText(context, "Lokasi disalin", Toast.LENGTH_SHORT).show()
                }) { Text("Salin lokasi") } },
                dismissButton = { Row {
                    TextButton(onClick = { renameText = item.name; renameItem = item; detailItem = null }) { Text("Ganti nama") }
                    TextButton(onClick = { trashItem = item; detailItem = null }) { Text("Ke Sampah") }
                    TextButton(onClick = { detailItem = null }) { Text("Tutup") }
                } })
        }

        renameItem?.let { item ->
            AlertDialog(onDismissRequest = { renameItem = null }, title = { Text("Ganti nama berkas") },
                text = { OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true, label = { Text("Nama baru") }) },
                confirmButton = { TextButton(onClick = {
                    runCatching { check(db.renameLocalFile(item.path, renameText, library)) { "Tidak dapat mengganti nama." } }
                        .onSuccess { Toast.makeText(context, "Nama berkas diperbarui", Toast.LENGTH_SHORT).show(); renameItem = null; refreshFiles++ }
                        .onFailure { Toast.makeText(context, it.message ?: "Gagal", Toast.LENGTH_LONG).show() }
                }) { Text("Simpan") } },
                dismissButton = { TextButton(onClick = { renameItem = null }) { Text("Batal") } })
        }
        trashItem?.let { item ->
            AlertDialog(onDismissRequest = { trashItem = null }, title = { Text("Pindahkan ke Sampah?") },
                text = { Text("${item.name} akan dipindahkan ke folder Sampah privat Nexora. File tidak langsung dihapus dan dapat dipulihkan.") },
                confirmButton = { TextButton(onClick = {
                    runCatching { check(db.moveToTrash(item.path, library, File(library.parentFile, "sampah"))) { "Pemindahan gagal." } }
                        .onSuccess { Toast.makeText(context, "Berkas dipindahkan ke Sampah", Toast.LENGTH_SHORT).show(); trashItem = null; refreshFiles++ }
                        .onFailure { Toast.makeText(context, it.message ?: "Gagal", Toast.LENGTH_LONG).show() }
                }) { Text("Pindahkan") } },
                dismissButton = { TextButton(onClick = { trashItem = null }) { Text("Batal") } })
        }
        if (showTrash) AlertDialog(onDismissRequest = { showTrash = false }, title = { Text("Sampah Nexora") },
            text = { Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                if (trashRows.isEmpty()) Text("Sampah kosong") else trashRows.forEach { row ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(row.originalName, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(dateTime(row.deletedAt), color = Muted, fontSize = 10.sp) }
                        TextButton(onClick = { runCatching { check(db.restoreFromTrash(row.id, library)) }; trashRows = db.listTrash(); refreshFiles++ }) { Text("Pulihkan") }
                    }
                }
            } }, confirmButton = { TextButton(onClick = { showTrash = false }) { Text("Tutup") } })
        if (showInsights) {
            val totalBytes = items.sumOf { it.size }
            val categories = listOf(
                "Foto" to items.count { it.ext in imageExt },
                "Video" to items.count { it.ext in videoExt },
                "Audio" to items.count { it.ext in audioExt },
                "Dokumen" to items.count { it.ext in documentExt },
                "Arsip" to items.count { it.ext in archiveExt },
                "Lainnya" to items.count { it.ext !in imageExt && it.ext !in videoExt && it.ext !in audioExt && it.ext !in documentExt && it.ext !in archiveExt }
            )
            val extensions = items.groupBy { it.ext.ifBlank { "(tanpa ekstensi)" } }
                .mapValues { entry -> entry.value.size to entry.value.sumOf { it.size } }
                .toList().sortedByDescending { it.second.second }.take(8)
            val largest = items.sortedByDescending { it.size }.take(5)
            AlertDialog(
                onDismissRequest = { showInsights = false },
                title = { Text("Analisis penyimpanan") },
                text = {
                    Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                        Text("Ringkasan pustaka lokal", fontWeight = FontWeight.SemiBold, color = Ink)
                        Text("${items.size} file • ${sizeText(totalBytes)}", color = Muted)
                        Spacer(Modifier.height(12.dp))
                        Text("Kategori", fontWeight = FontWeight.SemiBold, color = Ink)
                        categories.forEach { (label, count) ->
                            if (count > 0) Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(label, color = Ink); Text("$count file", color = Muted)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("Jenis file terbesar berdasarkan total ukuran", fontWeight = FontWeight.SemiBold, color = Ink)
                        extensions.forEach { (ext, stats) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(".$ext (${stats.first})", color = Ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(sizeText(stats.second), color = Muted)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("5 file terbesar", fontWeight = FontWeight.SemiBold, color = Ink)
                        if (largest.isEmpty()) Text("Belum ada file di katalog.", color = Muted)
                        largest.forEachIndexed { index, item ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Text("${index + 1}. ${item.name}", color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(sizeText(item.size), color = Muted, fontSize = 11.sp)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Duplikasi: $duplicateGroups kelompok hash identik terdeteksi pada katalog. Nexora tidak menghapus file otomatis.", color = Muted, fontSize = 11.sp)
                        Text("Cakupan: hanya file yang sudah diimpor ke pustaka privat Nexora, bukan seluruh penyimpanan Android atau isi Telegram Cloud.", color = Muted, fontSize = 11.sp)
                    }
                },
                confirmButton = { TextButton(onClick = { showInsights = false }) { Text("Tutup") } }
            )
        }
    }

    @Composable private fun SyncMetric(label: String, value: Int) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value.toString(), color = Ink, fontWeight = FontWeight.Bold, fontSize = 19.sp)
            Text(label, color = Muted, fontSize = 9.sp)
        }
    }

    @Composable private fun TransferScreen(records: List<TransferRecord>, onClearHistory: () -> Unit, onCancel: (TransferRecord) -> Unit) {
        val waiting = records.count { it.status == "Menunggu" }
        val running = records.count { it.status == "Berjalan" }
        val completed = records.count { it.status == "Selesai" }
        val failed = records.count { it.status == "Gagal" }
        Column(Modifier.fillMaxSize().padding(18.dp)) {
            Text("Cloud Sync Center", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text("Pusat kendali antrean transfer lokal dan cloud melalui WorkManager", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Pale), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Ringkasan sinkronisasi", color = Ink, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        SyncMetric("Menunggu", waiting)
                        SyncMetric("Berjalan", running)
                        SyncMetric("Selesai", completed)
                        SyncMetric("Gagal", failed)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("${records.size} catatan terakhir • maksimal 100 catatan ditampilkan", color = Muted, fontSize = 10.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Transfer tertunda akan diproses oleh WorkManager sesuai kondisi sistem. Koneksi cloud memerlukan backend aktif dan konfigurasi akun yang benar.", color = Muted, fontSize = 11.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onClearHistory, enabled = records.any { it.status == "Selesai" || it.status == "Gagal" || it.status == "Dibatalkan" }) { Icon(Icons.Default.DeleteSweep, null); Spacer(Modifier.width(4.dp)); Text("Bersihkan riwayat selesai") }
            }
            Spacer(Modifier.height(6.dp))
            if (records.isEmpty()) EmptyState("Belum ada transfer", "Saat kamu menyimpan file ke lokasi pilihan, aktivitasnya akan muncul di sini.")
            else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(records.size) { index ->
                    val record = records[index]
                    Card(colors = CardDefaults.cardColors(containerColor = SurfaceColor)) {
                        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (record.status == "Selesai") Icons.Default.CheckCircle else if (record.status == "Gagal") Icons.Default.Error else Icons.Default.Sync, null, tint = Blue)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(record.name, color = Ink, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${record.direction} • ${record.status}", color = Muted, fontSize = 11.sp)
                                Text(record.message, color = Muted, fontSize = 10.sp)
                                if (record.status == "Berjalan" || record.status == "Menunggu") {
                                    Spacer(Modifier.height(6.dp))
                                    LinearProgressIndicator(progress = { record.progress / 100f }, modifier = Modifier.fillMaxWidth())
                                    Text("${record.progress}% • ${record.bytesDone} / ${record.bytesTotal} byte", color = Muted, fontSize = 10.sp)
                                    TextButton(onClick = { onCancel(record) }) { Icon(Icons.Default.Close, null); Spacer(Modifier.width(4.dp)); Text("Batalkan transfer") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable private fun CloudScreen(
        cloudFiles: List<CloudFileItem>, localFiles: List<LocalItem>, loading: Boolean, status: String?,
        stats: CloudCatalogStats?, hasMore: Boolean, onRefresh: (String, String, String) -> Unit,
        onLoadMore: (String, String, String) -> Unit, onPreview: (CloudFileItem) -> Unit,
        onDownload: (CloudFileItem) -> Unit, onBulkDownload: (List<CloudFileItem>) -> Unit, onSoftDelete: (CloudFileItem) -> Unit,
        emptyFolders: List<String>, folderTools: Boolean, sourceLabel: String,
        onMove: (CloudFileItem, String) -> Unit, onCreateFolder: (String) -> Unit,
        onRenameFolder: (String, String) -> Unit, onDeleteFolder: (String) -> Unit
    ) {
        var newFolderDialog by remember { mutableStateOf(false) }
        var folderNameText by remember { mutableStateOf("") }
        var renameFolderTarget by remember { mutableStateOf<String?>(null) }
        var moveItem by remember { mutableStateOf<CloudFileItem?>(null) }
        var moveText by remember { mutableStateOf("") }
        var archiveFolder by remember { mutableStateOf("") }
        var cloudSearch by remember { mutableStateOf("") }
        var cloudType by remember { mutableStateOf("Semua") }
        var sortBySize by remember { mutableStateOf(false) }
        var detailItem by remember { mutableStateOf<CloudFileItem?>(null) }
        var confirmDeleteItem by remember { mutableStateOf<CloudFileItem?>(null) }
        var selectionMode by remember { mutableStateOf(false) }
        var selectedIds by remember { mutableStateOf(setOf<Long>()) }
        val currentParts = archiveFolder.split("/").filter { it.isNotBlank() }
        val visibleFiles = cloudFiles.filter { item ->
            val parts = item.archivePath.split("/").filter { it.isNotBlank() }
            parts == currentParts &&
                item.name.contains(cloudSearch, true) &&
                (cloudType == "Semua" ||
                 (cloudType == "Gambar" && item.mimeType.startsWith("image/")) ||
                 (cloudType == "Video" && item.mimeType.startsWith("video/")) ||
                 (cloudType == "Audio" && item.mimeType.startsWith("audio/")) ||
                 (cloudType == "Dokumen" && (item.mimeType.startsWith("text/") || item.mimeType == "application/pdf")))
        }.let { list -> if (sortBySize) list.sortedByDescending { it.byteSize } else list.sortedByDescending { it.createdAt } }
        val allPaths = cloudFiles.map { it.archivePath.split("/").filter { p -> p.isNotBlank() } } +
            emptyFolders.map { it.split("/").filter { p -> p.isNotBlank() } }
        val nextFolders = allPaths.mapNotNull { parts ->
            if (parts.size > currentParts.size && parts.take(currentParts.size) == currentParts) parts[currentParts.size] else null
        }.distinct().sortedDescending()
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
            Text("Telegram Cloud", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text("Sumber: $sourceLabel. Folder adalah pengelompokan metadata (caption pesan), bukan folder fisik Telegram.", color = Muted, fontSize = 11.sp)
            TransferPanel()
            Spacer(Modifier.height(10.dp))
            Button(onClick = { onRefresh(cloudSearch, when (cloudType) { "Gambar" -> "image"; "Video" -> "video"; "Audio" -> "audio"; "Dokumen" -> "document"; else -> "all" }, if (sortBySize) "size" else "date") }, enabled = !loading) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Cari di seluruh cloud") }
            OutlinedTextField(value = cloudSearch, onValueChange = { cloudSearch = it }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true, label = { Text("Cari nama file di seluruh cloud") }, leadingIcon = { Icon(Icons.Default.Search, null) })
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Semua", "Gambar", "Video", "Audio", "Dokumen").forEach { kind ->
                    FilterChip(selected = cloudType == kind, onClick = { cloudType = kind }, label = { Text(kind, fontSize = 10.sp) })
                }
            }
            OutlinedButton(onClick = { sortBySize = !sortBySize }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Sort, null); Spacer(Modifier.width(6.dp)); Text(if (sortBySize) "Urutkan: ukuran terbesar" else "Urutkan: terbaru")
            }
            if (currentParts.size == 4 && visibleFiles.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { selectionMode = !selectionMode; selectedIds = emptySet() }) {
                        Icon(Icons.Default.Checklist, null); Spacer(Modifier.width(5.dp)); Text(if (selectionMode) "Selesai memilih" else "Pilih beberapa")
                    }
                    if (selectionMode) {
                        TextButton(onClick = { selectedIds = if (visibleFiles.all { it.id in selectedIds }) selectedIds - visibleFiles.map { it.id }.toSet() else selectedIds + visibleFiles.map { it.id }.toSet() }) {
                            Text(if (selectedIds.size == visibleFiles.size) "Batal semua" else "Pilih semua")
                        }
                    }
                }
                if (selectionMode) {
                    Button(onClick = { onBulkDownload(visibleFiles.filter { it.id in selectedIds }); selectedIds = emptySet(); selectionMode = false },
                        enabled = visibleFiles.any { it.id in selectedIds } && !loading, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp)); Text("Unduh ${visibleFiles.count { it.id in selectedIds }} file terpilih")
                    }
                }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
            status?.let { Text(it, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp)) }
            if (stats != null) {
                Card(Modifier.fillMaxWidth().padding(vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = Pale)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Ringkasan seluruh cloud", color = Ink, fontWeight = FontWeight.Bold)
                        Text("${stats.totalFiles} file • ${sizeText(stats.totalBytes)}", color = Blue, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("${stats.images} gambar • ${stats.videos} video • ${stats.audios} audio • ${stats.documents} dokumen", color = Muted, fontSize = 11.sp)
                        Text("${cloudFiles.size} file sedang dimuat di perangkat • ${localFiles.size} file lokal", color = Muted, fontSize = 11.sp)
                        stats.latestUpload?.let { Text("Unggahan terakhir: $it", color = Muted, fontSize = 10.sp) }
                    }
                }
            }
            if (folderTools) OutlinedButton(onClick = { folderNameText = ""; newFolderDialog = true }) { Text("Folder baru") }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                Text("Arsip", color = Blue, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { archiveFolder = "" })
                currentParts.forEachIndexed { index, part ->
                    Text("  /  ", color = Muted)
                    Text(part, color = if (index == currentParts.lastIndex) Ink else Blue,
                        modifier = Modifier.clickable { archiveFolder = currentParts.take(index + 1).joinToString("/") })
                }
                if (currentParts.isNotEmpty()) Text("   ← Kembali", color = Blue, modifier = Modifier.clickable { archiveFolder = currentParts.dropLast(1).joinToString("/") })
            }
            run {
                nextFolders.forEach { folder ->
                    val prefix = (currentParts + folder).joinToString("/")
                    val count = cloudFiles.count { it.archivePath.split("/").take(currentParts.size + 1) == currentParts + folder }
                    Card(onClick = { archiveFolder = prefix }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = SurfaceColor)) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Folder, null, tint = Blue, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(folder, color = Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text("$count file termuat", color = Muted, fontSize = 11.sp)
                            if (folderTools) {
                                IconButton(onClick = { folderNameText = prefix; renameFolderTarget = prefix }) { Icon(Icons.Default.Edit, "Ganti nama folder", tint = Muted) }
                                IconButton(onClick = { onDeleteFolder(prefix) }) { Icon(Icons.Default.Delete, "Hapus folder", tint = Muted) }
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = Muted)
                        }
                    }
                }
            }
            run {
                if (visibleFiles.isEmpty() && nextFolders.isEmpty() && !loading) Text("Folder ini kosong.", color = Muted)
                visibleFiles.forEach { item ->
                    Card(onClick = { if (selectionMode) selectedIds = if (item.id in selectedIds) selectedIds - item.id else selectedIds + item.id else detailItem = item }, modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp), colors = CardDefaults.cardColors(containerColor = SurfaceColor)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (selectionMode) Checkbox(checked = item.id in selectedIds, onCheckedChange = { checked -> selectedIds = if (checked) selectedIds + item.id else selectedIds - item.id })
                            else Icon(Icons.Default.InsertDriveFile, null, tint = Blue, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.name, color = Ink, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("${sizeText(item.byteSize)} • ${item.mimeType}", color = Muted, fontSize = 10.sp)
                                Text("${item.createdAt} WIB", color = Muted, fontSize = 10.sp)
                            }
                            if (!selectionMode) IconButton(onClick = { onDownload(item) }, enabled = !loading) { Icon(Icons.Default.Download, "Unduh", tint = Blue) }
                        }
                    }
                }
            }
            if (visibleFiles.isEmpty() && nextFolders.isEmpty() && cloudFiles.isNotEmpty() && (cloudSearch.isNotBlank() || cloudType != "Semua")) Text("Tidak ada file yang cocok dengan pencarian atau filter pada folder ini.", color = Muted)
            if (hasMore) OutlinedButton(onClick = { onLoadMore(cloudSearch, when (cloudType) { "Gambar" -> "image"; "Video" -> "video"; "Audio" -> "audio"; "Dokumen" -> "document"; else -> "all" }, if (sortBySize) "size" else "date") }, enabled = !loading, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) { Text(if (loading) "Memuat..." else "Muat 100 file berikutnya") }
            if (cloudFiles.isEmpty() && !loading) Text("Belum ada data cloud. Tekan Muat ulang setelah backend tersambung.", color = Muted, modifier = Modifier.padding(top = 18.dp))
            Text("Ketuk kartu file untuk melihat detail metadata. File asli tetap tersimpan di Telegram.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp))
        }
        detailItem?.let { item ->
            AlertDialog(
                onDismissRequest = { detailItem = null },
                title = { Text("Detail berkas") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text(item.name, fontWeight = FontWeight.Bold, color = Ink)
                        Spacer(Modifier.height(8.dp))
                        Text("ID cloud: ${item.id}", fontSize = 12.sp, color = Muted)
                        Text("Jenis: ${item.mimeType}", fontSize = 12.sp, color = Muted)
                        Text("Ukuran: ${sizeText(item.byteSize)} (${item.byteSize} byte)", fontSize = 12.sp, color = Muted)
                        Text("Waktu: ${item.createdAt} WIB", fontSize = 12.sp, color = Muted)
                        Text("Arsip: ${item.archivePath.ifBlank { "Tidak tersedia" }}", fontSize = 12.sp, color = Muted)
                        Spacer(Modifier.height(8.dp))
                        Text("SHA-256", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Ink)
                        Text(item.sha256.ifBlank { "Tidak tersedia" }, fontSize = 10.sp, color = Muted)
                    }
                },
                confirmButton = { Row {
                    TextButton(onClick = { onPreview(item); detailItem = null }) { Text("Pratinjau") }
                    TextButton(onClick = { onDownload(item); detailItem = null }) { Text("Unduh") }
                    if (folderTools) TextButton(onClick = { moveText = item.archivePath; moveItem = item; detailItem = null }) { Text("Pindahkan") }
                    TextButton(onClick = { confirmDeleteItem = item; detailItem = null }) { Text("Pindahkan ke Sampah", color = Color(0xFFB54747)) }
                } },
                dismissButton = { TextButton(onClick = { detailItem = null }) { Text("Tutup") } }
            )
        }
        if (newFolderDialog) AlertDialog(
            onDismissRequest = { newFolderDialog = false },
            title = { Text("Folder baru") },
            text = { Column {
                Text("Dibuat di: Arsip${currentParts.joinToString("") { "/$it" }}", fontSize = 12.sp, color = Muted)
                OutlinedTextField(value = folderNameText, onValueChange = { folderNameText = it }, singleLine = true, label = { Text("Nama folder") })
            } },
            confirmButton = { TextButton(onClick = {
                if (folderNameText.isNotBlank()) onCreateFolder((currentParts + folderNameText.trim()).joinToString("/")); newFolderDialog = false
            }) { Text("Buat") } },
            dismissButton = { TextButton(onClick = { newFolderDialog = false }) { Text("Batal") } }
        )
        renameFolderTarget?.let { old ->
            AlertDialog(
                onDismissRequest = { renameFolderTarget = null },
                title = { Text("Ganti nama / pindahkan folder") },
                text = { OutlinedTextField(value = folderNameText, onValueChange = { folderNameText = it }, singleLine = true, label = { Text("Jalur baru (mis. Projek/2026)") }) },
                confirmButton = { TextButton(onClick = {
                    if (folderNameText.isNotBlank() && folderNameText.trim() != old) onRenameFolder(old, folderNameText.trim()); renameFolderTarget = null
                }) { Text("Simpan") } },
                dismissButton = { TextButton(onClick = { renameFolderTarget = null }) { Text("Batal") } }
            )
        }
        moveItem?.let { item ->
            AlertDialog(
                onDismissRequest = { moveItem = null },
                title = { Text("Pindahkan ke folder") },
                text = { Column {
                    Text(item.name, fontSize = 12.sp, color = Muted)
                    OutlinedTextField(value = moveText, onValueChange = { moveText = it }, singleLine = true, label = { Text("Jalur folder (kosong = akar)") })
                } },
                confirmButton = { TextButton(onClick = { onMove(item, moveText.trim()); moveItem = null }) { Text("Pindahkan") } },
                dismissButton = { TextButton(onClick = { moveItem = null }) { Text("Batal") } }
            )
        }
        confirmDeleteItem?.let { item ->
            AlertDialog(
                onDismissRequest = { confirmDeleteItem = null },
                title = { Text("Pindahkan ke Sampah?") },
                text = { Text("${item.name} akan disembunyikan dari katalog aktif dan dapat dipulihkan dari menu Sampah. File fisik di Telegram tidak dihapus.") },
                confirmButton = { TextButton(onClick = { onSoftDelete(item); confirmDeleteItem = null }) { Text("Pindahkan") } },
                dismissButton = { TextButton(onClick = { confirmDeleteItem = null }) { Text("Batal") } }
            )
        }
    }

    @Composable private fun ActivityScreen(
        items: List<ActivityLogItem>, status: String?, busy: Boolean, selectedFilter: String,
        onFilter: (String) -> Unit, onRefresh: () -> Unit
    ) {
        val filters = listOf("all" to "Semua", "upload" to "Upload", "download_requested" to "Unduh", "delete" to "Hapus", "restore" to "Pulihkan")
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
            Text("Riwayat aktivitas", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text("Catatan aktivitas dari backend Nexora. Waktu mengikuti server.", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                filters.forEach { (key, label) ->
                    FilterChip(selected = selectedFilter == key, onClick = { onFilter(key) }, label = { Text(label, fontSize = 11.sp) })
                }
            }
            OutlinedButton(onClick = onRefresh, enabled = !busy) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Muat ulang") }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
            status?.let { Text(it, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp)) }
            if (items.isEmpty() && !busy) Text("Belum ada aktivitas untuk filter ini. Muat ulang setelah backend aktif.", color = Muted, modifier = Modifier.padding(top = 12.dp))
            items.forEach { item ->
                val (label, icon) = when (item.action) {
                    "upload" -> "Upload" to Icons.Default.CloudUpload
                    "delete" -> "Dipindahkan ke Sampah" to Icons.Default.DeleteOutline
                    "restore" -> "Dipulihkan" to Icons.Default.Restore
                    "download_requested" -> "Permintaan unduh" to Icons.Default.Download
                    else -> item.action to Icons.Default.History
                }
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = SurfaceColor)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, tint = Blue, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(label, color = Ink, fontWeight = FontWeight.SemiBold)
                            Text(item.fileName, color = Ink, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("ID aktivitas #${item.id} • ${item.createdAt}", color = Muted, fontSize = 10.sp)
                        }
                    }
                }
            }
            Text("Maksimal 200 aktivitas terbaru. Permintaan unduh tidak selalu berarti transfer telah selesai.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp))
        }
    }

    /** Progres upload/download real-time dari [TdCloud.transfers]. */
    @Composable private fun TransferPanel() {
        val transfers by TdCloud.get(this@MainActivity).transfers.collectAsState()
        if (transfers.isEmpty()) return
        Card(Modifier.fillMaxWidth().padding(top = 10.dp), colors = CardDefaults.cardColors(containerColor = SurfaceColor)) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Transfer Telegram", color = Ink, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (transfers.any { it.finished }) TextButton(onClick = { TdCloud.get(this@MainActivity).clearFinishedTransfers() }) { Text("Bersihkan") }
                }
                transfers.takeLast(6).forEach { t ->
                    val label = if (t.direction == TdTransferDirection.UPLOAD) "Mengunggah" else "Mengunduh"
                    Text(t.name, color = Ink, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val status = when {
                        t.error != null -> "Gagal: ${t.error}"
                        t.finished -> "Selesai • ${sizeText(t.totalBytes)}"
                        else -> "$label ${sizeText(t.doneBytes)} / ${sizeText(t.totalBytes)} (${(t.fraction * 100).toInt()}%)"
                    }
                    Text(status, color = if (t.error != null) Color(0xFFB54747) else Muted, fontSize = 10.sp)
                    if (!t.finished) LinearProgressIndicator(progress = { t.fraction }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }

    @Composable private fun TrashScreen(items: List<TrashFileItem>, status: String?, busy: Boolean, onRefresh: () -> Unit, onRestore: (TrashFileItem) -> Unit,
        canPurge: Boolean = false, onPurge: (TrashFileItem) -> Unit = {}) {
        var purgeItem by remember { mutableStateOf<TrashFileItem?>(null) }
        purgeItem?.let { item ->
            AlertDialog(
                onDismissRequest = { purgeItem = null },
                title = { Text("Hapus permanen?") },
                text = { Text("${item.name} akan dihapus dari Telegram dan tidak dapat dipulihkan.") },
                confirmButton = { TextButton(onClick = { onPurge(item); purgeItem = null }) { Text("Hapus permanen", color = Color(0xFFB54747)) } },
                dismissButton = { TextButton(onClick = { purgeItem = null }) { Text("Batal") } }
            )
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
            Text("Sampah / Arsip terhapus", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text("File yang dihapus dari katalog aktif disimpan di sini. Pemulihan mengembalikannya ke katalog; file fisik Telegram tidak dipindahkan.", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            Button(onClick = onRefresh, enabled = !busy) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Muat arsip terhapus") }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
            status?.let { Text(it, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp)) }
            if (items.isEmpty() && !busy) Text("Belum ada data. Tekan Muat arsip terhapus untuk memeriksa backend.", color = Muted, modifier = Modifier.padding(top = 12.dp))
            items.forEach { item ->
                Card(Modifier.fillMaxWidth().padding(vertical = 5.dp), colors = CardDefaults.cardColors(containerColor = SurfaceColor)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DeleteOutline, null, tint = Muted, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.name, color = Ink, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("${sizeText(item.byteSize)} • ${item.mimeType}", color = Muted, fontSize = 10.sp)
                            Text("Dihapus: ${item.deletedAt}", color = Muted, fontSize = 10.sp)
                        }
                        TextButton(onClick = { onRestore(item) }, enabled = !busy) { Text("Pulihkan") }
                        if (canPurge) TextButton(onClick = { purgeItem = item }, enabled = !busy) { Text("Hapus", color = Color(0xFFB54747)) }
                    }
                }
            }
            Text("Batas endpoint saat ini: 200 item terhapus terbaru. Hapus permanen tersedia setelah login akun Telegram.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 12.dp))
        }
    }

    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    @Composable private fun StorageScreen(
        folder: String?, backendUrl: String, onBackendUrl: (String) -> Unit,
        backendKey: String, onBackendKey: (String) -> Unit, backendStatus: String?, backendBusy: Boolean,
        onSaveBackend: () -> Unit, onTestBackend: () -> Unit, onTestTelegram: () -> Unit, onChoose: () -> Unit,
        breakdown: StorageBreakdown?, catalogStats: CloudCatalogStats?, recentActivity: List<ActivityLogItem>,
        statsStatus: String?, statsBusy: Boolean, onLoadStats: () -> Unit,
        since: String, onSince: (String) -> Unit, until: String, onUntil: (String) -> Unit,
        csvBusy: Boolean, onExportCsv: () -> Unit
    ) {
        var showSincePicker by remember { mutableStateOf(false) }
        var showUntilPicker by remember { mutableStateOf(false) }
        val sincePickerState = rememberDatePickerState()
        val untilPickerState = rememberDatePickerState()
        if (showSincePicker) DatePickerDialog(
            onDismissRequest = { showSincePicker = false },
            confirmButton = { TextButton(onClick = { sincePickerState.selectedDateMillis?.let { onSince(java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()) }; showSincePicker = false }) { Text("Pilih") } },
            dismissButton = { TextButton(onClick = { showSincePicker = false }) { Text("Batal") } }
        ) { DatePicker(state = sincePickerState) }
        if (showUntilPicker) DatePickerDialog(
            onDismissRequest = { showUntilPicker = false },
            confirmButton = { TextButton(onClick = { untilPickerState.selectedDateMillis?.let { onUntil(java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()) }; showUntilPicker = false }) { Text("Pilih") } },
            dismissButton = { TextButton(onClick = { showUntilPicker = false }) { Text("Batal") } }
        ) { DatePicker(state = untilPickerState) }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
            Text("Pusat penyimpanan", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text("Hubungkan backend Nexora dan atur tujuan unduhan.", color = Muted)
            Spacer(Modifier.height(12.dp))
            Card(colors = CardDefaults.cardColors(containerColor = SurfaceColor)) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Analitik kapasitas cloud", color = Ink, fontWeight = FontWeight.Bold)
                            Text("Rekap metadata file aktif • zona Asia/Jakarta", color = Muted, fontSize = 11.sp)
                        }
                        Icon(Icons.Default.Analytics, null, tint = Blue, modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onLoadStats, enabled = !statsBusy, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text(if (statsBusy) "Memuat statistik..." else "Muat statistik cloud")
                    }
                    if (statsBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    statsStatus?.let { Text(it, color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp)) }
                    catalogStats?.let { stats ->
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricTile("Total katalog", stats.totalFiles.toString(), Modifier.weight(1f))
                            MetricTile("Ukuran katalog", formatBytes(stats.totalBytes), Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("Distribusi jenis file • seluruh katalog aktif", color = Ink, fontWeight = FontWeight.SemiBold)
                        val categories = listOf("Gambar" to stats.images, "Video" to stats.videos, "Audio" to stats.audios, "Dokumen" to stats.documents)
                        val maxCategory = categories.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L
                        categories.forEach { (label, count) ->
                            Column(Modifier.padding(top = 6.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(label, color = Ink, fontSize = 11.sp); Text(count.toString(), color = Muted, fontSize = 11.sp)
                                }
                                LinearProgressIndicator(progress = { (count.toFloat() / maxCategory).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(6.dp), color = Blue, trackColor = Pale)
                            }
                        }
                    }
                    breakdown?.let { data ->
                        val totalFiles = data.monthly.sumOf { it.totalFiles }
                        val totalBytes = data.monthly.sumOf { it.totalBytes }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricTile("File (24 bln)", totalFiles.toString(), Modifier.weight(1f))
                            MetricTile("Kapasitas (24 bln)", formatBytes(totalBytes), Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Pertumbuhan bulanan", color = Ink, fontWeight = FontWeight.SemiBold)
                        if (data.monthly.isEmpty()) Text("Belum ada data statistik.", color = Muted, fontSize = 12.sp)
                        else {
                            val maxBytes = data.monthly.maxOf { it.totalBytes }.coerceAtLeast(1L)
                            data.monthly.sortedBy { it.period }.takeLast(12).forEach { period ->
                                Column(Modifier.padding(top = 7.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(period.period, color = Ink, fontSize = 11.sp)
                                        Text("${period.totalFiles} file • ${formatBytes(period.totalBytes)}", color = Muted, fontSize = 10.sp)
                                    }
                                    LinearProgressIndicator(progress = { (period.totalBytes.toFloat() / maxBytes).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(7.dp), color = Blue, trackColor = Pale)
                                }
                            }
                        }
                        if (data.yearly.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp)); Text("Ringkasan tahunan", color = Ink, fontWeight = FontWeight.SemiBold)
                            data.yearly.forEach { period ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(period.period, color = Ink, fontSize = 12.sp)
                                    Text("${period.totalFiles} file • ${formatBytes(period.totalBytes)}", color = Muted, fontSize = 11.sp)
                                }
                            }
                        }
                        Text("Catatan: angka ini berasal dari metadata file Nexora, bukan kuota akun Telegram. Rekap bulanan dibatasi 24 periode dan tahunan 10 periode.", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                    if (recentActivity.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp)); Text("Aktivitas terbaru", color = Ink, fontWeight = FontWeight.SemiBold)
                        recentActivity.forEach { item ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.History, null, tint = Blue, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("${item.action.replace('_', ' ')} • ${item.fileName}", color = Ink, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(item.createdAt, color = Muted, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
            Card(Modifier.fillMaxWidth().padding(top = 10.dp), colors = CardDefaults.cardColors(containerColor = SurfaceColor)) {
                Column(Modifier.padding(14.dp)) {
                    Text("Ekspor riwayat aktivitas", color = Ink, fontWeight = FontWeight.Bold)
                    Text("CSV dapat dibuka dengan Excel atau aplikasi spreadsheet. Tanggal opsional, format YYYY-MM-DD.", color = Muted, fontSize = 11.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = since, onValueChange = onSince, modifier = Modifier.weight(1f), singleLine = true, label = { Text("Dari (YYYY-MM-DD)") })
                        IconButton(onClick = { showSincePicker = true }) { Icon(Icons.Default.CalendarMonth, "Pilih tanggal mulai", tint = Blue) }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = until, onValueChange = onUntil, modifier = Modifier.weight(1f), singleLine = true, label = { Text("Sampai (YYYY-MM-DD)") })
                        IconButton(onClick = { showUntilPicker = true }) { Icon(Icons.Default.CalendarMonth, "Pilih tanggal akhir", tint = Blue) }
                    }
                    Button(onClick = onExportCsv, enabled = !csvBusy, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.FileDownload, null); Spacer(Modifier.width(6.dp)); Text(if (csvBusy) "Menyiapkan CSV..." else "Ekspor semua aktivitas ke CSV")
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Card(colors = CardDefaults.cardColors(containerColor = SurfaceColor)) {
                Column(Modifier.padding(14.dp)) {
                    Text("Koneksi Nexora Backend", color = Ink, fontWeight = FontWeight.Bold)
                    Text("Masukkan alamat backend yang sudah kamu deploy. API key harus sama dengan NEXORA_API_KEY di server.", color = Muted, fontSize = 11.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = backendUrl, onValueChange = onBackendUrl, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("URL backend (https://...)") })
                    OutlinedTextField(value = backendKey, onValueChange = onBackendKey, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Nexora API key") })
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Button(onClick = onSaveBackend) { Text("Simpan") }
                        OutlinedButton(onClick = onTestBackend, enabled = !backendBusy) { Text("Tes backend") }
                    }
                    OutlinedButton(onClick = onTestTelegram, enabled = !backendBusy, modifier = Modifier.fillMaxWidth()) { Text("Periksa koneksi Telegram") }
                    if (backendBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    backendStatus?.let { Text(it, color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp)) }
                }
            }
            StorageCard("Penyimpanan perangkat", "Pustaka privat Nexora", Icons.Default.PhoneAndroid, "Aktif")
            StorageCard("Telegram Cloud", "Upload melalui backend; token bot tetap berada di server", Icons.Default.Send, "Perlu backend aktif")
            StorageCard("Google Drive / OneDrive", "Provider cloud tambahan", Icons.Default.CloudQueue, "Belum terhubung")
            StorageCard("WebDAV / S3 Compatible", "Server dan object storage", Icons.Default.Dns, "Belum terhubung")
            Spacer(Modifier.height(12.dp))
            Text("LOKASI UNDUHAN", fontWeight = FontWeight.Bold, color = Muted, fontSize = 12.sp)
            Card(colors = CardDefaults.cardColors(containerColor = SurfaceColor)) {
                Column(Modifier.padding(14.dp)) {
                    Text(if (folder == null) "Belum memilih folder" else "Folder tujuan tersimpan", color = Ink, fontWeight = FontWeight.SemiBold)
                    Text(folder ?: "Pilih folder internal, kartu SD, atau USB OTG.", color = Muted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onChoose) { Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text("Pilih lokasi unduhan") }
                }
            }
            Text("API key dienkripsi dengan Android Keystore pada perangkat. Token bot Telegram dan kredensial PostgreSQL hanya disimpan di backend. Gunakan HTTPS untuk server publik. File sumber tidak dihapus setelah upload.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp))
        }
    }

    @Composable private fun MetricTile(title: String, value: String, modifier: Modifier = Modifier) {
        Card(modifier, colors = CardDefaults.cardColors(containerColor = Pale), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(10.dp)) { Text(title, color = Muted, fontSize = 10.sp); Text(value, color = Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = listOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble(); var index = -1
        do { value /= 1024.0; index++ } while (value >= 1024 && index < units.lastIndex)
        return String.format(Locale.US, "%.1f %s", value, units[index])
    }

    @Composable private fun StorageCard(title: String, detail: String, icon: ImageVector, status: String) {
        Card(Modifier.fillMaxWidth().padding(vertical = 5.dp), colors = CardDefaults.cardColors(containerColor = SurfaceColor)) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = Blue, modifier = Modifier.size(27.dp)); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Text(title, color = Ink, fontWeight = FontWeight.SemiBold); Text(detail, color = Muted, fontSize = 11.sp) }
                Text(status, color = Muted, fontSize = 10.sp)
            }
        }
    }

    @Composable private fun MenuScreen(onChooseFolder: () -> Unit, folder: String?, count: Int, duplicateGroups: Int, onConfiguration: () -> Unit, onPersonalization: () -> Unit, onBackup: () -> Unit, onAccount: () -> Unit) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
            Text("Menu & pengaturan", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text("$count file tersimpan di pustaka lokal", color = Muted, fontSize = 12.sp)
            Card(Modifier.fillMaxWidth().padding(top = 12.dp), colors = CardDefaults.cardColors(containerColor = SurfaceColor)) {
                Column(Modifier.padding(14.dp)) {
                    Text("Indeks metadata lokal", color = Ink, fontWeight = FontWeight.SemiBold)
                    Text("Database katalog aktif • $duplicateGroups kelompok file dengan hash identik", color = Muted, fontSize = 11.sp)
                    Text("Pemeriksaan duplikat hanya mendeteksi kesamaan isi; file tidak dihapus otomatis.", color = Muted, fontSize = 10.sp)
                }
            }
            Row(Modifier.fillMaxWidth().clickable(onClick = onConfiguration).padding(vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Tune, null, tint = Blue); Spacer(Modifier.width(12.dp)); Text("Configuration Center", color = Ink, modifier = Modifier.weight(1f)); Icon(Icons.Default.ChevronRight, null, tint = Muted)
            }
            HorizontalDivider(color = Color(0xFFE8EDF1))
            Row(Modifier.fillMaxWidth().clickable(onClick = onPersonalization).padding(vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Palette, null, tint = Blue); Spacer(Modifier.width(12.dp)); Text("Personalisasi tampilan", color = Ink, modifier = Modifier.weight(1f)); Icon(Icons.Default.ChevronRight, null, tint = Muted)
            }
            HorizontalDivider(color = Color(0xFFE8EDF1))
            Row(Modifier.fillMaxWidth().clickable(onClick = onBackup).padding(vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Backup, null, tint = Blue); Spacer(Modifier.width(12.dp)); Text("Ekspor backup database", color = Ink, modifier = Modifier.weight(1f)); Icon(Icons.Default.ChevronRight, null, tint = Muted)
            }
            listOf("Akun dan koneksi", "Preferensi galeri", "Aturan unduhan", "Pencadangan otomatis", "Keamanan", "Panel debug", "Tentang Nexora Cloud").forEach { label ->
                Row(Modifier.fillMaxWidth().clickable { when (label) { "Akun dan koneksi" -> onAccount(); "Aturan unduhan" -> onChooseFolder() } }.padding(vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Settings, null, tint = Blue); Spacer(Modifier.width(12.dp)); Text(label, color = Ink, modifier = Modifier.weight(1f)); Icon(Icons.Default.ChevronRight, null, tint = Muted)
                }
                HorizontalDivider(color = Color(0xFFE8EDF1))
            }
            Text("Lokasi unduhan: ${if (folder == null) "belum diatur" else "sudah dipilih"}", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 12.dp))
        }
    }

    @Composable private fun AccountDeviceScreen(
        backendUrl: String, localFileCount: Int, transferCount: Int, deviceId: String,
        savedDeviceName: String, lastActive: String, recentTransfers: List<TransferRecord>,
        onSaveDeviceName: (String) -> Unit,
        telegramStatus: TelegramAccountAuthStatus?, status: String?, busy: Boolean,
        onCheckBackend: () -> Unit, onCheckTelegram: () -> Unit
    ) {
        var editableName by remember(savedDeviceName) { mutableStateOf(savedDeviceName) }
        var persistedName by remember(savedDeviceName) { mutableStateOf(savedDeviceName) }
        val deviceName = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim()
        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "tidak diketahui"
        val lastActiveText = lastActive.toLongOrNull()?.let { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")).format(Date(it)) } ?: "Belum tercatat"
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
            Text("Akun & perangkat", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text("Informasi perangkat dan pemeriksaan koneksi layanan.", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SurfaceColor)) {
                Column(Modifier.padding(15.dp)) {
                    Text("Perangkat ini", color = Ink, fontWeight = FontWeight.Bold)
                    Text(deviceName, color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 7.dp))
                    OutlinedTextField(value = editableName, onValueChange = { editableName = it.take(40) }, label = { Text("Nama perangkat") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                    OutlinedButton(onClick = { onSaveDeviceName(editableName); persistedName = editableName.trim() }, enabled = editableName.isNotBlank() && editableName.trim() != persistedName.trim(), modifier = Modifier.fillMaxWidth()) { Text("Simpan nama perangkat") }
                    Text("ID lokal: ${deviceId.take(8)}-••••-••••-••••-${deviceId.takeLast(4)}", color = Muted, fontSize = 11.sp)
                    Text("ID ini hanya identitas lokal aplikasi, belum didaftarkan ke server.", color = Muted, fontSize = 10.sp)
                    Text("Android ${android.os.Build.VERSION.RELEASE} • API ${android.os.Build.VERSION.SDK_INT}", color = Muted, fontSize = 12.sp)
                    Text("Nexora Cloud $version • $packageName", color = Muted, fontSize = 11.sp)
                    Text("Aktivitas perangkat terakhir: $lastActiveText", color = Muted, fontSize = 11.sp)
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))
                    Text("$localFileCount file lokal • $transferCount catatan transfer", color = Ink, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SurfaceColor)) {
                Column(Modifier.padding(15.dp)) {
                    Text("Riwayat sinkronisasi lokal", color = Ink, fontWeight = FontWeight.Bold)
                    Text("Menampilkan catatan transfer terbaru yang tersimpan di perangkat.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                    if (recentTransfers.isEmpty()) Text("Belum ada aktivitas transfer.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                    recentTransfers.forEach { item ->
                        Row(Modifier.fillMaxWidth().padding(top = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (item.direction.contains("upload", true)) Icons.Default.CloudUpload else Icons.Default.CloudDownload, null, tint = Blue, modifier = Modifier.size(19.dp))
                            Spacer(Modifier.width(9.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.name, color = Ink, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${item.direction} • ${item.status}", color = Muted, fontSize = 10.sp)
                            }
                            Text("${item.progress}%", color = Muted, fontSize = 10.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SurfaceColor)) {
                Column(Modifier.padding(15.dp)) {
                    Text("Koneksi backend", color = Ink, fontWeight = FontWeight.Bold)
                    Text(if (backendUrl.isBlank()) "URL backend belum dikonfigurasi." else backendUrl, color = Muted, fontSize = 12.sp)
                    OutlinedButton(onClick = onCheckBackend, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Uji koneksi") }
                }
            }
            Spacer(Modifier.height(10.dp))
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SurfaceColor)) {
                Column(Modifier.padding(15.dp)) {
                    Text("Akun Telegram", color = Ink, fontWeight = FontWeight.Bold)
                    Text(when {
                        telegramStatus == null -> "Status belum diperiksa."
                        !telegramStatus.configured -> "Autentikasi akun Telegram belum dikonfigurasi di backend."
                        telegramStatus.ready -> "Akun Telegram siap."
                        else -> "Akun Telegram sudah dikonfigurasi, tetapi belum siap."
                    }, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    OutlinedButton(onClick = onCheckTelegram, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Periksa status Telegram") }
                }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 10.dp))
            status?.let { Text(it, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp)) }
            Text("Catatan: halaman ini hanya membaca informasi perangkat dan status backend. Pengelolaan sesi jarak jauh belum tersedia karena backend saat ini belum menyediakan API daftar atau pencabutan sesi perangkat.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 14.dp))
        }
    }

    @Composable private fun ConfigurationScreen(
        backendUrl: String, onExport: () -> Unit, onImport: () -> Unit, onBackup: () -> Unit,
        onTest: () -> Unit, status: String?, busy: Boolean
    ) {
        val prefs = getSharedPreferences("nexora_providers", MODE_PRIVATE)
        var telegram by remember { mutableStateOf(prefs.getBoolean("telegram", true)) }
        var drive by remember { mutableStateOf(prefs.getBoolean("google_drive", false)) }
        var oneDrive by remember { mutableStateOf(prefs.getBoolean("onedrive", false)) }
        var webdav by remember { mutableStateOf(prefs.getBoolean("webdav", false)) }
        var s3 by remember { mutableStateOf(prefs.getBoolean("s3", false)) }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
            Text("Configuration Center", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text("Kelola konfigurasi perangkat, provider, dan pemeriksaan layanan.", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            Card(colors = CardDefaults.cardColors(containerColor = SurfaceColor)) {
                Column(Modifier.padding(14.dp)) {
                    Text("Backend Nexora", color = Ink, fontWeight = FontWeight.Bold)
                    Text(if (backendUrl.isBlank()) "URL backend belum diatur." else backendUrl, color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp)); OutlinedButton(onClick = onTest, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Uji koneksi backend") }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    status?.let { Text(it, color = Muted, fontSize = 11.sp) }
                }
            }
            Spacer(Modifier.height(10.dp))
            TelegramAppConfigurationCard()
            Spacer(Modifier.height(10.dp))
            Card(colors = CardDefaults.cardColors(containerColor = SurfaceColor)) {
                Column(Modifier.padding(14.dp)) {
                    Text("Manajer provider", color = Ink, fontWeight = FontWeight.Bold)
                    Text("Pilihan ini menyimpan preferensi provider. Status Aktif bukan berarti integrasi cloud sudah tersedia.", color = Muted, fontSize = 11.sp)
                    ProviderToggle("Telegram Cloud", "Provider backend utama", telegram, true) { telegram = it; prefs.edit().putBoolean("telegram", it).apply() }
                    ProviderToggle("Google Drive", "Provider tambahan", drive, false) { drive = it; prefs.edit().putBoolean("google_drive", it).apply() }
                    ProviderToggle("Microsoft OneDrive", "Provider tambahan", oneDrive, false) { oneDrive = it; prefs.edit().putBoolean("onedrive", it).apply() }
                    ProviderToggle("WebDAV", "Server file pribadi", webdav, false) { webdav = it; prefs.edit().putBoolean("webdav", it).apply() }
                    ProviderToggle("S3 Compatible", "Object storage", s3, false) { s3 = it; prefs.edit().putBoolean("s3", it).apply() }
                }
            }
            Spacer(Modifier.height(10.dp))
            Card(colors = CardDefaults.cardColors(containerColor = SurfaceColor)) {
                Column(Modifier.padding(14.dp)) {
                    Text("Migrasi & pencadangan", color = Ink, fontWeight = FontWeight.Bold)
                    Text("Ekspor/impor konfigurasi non-rahasia dalam JSON. API key dan rahasia server sengaja tidak disertakan.", color = Muted, fontSize = 11.sp)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) { Text("Ekspor konfigurasi JSON") }
                    OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text("Impor konfigurasi JSON") }
                    OutlinedButton(onClick = onBackup, modifier = Modifier.fillMaxWidth()) { Text("Ekspor backup database lokal") }
                }
            }
            Text("Keamanan: token bot Telegram dan kredensial PostgreSQL tetap berada di backend. Jangan memasukkan rahasia server ke file konfigurasi atau APK.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 12.dp))
        }
    }

    @Composable private fun TelegramAppConfigurationCard() {
        val context = this@MainActivity
        val initial = remember { TelegramAppConfiguration.load(context) }
        var appTitle by remember { mutableStateOf(initial.appTitle) }
        var shortName by remember { mutableStateOf(initial.shortName) }
        var platform by remember { mutableStateOf(initial.platform) }
        var appUrl by remember { mutableStateOf(initial.appUrl) }
        var apiId by remember { mutableStateOf(initial.apiId) }
        var apiHash by remember { mutableStateOf(initial.apiHash) }
        var testDc2 by remember { mutableStateOf(initial.testDc2) }
        var productionDc2 by remember { mutableStateOf(initial.productionDc2) }
        var message by remember { mutableStateOf<String?>(null) }
        var showResetConfirm by remember { mutableStateOf(false) }

        if (showResetConfirm) AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset konfigurasi bawaan?") },
            text = { Text("Semua kolom konfigurasi Telegram akan dikembalikan ke nilai pabrikan Nexora Cloud. Pengaturan backend dan data file tidak berubah.") },
            confirmButton = { TextButton(onClick = {
                TelegramAppConfiguration.reset(context)
                val defaults = TelegramAppConfiguration.FACTORY
                appTitle = defaults.appTitle; shortName = defaults.shortName; platform = defaults.platform
                appUrl = defaults.appUrl; apiId = defaults.apiId; apiHash = defaults.apiHash
                testDc2 = defaults.testDc2; productionDc2 = defaults.productionDc2
                message = "Konfigurasi bawaan berhasil dipulihkan."
                showResetConfirm = false
            }) { Text("Reset bawaan") } },
            dismissButton = { TextButton(onClick = { showResetConfirm = false }) { Text("Batal") } }
        )

        Card(colors = CardDefaults.cardColors(containerColor = SurfaceColor)) {
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Text("Konfigurasi Aplikasi Telegram", color = Ink, fontWeight = FontWeight.Bold)
                Text("Nilai awal Nexora Cloud dapat diubah kapan saja. API Hash disimpan terenkripsi di Android Keystore.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
                OutlinedTextField(appTitle, { appTitle = it.take(80) }, label = { Text("App Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(shortName, { shortName = it.take(64) }, label = { Text("Short Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(platform, { platform = it.take(32) }, label = { Text("Platform") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(appUrl, { appUrl = it.take(200) }, label = { Text("URL Aplikasi") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(apiId, { apiId = it.filter { ch -> ch.isDigit() }.take(20) }, label = { Text("App api_id") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(apiHash, { apiHash = it.trim().take(128) }, label = { Text("App api_hash") }, singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                HorizontalDivider(Modifier.padding(vertical = 10.dp))
                Text("MTProto • Data Center 2", color = Ink, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(testDc2, { testDc2 = it.take(100) }, label = { Text("Test Configuration (host:port)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(productionDc2, { productionDc2 = it.take(100) }, label = { Text("Production Configuration (host:port)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(onClick = {
                    val validUrl = runCatching { val u = java.net.URI(appUrl.trim()); u.scheme in listOf("https", "http") && !u.host.isNullOrBlank() }.getOrDefault(false)
                    val validDc = Regex("^[A-Za-z0-9.-]+:[0-9]{1,5}$")
                    val validPort = listOf(testDc2, productionDc2).all { value ->
                        val port = value.substringAfterLast(":", "").toIntOrNull()
                        validDc.matches(value.trim()) && port != null && port in 1..65535
                    }
                    when {
                        appTitle.isBlank() || shortName.isBlank() || platform.isBlank() -> message = "App Title, Short Name, dan Platform wajib diisi."
                        !validUrl -> message = "URL aplikasi tidak valid. Gunakan http:// atau https://."
                        apiId.isBlank() || apiId.toLongOrNull() == null -> message = "App api_id harus berupa angka."
                        !Regex("^[a-fA-F0-9]{32}$").matches(apiHash.trim()) -> message = "App api_hash harus terdiri dari 32 karakter heksadesimal."
                        !validPort -> message = "Alamat DC harus berformat host:port dengan port 1–65535."
                        else -> {
                            TelegramAppConfiguration.save(context, TelegramAppConfiguration(appTitle, shortName, platform, appUrl, apiId, apiHash, testDc2, productionDc2))
                            message = "Konfigurasi Telegram berhasil disimpan di perangkat."
                        }
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Simpan konfigurasi Telegram") }
                OutlinedButton(onClick = { showResetConfirm = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Restore, null); Spacer(Modifier.width(7.dp)); Text("Reset ke pabrikan / bawaan") }
                message?.let { Text(it, color = if (it.startsWith("Konfigurasi") || it.startsWith("Konfigurasi bawaan")) Color(0xFF23845B) else Color(0xFFB45309), fontSize = 11.sp, modifier = Modifier.padding(top = 7.dp)) }
                Text("Catatan: pengaturan ini menyimpan profil konfigurasi pada aplikasi. Backend Telegram Client API tetap perlu dikonfigurasi secara terpisah agar nilai api_id/api_hash dan DC benar-benar digunakan oleh mesin autentikasi.", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }

    private fun exportDatabaseBackup() {
        backupLauncher.launch("nexora-local-backup.db")
    }

    private val backupLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.sqlite3")) { uri ->
        if (uri != null) runCatching {
            fileDatabase.writableDatabase.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToFirst() }
            contentResolver.openOutputStream(uri)!!.use { output -> File(getDatabasePath("nexora_local.db").absolutePath).inputStream().use { it.copyTo(output) } }
        }.onSuccess { Toast.makeText(this, "Backup database lokal berhasil diekspor.", Toast.LENGTH_LONG).show() }
            .onFailure { Toast.makeText(this, "Backup gagal: ${it.message}", Toast.LENGTH_LONG).show() }
    }

    private val restoreLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            val temp = File(cacheDir, "nexora_restore_check.db")
            contentResolver.openInputStream(uri)!!.use { input -> temp.outputStream().use { input.copyTo(it) } }
            val header = temp.inputStream().use { ByteArray(16).also { b -> it.read(b) } }
            require(String(header, Charsets.US_ASCII).startsWith("SQLite format 3")) { "File bukan database SQLite yang valid." }
            fileDatabase.close()
            val live = getDatabasePath("nexora_local.db")
            File(live.absolutePath + "-wal").delete()
            File(live.absolutePath + "-shm").delete()
            live.delete()
            temp.copyTo(live, overwrite = true)
            temp.delete()
            LocalFileDatabase(this).writableDatabase.close()
        }.onSuccess { Toast.makeText(this, "Database dipulihkan. Nexora akan dimuat ulang.", Toast.LENGTH_LONG).show(); recreate() }
            .onFailure { Toast.makeText(this, "Pemulihan gagal: ${it.message}", Toast.LENGTH_LONG).show() }
    }

    @Composable private fun PersonalizationScreen(
        accentHex: String, onAccent: (String) -> Unit,
        compactMode: Boolean, onCompact: (Boolean) -> Unit,
        confirmDelete: Boolean, onConfirmDelete: (Boolean) -> Unit,
        defaultSort: String, onDefaultSort: (String) -> Unit,
        archivePattern: String, onArchivePattern: (String) -> Unit,
        appLanguage: String, onLanguage: (String) -> Unit,
        transferNotifications: Boolean, onTransferNotifications: (Boolean) -> Unit,
        themeMode: String, onThemeMode: (String) -> Unit, onExportBackup: () -> Unit, onRestoreBackup: () -> Unit,
        settingCount: Int, onClearCache: () -> Unit
    ) {
        val palettes = listOf("#2787C7", "#16A085", "#8E63CE", "#E67E22", "#D94F70", "#455A64", "#202A44")
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
            Text("Personalisasi Nexora", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text("Atur tampilan dan perilaku aplikasi. Preferensi disimpan di database lokal perangkat.", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(14.dp))
            Card(colors = CardDefaults.cardColors(containerColor = SurfaceColor)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Warna aksen", color = Ink, fontWeight = FontWeight.Bold)
                    Text("Pilih warna utama untuk komponen Material 3.", color = Muted, fontSize = 11.sp)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        palettes.forEach { hex ->
                            val selected = accentHex.equals(hex, true)
                            Box(Modifier.size(34.dp).background(Color(android.graphics.Color.parseColor(hex)), CircleShape)
                                .clickable { onAccent(hex) }, contentAlignment = Alignment.Center) {
                                if (selected) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(19.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Warna aktif: $accentHex", color = Muted, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Card(colors = CardDefaults.cardColors(containerColor = SurfaceColor)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text("Tampilan ringkas", color = Ink, fontWeight = FontWeight.SemiBold); Text("Simpan preferensi untuk mode daftar lebih padat (penerapan bertahap).", color = Muted, fontSize = 11.sp) }
                        Switch(checked = compactMode, onCheckedChange = onCompact)
                    }
                    HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text("Konfirmasi sebelum penghapusan", color = Ink, fontWeight = FontWeight.SemiBold); Text("Preferensi ini disimpan untuk aksi penghapusan berikutnya.", color = Muted, fontSize = 11.sp) }
                        Switch(checked = confirmDelete, onCheckedChange = onConfirmDelete)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Card(colors = CardDefaults.cardColors(containerColor = SurfaceColor)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Urutan file bawaan", color = Ink, fontWeight = FontWeight.Bold)
                    listOf("terbaru" to "Paling baru", "nama" to "Nama A–Z", "terbesar" to "Ukuran terbesar").forEach { (value, label) ->
                        Row(Modifier.fillMaxWidth().clickable { onDefaultSort(value) }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = defaultSort == value, onClick = { onDefaultSort(value) })
                            Text(label, color = Ink)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Card(colors = CardDefaults.cardColors(containerColor = SurfaceColor)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Aturan Arsip Cloud", color = Ink, fontWeight = FontWeight.Bold)
                    Text("Pilih pola folder tanggal untuk metadata pengarsipan Nexora. Pengaturan disimpan lokal; penerapan folder Telegram menunggu dukungan API backend.", color = Muted, fontSize = 11.sp)
                    listOf("yyyy/MM/dd" to "Tahun / Bulan / Hari", "yyyy/MM" to "Tahun / Bulan", "yyyy" to "Tahun saja", "flat" to "Tanpa folder tanggal").forEach { (value, label) ->
                        Row(Modifier.fillMaxWidth().clickable { onArchivePattern(value) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = archivePattern == value, onClick = { onArchivePattern(value) })
                            Text(label, color = Ink)
                        }
                    }
                    val sample = if (archivePattern == "flat") "Arsip/NamaFile.ext" else "Arsip/" + java.text.SimpleDateFormat(archivePattern, java.util.Locale.ROOT).format(java.util.Date()) + "/NamaFile.ext"
                    Text("Contoh struktur: $sample", color = Muted, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Card(colors = CardDefaults.cardColors(containerColor = SurfaceColor)) {
                Column(Modifier.padding(16.dp)) {
                    Text(if (appLanguage == "su") "Téma aplikasi" else "Tema aplikasi", color = Ink, fontWeight = FontWeight.Bold)
                    listOf("light" to (if (appLanguage == "su") "Caang" else "Terang"), "dark" to (if (appLanguage == "su") "Poék" else "Gelap")).forEach { (value, label) ->
                        Row(Modifier.fillMaxWidth().clickable { onThemeMode(value) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = themeMode == value, onClick = { onThemeMode(value) })
                            Text(label, color = Ink)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Card(colors = CardDefaults.cardColors(containerColor = SurfaceColor)) {
                Column(Modifier.padding(16.dp)) {
                    Text(if (appLanguage == "su") "Puseur cadangan" else "Pusat backup", color = Ink, fontWeight = FontWeight.Bold)
                    Text(if (appLanguage == "su") "Cadangkeun atawa pulihkeun database lokal." else "Ekspor dan pulihkan database lokal Nexora.", color = Muted, fontSize = 11.sp)
                    Button(onClick = onExportBackup, modifier = Modifier.fillMaxWidth()) { Text(if (appLanguage == "su") "Cadangkeun database" else "Ekspor backup database") }
                    OutlinedButton(onClick = onRestoreBackup, modifier = Modifier.fillMaxWidth()) { Text(if (appLanguage == "su") "Pulihkeun database" else "Pulihkan database dari file") }
                }
            }
            Spacer(Modifier.height(10.dp))
            Card(colors = CardDefaults.cardColors(containerColor = SurfaceColor)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Preferensi aplikasi", color = Ink, fontWeight = FontWeight.Bold)
                    Text("Pilihan ini tersimpan di database lokal dan tetap ada setelah APK diperbarui.", color = Muted, fontSize = 11.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Bahasa pilihan", color = Ink, fontWeight = FontWeight.SemiBold)
                    listOf("id" to "Indonesia", "su" to "Sunda").forEach { (code, label) ->
                        Row(Modifier.fillMaxWidth().clickable { onLanguage(code) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = appLanguage == code, onClick = { onLanguage(code) })
                            Text(label, color = Ink)
                        }
                    }
                    HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Notifikasi transfer", color = Ink, fontWeight = FontWeight.SemiBold)
                            Text("Simpan preferensi notifikasi transfer untuk pengaturan worker berikutnya.", color = Muted, fontSize = 11.sp)
                        }
                        Switch(checked = transferNotifications, onCheckedChange = onTransferNotifications)
                    }
                    Text(if (appLanguage == "su") "Pilihan basa Sunda ayeuna diterapkeun dina sababaraha bagian personalisasi jeung navigasi." else "Pilihan bahasa diterapkan pada navigasi dan sebagian menu personalisasi.", color = Muted, fontSize = 10.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Card(colors = CardDefaults.cardColors(containerColor = SurfaceColor)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Pusat Privasi", color = Ink, fontWeight = FontWeight.Bold)
                    Text("Ringkasan pengelolaan data Nexora", color = Muted, fontSize = 11.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("• Katalog file, preferensi, dan riwayat lokal disimpan di database privat aplikasi.", color = Ink, fontSize = 12.sp)
                    Text("• API key backend disimpan melalui penyimpanan rahasia Android Keystore.", color = Ink, fontSize = 12.sp)
                    Text("• Token bot Telegram dan kredensial database server tetap berada di backend, bukan APK.", color = Ink, fontSize = 12.sp)
                    Text("• Saat memakai fitur cloud, file dan metadata yang dipilih dikirim ke backend yang telah kamu konfigurasi.", color = Ink, fontSize = 12.sp)
                    Text("• Nexora tidak menambahkan SDK analitik atau iklan pada proyek ini.", color = Ink, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onClearCache, modifier = Modifier.fillMaxWidth()) { Text("Bersihkan cache sementara") }
                    Text("Tindakan ini hanya membersihkan cache aplikasi. Database, pengaturan, riwayat, dan file pustaka tidak disentuh.", color = Muted, fontSize = 10.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Card(colors = CardDefaults.cardColors(containerColor = SurfaceColor)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Penyimpanan aplikasi", color = Ink, fontWeight = FontWeight.Bold)
                    Text("Database: nexora_local.db • tabel preferensi: pengaturan", color = Muted, fontSize = 11.sp)
                    Text("$settingCount preferensi tersimpan. Database berada di ruang privat aplikasi dan dapat ikut dalam backup database Nexora.", color = Muted, fontSize = 11.sp)
                    Text("Data tetap ada saat aplikasi diperbarui dengan package ID yang sama. Menghapus aplikasi atau membersihkan data aplikasi dapat menghapus database lokal.", color = Muted, fontSize = 11.sp)
                }
            }
        }
    }

    @Composable private fun ProviderToggle(label: String, detail: String, checked: Boolean, implemented: Boolean, onChange: (Boolean) -> Unit) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, color = Ink)
                Text(if (implemented) detail else "$detail • integrasi belum tersedia", color = Muted, fontSize = 10.sp)
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }

    @Composable private fun EmptyState(title: String, message: String) {
        Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.CloudQueue, null, tint = Blue, modifier = Modifier.size(48.dp)); Spacer(Modifier.height(12.dp))
            Text(title, color = Ink, fontWeight = FontWeight.Bold); Text(message, color = Muted, fontSize = 12.sp)
        }
    }
}

data class LocalItem(val name: String, val size: Long, val time: Long, val path: String) {
    val ext: String get() = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
}

private val imageExt = setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "bmp", "tif", "tiff", "avif")
private val videoExt = setOf("mp4", "mkv", "mov", "webm", "3gp", "avi", "m4v", "mpeg", "mpg")
private val audioExt = setOf("mp3", "m4a", "wav", "flac", "ogg", "aac", "opus", "wma", "aiff")
private val documentExt = setOf("pdf", "doc", "docx", "txt", "rtf", "odt", "xls", "xlsx", "csv", "ppt", "pptx", "ods", "odp")
private val archiveExt = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz")
private val knownExt = imageExt + videoExt + audioExt + documentExt + archiveExt + setOf("apk", "aab")
private fun fileIcon(ext: String): ImageVector = when (ext) {
    in imageExt -> Icons.Default.Image
    in videoExt -> Icons.Default.PlayCircle
    in audioExt -> Icons.Default.MusicNote
    in documentExt -> if (ext == "pdf") Icons.Default.PictureAsPdf else Icons.Default.Description
    in archiveExt -> Icons.Default.FolderZip
    "apk", "aab" -> Icons.Default.Android
    else -> Icons.Default.InsertDriveFile
}
private fun dayKey(time: Long) = SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID")).format(Date(time))
private fun hourKey(time: Long) = SimpleDateFormat("HH:00", Locale("id", "ID")).format(Date(time))
private fun dateTime(time: Long) = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")).format(Date(time))
private fun sizeText(size: Long): String = when {
    size < 1024 -> "$size B"
    size < 1024 * 1024 -> "${size / 1024} KB"
    size < 1024L * 1024L * 1024L -> "${size / (1024 * 1024)} MB"
    else -> String.format(Locale.ROOT, "%.2f GB", size / (1024.0 * 1024.0 * 1024.0))
}
