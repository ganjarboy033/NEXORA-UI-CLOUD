package dev.gsprasada.nexoracloud

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Aksi yang hanya dapat dilakukan Activity (peluncur pemilih, FileProvider, logout total). */
class NexoraActions(
    val openLogin: () -> Unit,
    val pickUploadFiles: () -> Unit,
    val pickUploadFolder: () -> Unit,
    val pickDownloadFolder: () -> Unit,
    val logoutAll: () -> Unit,
    val shareFile: (File, String) -> Unit,
    val openFile: (File, String) -> Unit
)

sealed interface NxDlg {
    data class Confirm(val title: String, val message: String, val danger: Boolean, val onYes: () -> Unit) : NxDlg
    data class Rename(val item: NxItem) : NxDlg
    data class Create(val folder: Boolean) : NxDlg
    data class Delete(val targets: List<NxItem>) : NxDlg
    data class Move(val targets: List<NxItem>, val copy: Boolean) : NxDlg
    data class Download(val item: NxItem) : NxDlg
    data class Labels(val item: NxItem) : NxDlg
}

private val NxProviders = listOf("all", "tdlib", "gdrive", "github", "lokal")

/**
 * Cangkang UI Nexora v12: top bar, drawer, tiga tab (Gallery, Files, Setting), FAB Upload, banner status,
 * batch bar, sheet/dialog, viewer imersif, dan tumpukan subhalaman. [legacy] merender layar lama berdasarkan nama tab lamanya.
 */
@Composable
fun NexoraRoot(
    vm: ExplorerViewModel,
    colors: NexoraColors,
    onToggleTheme: () -> Unit,
    actions: NexoraActions,
    legacy: @Composable (String) -> Unit
) {
    val ctx = LocalContext.current
    val authState by TdClient.state.collectAsState()
    val tdTransfers by TdCloud.get(ctx).transfers.collectAsState()
    var tab by rememberSaveable { mutableStateOf("files") }
    var drawer by remember { mutableStateOf(false) }
    val pages = remember { mutableStateListOf<String>() }
    var menuItem by remember { mutableStateOf<NxItem?>(null) }
    var dlg by remember { mutableStateOf<NxDlg?>(null) }
    var shownDlg by remember { mutableStateOf<NxDlg?>(null) }
    var infoItem by remember { mutableStateOf<NxItem?>(null) }
    var detailItem by remember { mutableStateOf<NxItem?>(null) }
    var lastPage by remember { mutableStateOf("") }
    val records by produceState(emptyList<TransferRecord>(), pages.lastOrNull()) {
        while (true) {
            value = withContext(Dispatchers.IO) { vm.db.listTransfers() }
            delay(if (pages.lastOrNull() == "transfer") 1000L else 5000L)
        }
    }
    val failed = records.count { it.status == "Gagal" }
    val ready = authState is TdAuthState.Ready

    LaunchedEffect(Unit) { vm.refresh() }
    LaunchedEffect(ready) { vm.refresh() }
    LaunchedEffect(dlg) { if (dlg != null) shownDlg = dlg }
    LaunchedEffect(pages.lastOrNull()) { pages.lastOrNull()?.let { lastPage = it } }

    fun openPage(id: String) { drawer = false; menuItem = null; pages.add(id) }
    fun closePage() { if (pages.isNotEmpty()) pages.removeAt(pages.lastIndex) }
    fun confirm(title: String, message: String, danger: Boolean = false, onYes: () -> Unit) { dlg = NxDlg.Confirm(title, message, danger, onYes) }
    val currentFolder = if (tab == "gallery") vm.galleryFolder else vm.filesFolder

    // Back: prioritas terendah ke tertinggi (handler yang terakhir dikomposisi menang).
    BackHandler(enabled = tab == "files" && vm.filesFolder.isNotEmpty()) { vm.filesFolder = vm.filesFolder.substringBeforeLast('/', "") }
    BackHandler(enabled = tab == "gallery" && vm.galleryFolder.isNotEmpty()) { vm.galleryFolder = vm.galleryFolder.substringBeforeLast('/', "") }
    BackHandler(enabled = vm.batchMode) { vm.setBatch(false) }
    BackHandler(enabled = drawer) { drawer = false }
    BackHandler(enabled = pages.isNotEmpty()) { closePage() }
    BackHandler(enabled = vm.viewerItem != null) { vm.closeViewer() }
    BackHandler(enabled = infoItem != null) { infoItem = null }
    BackHandler(enabled = menuItem != null) { menuItem = null }
    BackHandler(enabled = dlg != null) { dlg = null }

    CompositionLocalProvider(LocalNexora provides colors) {
        val c = colors
        Box(Modifier.fillMaxSize().background(c.bg)) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                NxTopBar(
                    ready = ready, failed = failed,
                    onMenu = { drawer = true },
                    onTransfer = { openPage("transfer") },
                    onGoogle = { openPage("google") },
                    onTd = actions.openLogin,
                    onRefresh = { dlg = NxDlg.Confirm("Segarkan Semua Katalog?", "Menyinkronkan ulang daftar file dan folder dari Telegram dan pustaka lokal.", false) { vm.refresh() } },
                    onTheme = onToggleTheme
                )
                // batch bar
                AnimatedVisibility(vm.batchMode, enter = fadeIn(tween(140)), exit = fadeOut(tween(120))) {
                    Row(
                        Modifier.padding(horizontal = 14.dp).padding(bottom = 8.dp).fillMaxWidth().shadow(4.dp, RoundedCornerShape(14.dp))
                            .background(c.primary, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("${vm.selected.size} dipilih", Modifier.weight(1f), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        BatchBtn("☑ Semua") { vm.selectAll(tab == "gallery") }
                        BatchBtn("🚀") { if (vm.selected.isNotEmpty()) dlg = NxDlg.Move(vm.selectedTargets(), false) else vm.showToast("Pilih item terlebih dahulu.") }
                        BatchBtn("📋") { if (vm.selected.isNotEmpty()) dlg = NxDlg.Move(vm.selectedTargets(), true) else vm.showToast("Pilih item terlebih dahulu.") }
                        BatchBtn("🗑️") { if (vm.selected.isNotEmpty()) dlg = NxDlg.Delete(vm.selectedTargets()) else vm.showToast("Pilih item terlebih dahulu.") }
                        BatchBtn("✕") { vm.setBatch(false) }
                    }
                }
                if (tab != "settings") NxSearchBar(vm.query) { vm.query = it }
                if (!ready) {
                    Row(
                        Modifier.padding(horizontal = 14.dp).padding(bottom = 8.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(c.primaryContainer).clickable { actions.openLogin() }.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⚡ Belum login TDLib • Ketuk untuk masuk", Modifier.weight(1f), color = c.onPrimaryContainer, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("→", color = c.onPrimaryContainer)
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (tab) {
                        "gallery" -> NxGalleryTab(vm, onOpen = { vm.openViewer(it) }, onMenu = { menuItem = it }, onDuplicates = { openPage("duplicates") })
                        "files" -> NxFilesTab(vm, onOpen = { vm.openViewer(it) }, onMenu = { menuItem = it }, onCreate = { dlg = NxDlg.Create(it) }, onDuplicates = { openPage("duplicates") })
                        else -> NxSettingsTab(
                            tdSub = when (val s = authState) {
                                is TdAuthState.Ready -> "Terhubung: ${s.displayName.ifBlank { "akun Telegram" }} • Nomor HP, OTP, 2FA, QR & Target Chat"
                                else -> "Belum login • Nomor HP, OTP, 2FA, QR Tele, Registrasi & Target Chat"
                            },
                            cacheCount = vm.sessionCacheFiles().size, failedCount = failed,
                            onOpen = { openPage(it) }, onLogin = actions.openLogin,
                            onLogoutAll = { confirm("Keluar & Putus Semua Tautan?", "Sesi Telegram (TDLib) di HP ini dikeluarkan dan token tersimpan dihapus. File di Telegram tetap aman.", true) { actions.logoutAll() } }
                        )
                    }
                }
                NxBottomNav(tab) { tab = it; vm.setBatch(false) }
            }

            // FAB Upload
            if (tab != "settings") {
                Row(
                    Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 16.dp, bottom = 74.dp)
                        .shadow(6.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(c.primaryContainer)
                        .clickable { openPage("upload") }.padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Text("☁️", fontSize = 15.sp)
                    Text("Upload", color = c.onPrimaryContainer, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                }
            }

            NxDrawer(
                open = drawer, vm = vm, authState = authState, onClose = { drawer = false },
                onProvider = { vm.filter = it; drawer = false; if (tab == "settings") tab = "files" },
                onPage = { openPage(it) },
                onLogin = { drawer = false; actions.openLogin() },
                onSettings = { drawer = false; tab = "settings" },
                failedCount = failed
            )

            // Subhalaman
            AnimatedVisibility(
                pages.isNotEmpty(),
                enter = slideInHorizontally(tween(220)) { it } + fadeIn(tween(160)),
                exit = slideOutHorizontally(tween(180)) { it } + fadeOut(tween(140))
            ) {
                Box(Modifier.fillMaxSize().background(c.bg).systemBarsPadding()) {
                    NxPageHost(
                        id = lastPage, vm = vm, actions = actions, records = records, legacy = legacy,
                        detailItem = detailItem, onBack = { closePage() }, onOpenPage = { openPage(it) },
                        onConfirm = { t, m, d, y -> confirm(t, m, d, y) },
                        onViewer = { vm.openViewer(it) }
                    )
                }
            }

            NxViewerOverlay(
                vm = vm, tdTransfers = tdTransfers,
                onMore = { menuItem = it },
                onDownload = { dlg = NxDlg.Download(it) },
                onInfo = { infoItem = it },
                onShare = { item, file -> actions.shareFile(file, item.mime) },
                onDelete = { dlg = NxDlg.Delete(listOf(it)) },
                onOpenExternal = { file, mime -> actions.openFile(file, mime) }
            )

            // Sheet menu item
            NxSheet(menuItem != null, { menuItem = null }) {
                val item = menuItem
                if (item != null) {
                    Text(item.name, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (item.isFolder) "Folder Virtual • ${item.fileCount} file" else "${nxSize(item.sizeBytes)} • ${nxSourceLabel(item.source)}",
                        color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp)
                    )
                    if (!item.isFolder) {
                        NxSheetAction("👁️", "Pratinjau / Buka Langsung") { menuItem = null; vm.openViewer(item) }
                        if (item.kind == "zip") NxSheetAction("📂", "Ekstrak ZIP Langsung ke Folder Ini") {
                            menuItem = null
                            confirm("Ekstrak Arsip ZIP?", "Isi ZIP akan dibongkar dan diunggah sebagai berkas terpisah ke folder ini.", false) { vm.extractZipHere(item) }
                        }
                    }
                    NxSheetAction("🚀", "Pindahkan ke Cloud / Folder Lain (Move)") { menuItem = null; dlg = NxDlg.Move(listOf(item), false) }
                    if (!item.isFolder) NxSheetAction("📋", "Salin ke Cloud / Folder Lain (Copy)") { menuItem = null; dlg = NxDlg.Move(listOf(item), true) }
                    NxSheetAction("✏️", "Ubah Nama (Rename)") { menuItem = null; dlg = NxDlg.Rename(item) }
                    if (!item.isFolder) {
                        NxSheetAction("⭐", if (vm.isFav(item)) "Hapus dari Favorit" else "Tambah ke Favorit") { menuItem = null; vm.toggleFavorite(item) }
                        NxSheetAction("🗄️", if (vm.isArchived(item)) "Kembalikan dari Arsip" else "Pindahkan ke Arsip") { menuItem = null; vm.toggleArchive(item) }
                        NxSheetAction("⬇️", "Unduh ke Folder Pilihan") { menuItem = null; dlg = NxDlg.Download(item) }
                        NxSheetAction("ℹ️", "Detail & SHA-256") { menuItem = null; detailItem = item; openPage("detail") }
                    }
                    NxSheetAction("🗑️", "Hapus (Delete)", danger = true) { menuItem = null; dlg = NxDlg.Delete(listOf(item)) }
                }
            }

            // Sheet info & label (viewer)
            NxSheet(infoItem != null, { infoItem = null }) {
                val item = infoItem
                if (item != null) {
                    Text(item.dateText, color = c.muted, fontSize = 12.sp)
                    Text(item.name, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(nxEmoji(item.kind) + " " + item.kind.replaceFirstChar { it.uppercase() } + " / Berkas", color = c.primary, fontSize = 12.sp)
                    Text("${nxSize(item.sizeBytes)} | ${item.mime}", color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    Text("SHA-256:", color = c.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
                    Text(item.sha256.ifBlank { "—  (belum tercatat untuk berkas ini)" }, color = c.text, fontSize = 11.sp)
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Label", Modifier.weight(1f), color = c.text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        NxMiniButton("Edit") { infoItem = null; dlg = NxDlg.Labels(item) }
                    }
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        val labels = vm.labelsOf(item)
                        if (labels.isEmpty()) Text("Belum ada label", color = c.muted, fontSize = 11.sp)
                        labels.forEach { NxPill(it) }
                    }
                }
            }

            // Dialog universal
            NxDialog(dlg != null, { dlg = null }) {
                val d = shownDlg
                if (d != null) NxDialogContent(d, vm, actions, currentFolder, onClose = { dlg = null }, onNext = { dlg = it })
            }

            NxToastHost(vm.toast, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 78.dp))
        }
    }
}

// ------------------------------------------------------------------ top bar, search, nav

@Composable
private fun BatchBtn(text: String, onClick: () -> Unit) {
    Text(
        text, color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.2f)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 5.dp)
    )
}

@Composable
private fun NxTopBar(ready: Boolean, failed: Int, onMenu: () -> Unit, onTransfer: () -> Unit, onGoogle: () -> Unit, onTd: () -> Unit, onRefresh: () -> Unit, onTheme: () -> Unit) {
    val c = LocalNexora.current
    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        NxCircleButton("☰", onClick = onMenu)
        Text("Nexora Cloud", Modifier.weight(1f), color = c.text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box {
            NxCircleButton("⇅", onClick = onTransfer)
            if (failed > 0) Box(Modifier.align(Alignment.TopEnd).size(9.dp).background(c.danger, CircleShape))
        }
        TopPill("🇬", onGoogle)
        TopPill(if (ready) "✈️ TDLib ✓" else "✈️ TDLib", onTd)
        NxCircleButton("↻", onClick = onRefresh)
        NxCircleButton("🌓", onClick = onTheme)
    }
}

@Composable
private fun TopPill(text: String, onClick: () -> Unit) {
    val c = LocalNexora.current
    val shape = RoundedCornerShape(20.dp)
    Text(
        text, color = c.primary, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
        modifier = Modifier.clip(shape).background(c.surface).border(1.dp, c.border, shape).clickable(onClick = onClick).padding(horizontal = 9.dp, vertical = 6.dp)
    )
}

@Composable
private fun NxSearchBar(value: String, onChange: (String) -> Unit) {
    val c = LocalNexora.current
    val shape = RoundedCornerShape(24.dp)
    BasicTextField(
        value = value, onValueChange = onChange, singleLine = true,
        textStyle = TextStyle(color = c.text, fontSize = 13.sp), cursorBrush = SolidColor(c.primary),
        modifier = Modifier.padding(horizontal = 14.dp).padding(bottom = 8.dp).fillMaxWidth(),
        decorationBox = { inner ->
            Row(Modifier.fillMaxWidth().background(c.surface, shape).border(1.dp, c.border, shape).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🔍", fontSize = 13.sp)
                Box(Modifier.weight(1f).padding(start = 8.dp)) {
                    if (value.isEmpty()) Text("Cari nama, ekstensi, SHA-256…", color = c.muted, fontSize = 13.sp)
                    inner()
                }
                if (value.isNotEmpty()) Text("✕", color = c.muted, modifier = Modifier.clickable { onChange("") }.padding(start = 6.dp))
            }
        }
    )
}

@Composable
private fun NxBottomNav(tab: String, onSelect: (String) -> Unit) {
    val c = LocalNexora.current
    Row(Modifier.fillMaxWidth().height(62.dp).background(c.surface).border(1.dp, c.border), verticalAlignment = Alignment.CenterVertically) {
        listOf(Triple("gallery", "🖼️", "Gallery"), Triple("files", "📁", "Files"), Triple("settings", "☰", "Setting")).forEach { (key, icon, label) ->
            Column(
                Modifier.weight(1f).fillMaxHeight().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(key) },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
            ) {
                Text(icon, fontSize = 18.sp, color = if (tab == key) c.primary else c.muted)
                Text(label, fontSize = 10.5.sp, color = if (tab == key) c.primary else c.muted, fontWeight = if (tab == key) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

// ------------------------------------------------------------------ drawer

@Composable
private fun DrawerItem(icon: String, title: String, badge: String? = null, selected: Boolean = false, badgeError: Boolean = false, onClick: () -> Unit) {
    val c = LocalNexora.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 1.dp).clip(RoundedCornerShape(12.dp)).background(if (selected) c.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Text(icon, fontSize = 15.sp)
        Text(title, Modifier.weight(1f), color = if (selected) c.onPrimaryContainer else c.text, fontSize = 13.5.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (badge != null) NxPill(badge, bg = if (badgeError) c.dangerBg else null, fg = if (badgeError) c.danger else null)
    }
}

@Composable
private fun NxDrawer(
    open: Boolean, vm: ExplorerViewModel, authState: TdAuthState, failedCount: Int,
    onClose: () -> Unit, onProvider: (String) -> Unit, onPage: (String) -> Unit, onLogin: () -> Unit, onSettings: () -> Unit
) {
    val c = LocalNexora.current
    AnimatedVisibility(open, enter = fadeIn(tween(160)), exit = fadeOut(tween(140))) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose))
    }
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            open, modifier = Modifier.align(Alignment.CenterStart),
            enter = slideInHorizontally(tween(220)) { -it }, exit = slideOutHorizontally(tween(180)) { -it }
        ) {
            Column(Modifier.fillMaxHeight().fillMaxWidth(0.82f).widthIn(max = 315.dp).shadow(16.dp).background(c.surface)) {
                val bytes = vm.bytesBySource()
                val total = bytes.values.sum()
                Column(
                    Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(c.primaryContainer, c.surfaceVariant))).statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(c.primary), contentAlignment = Alignment.Center) { Text("☁️", fontSize = 18.sp) }
                        Column(Modifier.weight(1f).padding(start = 9.dp)) {
                            Text("Nexora Cloud", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                            Text("Multi-Cloud Hub • v12", color = c.muted, fontSize = 11.sp)
                        }
                        NxCircleButton("✕", size = 30, bg = c.surface, onClick = onClose)
                    }
                    Spacer(Modifier.height(12.dp))
                    DrawerStatus("✈️ Telegram TDLib", if (authState is TdAuthState.Ready) "Terhubung" else "Belum Login", authState is TdAuthState.Ready, onLogin)
                    Spacer(Modifier.height(6.dp))
                    DrawerStatus("🇬 Google Drive", "Belum Login", false) { onPage("google") }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Text("Penyimpanan Gabungan", Modifier.weight(1f), color = c.muted, fontSize = 11.sp)
                        Text("${nxSize(total)} / ∞ Unlimited", color = c.text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 5.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.border)) {
                        val tdShare = if (total > 0) bytes.getValue("tdlib").toFloat() / total else 0f
                        if (tdShare > 0f) Box(Modifier.weight(tdShare).fillMaxHeight().background(c.primary))
                        if (tdShare < 1f && total > 0) Box(Modifier.weight(1f - tdShare).fillMaxHeight().background(c.warning))
                    }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 10.dp, end = 10.dp, bottom = 24.dp)) {
                    NxSectionTitle("Penyedia Cloud & Penyimpanan")
                    DrawerItem("🌐", "Semua Cloud (Gabungan)", "All", vm.filter == "all") { onProvider("all") }
                    DrawerItem("✈️", "Telegram TDLib Cloud", "∞", vm.filter == "tdlib") { onProvider("tdlib") }
                    DrawerItem("🇬", "Google Drive", "Belum tersedia", vm.filter == "gdrive") { onProvider("gdrive") }
                    DrawerItem("🐙", "GitHub Releases / Repo", "2 GB/file", vm.filter == "github") { onProvider("github") }
                    DrawerItem("📱", "Pustaka Lokal & SAF", "HP", vm.filter == "lokal") { onProvider("lokal") }
                    NxSectionTitle("Akses Cepat & Alat")
                    DrawerItem("⭐", "Favorit", selected = vm.filter == "fav") { onProvider("fav") }
                    DrawerItem("⚡", "Cache Sesi Offline", selected = vm.filter == "offline") { onProvider("offline") }
                    DrawerItem("🗄️", "Arsip Tersembunyi", selected = vm.filter == "archive") { onProvider("archive") }
                    DrawerItem("⇅", "Antrean & Upload Gagal (Retry)", if (failedCount > 0) "$failedCount Gagal" else null, badgeError = true) { onPage("transfer") }
                    DrawerItem("🧬", "Deteksi & Kelola Duplikat", "4 Jenis") { onPage("duplicates") }
                    DrawerItem("🗑️", "Sampah (TrashScreen)") { onPage("trash") }
                    NxSectionTitle("Koneksi & Pengaturan")
                    DrawerItem("🔑", "TelegramAppConfig (DC, Key & Proxy)") { onPage("tgconfig") }
                    DrawerItem("🔐", "Login / Kelola Akun Google") { onPage("google") }
                    DrawerItem("☁️", "Kelola Multi-Cloud Provider") { onPage("multicloud") }
                    DrawerItem("📡", "Nexora Link (Vercel / GitHub)") { onPage("broadcast") }
                    DrawerItem("🐞", "Panel Debug Multi-Tab (Dev)", "Live") { onPage("debug") }
                    DrawerItem("⚙️", "Semua Pengaturan Lengkap") { onSettings() }
                }
            }
        }
    }
}

@Composable
private fun DrawerStatus(title: String, status: String, ok: Boolean, onClick: () -> Unit) {
    val c = LocalNexora.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.surface).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, Modifier.weight(1f), color = c.text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        NxPill(status, bg = if (ok) c.successBg else null, fg = if (ok) c.success else null)
    }
}

// ------------------------------------------------------------------ isi dialog

@Composable
private fun DialogTitle(text: String) {
    val c = LocalNexora.current
    Text(text, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun NxDialogContent(d: NxDlg, vm: ExplorerViewModel, actions: NexoraActions, currentFolder: String, onClose: () -> Unit, onNext: (NxDlg?) -> Unit) {
    val c = LocalNexora.current
    when (d) {
        is NxDlg.Confirm -> {
            DialogTitle(d.title)
            Text(d.message, color = c.muted, fontSize = 13.sp)
            NxButton("Ya, Lanjutkan", kind = if (d.danger) NxKind.Danger else NxKind.Primary) { onClose(); d.onYes() }
            NxButton("Batal", kind = NxKind.Outline, onClick = onClose)
        }
        is NxDlg.Rename -> {
            var name by remember(d.item.id) { mutableStateOf(d.item.name) }
            DialogTitle("Ubah Nama Item")
            NxField(name, { name = it }, "Nama baru")
            NxButton("Konfirmasi Simpan Nama", enabled = name.isNotBlank()) { onClose(); vm.rename(d.item, name) }
            NxButton("Batal", kind = NxKind.Outline, onClick = onClose)
        }
        is NxDlg.Create -> {
            var folder by remember { mutableStateOf(d.folder) }
            var name by remember { mutableStateOf("") }
            var body by remember { mutableStateOf("") }
            DialogTitle(if (folder) "Buat Folder Baru" else "Buat File Baru")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NxChip("📁 Folder", folder) { folder = true }
                NxChip("📝 File teks", !folder) { folder = false }
            }
            Text("Lokasi: " + if (currentFolder.isEmpty()) "Root" else currentFolder, color = c.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
            NxField(name, { name = it }, if (folder) "Nama folder…" else "Nama berkas (mis. catatan.txt)")
            if (!folder) NxField(body, { body = it }, "Isi teks berkas (catatan, JSON, kode)…", singleLine = false, minLines = 4)
            NxButton("Konfirmasi & Buat", enabled = name.isNotBlank()) {
                onClose()
                if (folder) vm.createFolder(currentFolder, name) else vm.createTextFile(currentFolder, name, body)
            }
            NxButton("Batal", kind = NxKind.Outline, onClick = onClose)
        }
        is NxDlg.Delete -> {
            DialogTitle(if (d.targets.size == 1) "Hapus Item Ini?" else "Hapus ${d.targets.size} Item?")
            Text("Pilih tindakan penghapusan untuk berkas atau folder yang dipilih.", color = c.muted, fontSize = 13.sp)
            NxButton("Pindahkan ke Sampah (Bisa Dipulihkan)", kind = NxKind.Warn) { onClose(); vm.trash(d.targets); if (vm.viewerItem != null) vm.closeViewer() }
            NxButton("Hapus Permanen (Termasuk Cache Sesi)", kind = NxKind.Danger) { onClose(); vm.purge(d.targets); if (vm.viewerItem != null) vm.closeViewer() }
            NxButton("Batal", kind = NxKind.Outline, onClick = onClose)
        }
        is NxDlg.Move -> {
            var provider by remember { mutableStateOf("tdlib") }
            var folder by remember { mutableStateOf("") }
            val choices = remember(vm.cloudItems, vm.emptyFolders) { vm.folderChoices() }
            DialogTitle(if (d.copy) "Salin ke Cloud / Folder" else "Pindahkan ke Cloud / Folder")
            Text(if (d.targets.size == 1) d.targets.first().name else "${d.targets.size} item dipilih", color = c.muted, fontSize = 12.sp)
            Text("1. Penyedia Cloud Tujuan", color = c.text, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            NxDropdown(
                listOf("tdlib" to "✈️ Telegram TDLib Cloud (Unlimited)", "gdrive" to "🇬 Google Drive (belum tersedia)", "github" to "🐙 GitHub Releases (belum tersedia)", "lokal" to "📱 Pustaka Lokal HP"),
                provider, { provider = it }, disabled = setOf("gdrive", "github")
            )
            if (provider == "tdlib") {
                Text("2. Folder Tujuan", color = c.text, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                NxDropdown(choices.map { it to (if (it.isEmpty()) "📁 Root (Utama)" else "📁 $it") }, folder, { folder = it })
            }
            NxButton("Konfirmasi & Jalankan") {
                onClose()
                val cloudTargets = d.targets.filter { it.isFolder || it.source == "tdlib" }
                val localTargets = d.targets.filter { !it.isFolder && it.source == "lokal" }
                if (provider == "tdlib") {
                    if (!d.copy && cloudTargets.isNotEmpty()) vm.moveToFolder(cloudTargets, folder)
                    if (d.copy && cloudTargets.isNotEmpty()) vm.showToast("Salin antar folder Telegram belum didukung; gunakan Pindahkan.")
                    localTargets.forEach { vm.uploadLocalToCloud(it, folder) }
                } else {
                    cloudTargets.filter { !it.isFolder }.forEach { vm.enqueueDownload(it) }
                    if (localTargets.isNotEmpty()) vm.showToast("Berkas lokal sudah berada di pustaka lokal.")
                }
                vm.setBatch(false)
            }
            NxButton("Batal", kind = NxKind.Outline, onClick = onClose)
        }
        is NxDlg.Download -> {
            var useCache by remember { mutableStateOf(true) }
            val dlCtx = LocalContext.current
            val folderTick by NxTransferEngine.folderTick.collectAsState()
            val tree = remember(folderTick) { NxTransferEngine.downloadTreeUri(dlCtx) }
            val label = tree?.let { runCatching { java.net.URLDecoder.decode(android.net.Uri.parse(it).lastPathSegment.orEmpty(), "UTF-8") }.getOrNull() } ?: "Belum dipilih"
            DialogTitle("⬇️ Unduh & Pilih Folder Tujuan")
            Text(d.item.name, color = c.muted, fontSize = 12.sp)
            Text("Folder unduhan (Android SAF):", color = c.text, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            Text("📂 $label", color = c.primary, fontSize = 13.sp, modifier = Modifier.padding(vertical = 4.dp))
            NxButton("🔍 Pilih / Ganti Folder (OpenDocumentTree)", kind = NxKind.Outline) { actions.pickDownloadFolder() }
            if (d.item.source == "tdlib") {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Gunakan Cache Sesi jika sudah terunduh", Modifier.weight(1f), color = c.text, fontSize = 12.sp)
                    Checkbox(useCache, { useCache = it }, colors = CheckboxDefaults.colors(checkedColor = c.primary))
                }
            }
            NxButton("Konfirmasi & Mulai Unduh", enabled = tree != null) { onClose(); vm.enqueueDownload(d.item, useCache) }
            NxButton("Batal", kind = NxKind.Outline, onClick = onClose)
        }
        is NxDlg.Labels -> {
            var labels by remember(d.item.id) { mutableStateOf(vm.labelsOf(d.item)) }
            var input by remember { mutableStateOf("") }
            DialogTitle("Label — ${d.item.name}")
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                labels.forEach { l -> NxChip("$l  ✕", false) { labels = labels - l } }
                if (labels.isEmpty()) Text("Belum ada label", color = c.muted, fontSize = 12.sp)
            }
            NxField(input, { input = it }, "Tambah label…")
            NxButton("+ Tambah", kind = NxKind.Outline, enabled = input.isNotBlank()) { labels = (labels + input.trim()).distinct(); input = "" }
            NxButton("Simpan Label") { vm.setLabels(d.item, labels); onClose() }
            NxButton("Batal", kind = NxKind.Outline, onClick = onClose)
        }
    }
}
