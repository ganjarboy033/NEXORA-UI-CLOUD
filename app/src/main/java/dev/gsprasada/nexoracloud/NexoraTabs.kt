package dev.gsprasada.nexoracloud

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ------------------------------------------------------------------ thumbnail

private fun nxDecodeThumb(file: File, target: Int): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (bounds.outWidth / sample > target * 2 || bounds.outHeight / sample > target * 2) sample *= 2
    BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}.getOrNull()

@Composable
fun rememberNxThumb(file: File?, target: Int = 240): ImageBitmap? {
    val state = produceState<ImageBitmap?>(initialValue = null, file?.path, file?.lastModified()) {
        value = if (file == null) null else withContext(Dispatchers.IO) { nxDecodeThumb(file, target) }
    }
    return state.value
}

/** Berkas gambar yang bisa dipakai sebagai thumbnail: berkas lokal, atau berkas Telegram yang sudah ada di cache sesi. */
fun nxThumbFile(vm: ExplorerViewModel, item: NxItem): File? {
    if (item.isFolder || item.kind != "photo") return null
    return if (item.source == "lokal") File(item.localPath.orEmpty()).takeIf { it.isFile }
    else if (vm.isCached(item)) vm.cacheFile(item)?.takeIf { it.isFile } else null
}

// ------------------------------------------------------------------ komponen daftar

@Composable
fun NxChipsRow(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (key, label) -> NxChip(label, selected == key) { onSelect(key) } }
    }
}

@Composable
private fun NxCheck(checked: Boolean, onCheck: () -> Unit) {
    val c = LocalNexora.current
    Checkbox(checked = checked, onCheckedChange = { onCheck() }, colors = CheckboxDefaults.colors(checkedColor = c.primary, uncheckedColor = c.muted))
}

@Composable
fun NxItemRow(
    item: NxItem,
    vm: ExplorerViewModel,
    batch: Boolean,
    checked: Boolean,
    onTap: () -> Unit,
    onDots: () -> Unit,
    onCheck: () -> Unit
) {
    val c = LocalNexora.current
    val shape = RoundedCornerShape(14.dp)
    val fav = !item.isFolder && vm.isFav(item)
    val archived = !item.isFolder && vm.isArchived(item)
    val cached = !item.isFolder && vm.isCached(item)
    val thumb = rememberNxThumb(nxThumbFile(vm, item), 96)
    Row(
        Modifier.fillMaxWidth().padding(bottom = 7.dp).shadow(1.dp, shape).background(c.surface, shape)
            .border(1.5.dp, if (checked) c.primary else Color.Transparent, shape).padding(start = if (batch) 2.dp else 10.dp, top = 9.dp, bottom = 9.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (batch) NxCheck(checked, onCheck)
        Row(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable(onClick = onTap), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(c.primaryContainer), contentAlignment = Alignment.Center) {
                if (thumb != null) Image(thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Text(nxEmoji(item.kind), fontSize = 17.sp)
            }
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(item.name, color = c.text, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = if (item.isFolder) "Folder Virtual • ${item.fileCount} file" else "${nxSize(item.sizeBytes)} • ${item.mime.substringAfter('/').take(18)} • ${if (item.folder.isEmpty()) nxSourceLabel(item.source) else item.folder}"
                Text(sub, color = c.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (fav || archived || (cached && item.source == "tdlib")) {
                    Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (fav) NxPill("⭐ Favorit")
                        if (archived) NxPill("🗄️ Arsip")
                        if (cached && item.source == "tdlib") NxPill("⚡ Offline Cache", bg = c.successBg, fg = c.success)
                    }
                }
            }
        }
        Text("⋮", color = c.muted, fontSize = 20.sp, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onDots).padding(horizontal = 10.dp, vertical = 6.dp))
    }
}

@Composable
fun NxMediaCard(item: NxItem, vm: ExplorerViewModel, batch: Boolean, checked: Boolean, onTap: () -> Unit, onDots: () -> Unit, onCheck: () -> Unit) {
    val c = LocalNexora.current
    val shape = RoundedCornerShape(12.dp)
    val thumb = rememberNxThumb(nxThumbFile(vm, item), 220)
    Box(
        Modifier.aspectRatio(1f).clip(shape).background(c.surface).border(1.5.dp, if (checked) c.primary else c.border, shape).clickable(onClick = onTap)
    ) {
        if (thumb != null) {
            Image(thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Column(Modifier.fillMaxSize().padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(nxEmoji(item.kind), fontSize = 24.sp)
                Spacer(Modifier.height(4.dp))
                Text(item.name, color = c.text, fontSize = 10.sp, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(
            if (item.isFolder) "Folder" else nxSize(item.sizeBytes), color = Color.White, fontSize = 8.5.sp,
            modifier = Modifier.align(Alignment.TopStart).padding(4.dp).background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(6.dp)).padding(horizontal = 5.dp, vertical = 2.dp)
        )
        Text(
            "⋮", color = if (thumb != null) Color.White else c.muted, fontSize = 17.sp,
            modifier = Modifier.align(Alignment.TopEnd).clip(RoundedCornerShape(8.dp)).clickable(onClick = onDots).padding(horizontal = 8.dp, vertical = 2.dp)
        )
        if (batch) Box(Modifier.align(Alignment.BottomStart).padding(2.dp).background(c.surface.copy(alpha = 0.85f), RoundedCornerShape(8.dp))) { NxCheck(checked, onCheck) }
        if (thumb != null) {
            Text(
                item.name, color = Color.White, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
private fun NxExplorerBar(breadcrumb: String, onBreadcrumb: () -> Unit, actions: @Composable () -> Unit) {
    val c = LocalNexora.current
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(breadcrumb, Modifier.weight(1f).clickable(onClick = onBreadcrumb), color = c.primary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
    }
}

private val NxFileChips = listOf(
    "all" to "Semua", "tdlib" to "✈️ TDLib", "gdrive" to "🇬 Google Drive", "github" to "🐙 GitHub",
    "lokal" to "📱 Lokal", "fav" to "⭐ Favorit", "offline" to "⚡ Offline", "archive" to "🗄️ Arsip"
)
private val NxGalleryChips = NxFileChips.filter { it.first != "lokal" }

// ------------------------------------------------------------------ tab Files

@Composable
fun NxFilesTab(vm: ExplorerViewModel, onOpen: (NxItem) -> Unit, onMenu: (NxItem) -> Unit, onCreate: (Boolean) -> Unit, onDuplicates: () -> Unit) {
    val c = LocalNexora.current
    val (folders, files) = vm.visible(gallery = false)
    val entries = folders + files
    val crumb = "📁 Files › " + if (vm.filesFolder.isEmpty()) "Root" else vm.filesFolder.replace("/", " › ")
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        NxChipsRow(NxFileChips, vm.filter) { vm.filter = it }
        NxExplorerBar(crumb, { vm.filesFolder = "" }) {
            NxMiniButton(if (vm.batchMode) "✕ Batch" else "☑️ Batch") { vm.setBatch(!vm.batchMode) }
            NxMiniButton("+ Folder") { onCreate(true) }
            NxMiniButton("+ File") { onCreate(false) }
        }
        if (vm.loading && entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.primary) }
        } else if (entries.isEmpty()) {
            NxEmpty(vm.cloudNote?.let { "Folder / filter ini kosong\n($it)" } ?: "Folder / filter ini kosong")
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 130.dp)) {
                items(entries, key = { it.id }) { item ->
                    NxItemRow(
                        item, vm, vm.batchMode, item.id in vm.selected,
                        onTap = {
                            when {
                                vm.batchMode -> vm.toggleSelect(item.id)
                                item.isFolder -> vm.filesFolder = item.fullPath
                                else -> onOpen(item)
                            }
                        },
                        onDots = { onMenu(item) },
                        onCheck = { vm.toggleSelect(item.id) }
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ tab Gallery

@Composable
fun NxGalleryTab(vm: ExplorerViewModel, onOpen: (NxItem) -> Unit, onMenu: (NxItem) -> Unit, onDuplicates: () -> Unit) {
    val c = LocalNexora.current
    val (folders, files) = vm.visible(gallery = true)
    val crumb = "🖼️ Gallery › " + if (vm.galleryFolder.isEmpty()) "Root" else vm.galleryFolder.replace("/", " › ")
    val where = if (vm.galleryFolder.isEmpty()) "Root" else vm.galleryFolder
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        NxChipsRow(NxGalleryChips, vm.filter) { vm.filter = it }
        NxExplorerBar(crumb, { vm.galleryFolder = "" }) {
            NxMiniButton(if (vm.batchMode) "✕ Batch" else "☑️ Pilih Batch") { vm.setBatch(!vm.batchMode) }
            NxMiniButton("🧬 Duplikat", onClick = onDuplicates)
        }
        Text(
            "Tips Batch: ketuk ☑️ Pilih Batch untuk mencentang folder/berkas, atau ketuk folder untuk masuk dan mencentang sebagian berkas di dalamnya.",
            color = c.muted, fontSize = 11.sp, modifier = Modifier.padding(bottom = 6.dp)
        )
        if (folders.isEmpty() && files.isEmpty()) {
            if (vm.loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.primary) }
            else NxEmpty("Tidak ada folder atau media di lokasi ini.")
        } else {
            LazyVerticalGrid(
                GridCells.Fixed(3), Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 130.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                if (folders.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("📁 Folder di $where", Modifier.weight(1f), color = c.text, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                            NxPill("${folders.size} folder")
                        }
                    }
                    items(folders, key = { it.id }) { item ->
                        NxMediaCard(
                            item, vm, vm.batchMode, item.id in vm.selected,
                            onTap = { if (vm.batchMode) vm.toggleSelect(item.id) else vm.galleryFolder = item.fullPath },
                            onDots = { onMenu(item) }, onCheck = { vm.toggleSelect(item.id) }
                        )
                    }
                }
                if (files.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("🖼️ Berkas & Media di $where", Modifier.weight(1f), color = c.text, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                            NxPill("${files.size} file")
                        }
                    }
                    items(files, key = { it.id }) { item ->
                        NxMediaCard(
                            item, vm, vm.batchMode, item.id in vm.selected,
                            onTap = { if (vm.batchMode) vm.toggleSelect(item.id) else onOpen(item) },
                            onDots = { onMenu(item) }, onCheck = { vm.toggleSelect(item.id) }
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ tab Setting

@Composable
fun NxSettingsTab(tdSub: String, cacheCount: Int, failedCount: Int, onOpen: (String) -> Unit, onLogin: () -> Unit, onLogoutAll: () -> Unit) {
    val c = LocalNexora.current
    @Composable fun group(content: @Composable () -> Unit) {
        Column(Modifier.fillMaxWidth().padding(bottom = 6.dp).shadow(1.dp, RoundedCornerShape(16.dp)).background(c.surface, RoundedCornerShape(16.dp)).padding(vertical = 4.dp)) { content() }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(bottom = 130.dp)) {
        NxSectionTitle("Akun Cloud & Autentikasi (Multi-Account)")
        group {
            NxMenuRow("✈️", "LoginActivity (Telegram TDLib)", tdSub, onClick = onLogin)
            NxMenuRow("🇬", "Login Google & Google Drive API", "OAuth 2.0 • Google Drive, Google One & Folder Sync") { onOpen("google") }
            NxMenuRow("☁️", "Penyedia Cloud Lainnya (Multi-Cloud)", "GitHub Releases, Cloudflare R2 / S3, WebDAV & MEGA") { onOpen("multicloud") }
            NxMenuRow("🔑", "TelegramAppConfiguration (Lengkap)", "API ID, API Hash, DC, Port & Proxy MTProto/SOCKS5") { onOpen("tgconfig") }
        }
        NxSectionTitle("Nexora Link — Berbagi Sesi Tanpa Login Ulang")
        group {
            NxMenuRow("📡", "Siarkan API (Host Vercel / GitHub)", "Kirim sesi login lewat relay Vercel / Gist") { onOpen("broadcast") }
            NxMenuRow("📲", "Terima API (Hubungkan via Vercel)", "URL relay + kode pairing untuk tautan permanen") { onOpen("receive") }
        }
        NxSectionTitle("Transfer, Upload Gagal (Retry), Duplikat & Cache")
        group {
            NxMenuRow("⇅", "TransferScreen & Upload Gagal (Retry)", if (failedCount > 0) "$failedCount transfer gagal dapat diulang" else "Antrean aktif & ulangi upload gagal", badge = if (failedCount > 0) "$failedCount gagal" else null) { onOpen("transfer") }
            NxMenuRow("🧬", "Pusat Deteksi & Kelola Duplikat", "SHA-256 identik, nama mirip, lintas cloud") { onOpen("duplicates") }
            NxMenuRow("📊", "StorageScreen & Analytics", "Kapasitas multi-cloud & folder unduhan SAF") { onOpen("storage") }
            NxMenuRow("⚡", "Cache Sesi Pembukaan Aplikasi", "$cacheCount file tersimpan • bisa dibuka offline") { onOpen("cache") }
            NxMenuRow("🗑️", "TrashScreen (Sampah)", "Pulihkan file (Trash: epoch) atau hapus permanen") { onOpen("trash") }
            NxMenuRow("🕒", "ActivityScreen & Ekspor CSV", "Filter all/upload/download/delete & ekspor CSV") { onOpen("activity") }
        }
        NxSectionTitle("Konfigurasi, Perangkat & Tampilan")
        group {
            NxMenuRow("⚙️", "ConfigurationScreen & Backend", "Backend URL/Key, ProviderToggle & ekspor/impor JSON") { onOpen("config") }
            NxMenuRow("📱", "AccountDeviceScreen & SQLite", "ID perangkat, backup nexora_local.db & privasi") { onOpen("account") }
            NxMenuRow("🎨", "PersonalizationScreen", "Tema, aksen warna, bahasa & pola arsip tanggal") { onOpen("personalization") }
            NxMenuRow("🐞", "Developer & Multi-Tab Debug Panel", "Log TDLib, status cloud, SQLite & state inspector") { onOpen("debug") }
        }
        NxSectionTitle("Mode Klasik (layar lama tetap tersedia)")
        group {
            NxMenuRow("🏠", "Dasbor", "Ringkasan cloud & aktivitas terbaru") { onOpen("legacy:Chat") }
            NxMenuRow("📁", "Berkas Lokal", "Pustaka lokal: bagikan, ganti nama, duplikat, analisis") { onOpen("legacy:Berkas") }
            NxMenuRow("🖼️", "Galeri Lokal", "Galeri universal pustaka lokal") { onOpen("legacy:Galeri") }
            NxMenuRow("☁️", "Katalog Cloud", "Daftar cloud dengan filter, folder & unduhan massal") { onOpen("legacy:Cloud") }
        }
        group {
            NxMenuRow("⏻", "Keluar / Putus Semua Tautan", "Hapus sesi TDLib dan token tersimpan di HP ini", danger = true, onClick = onLogoutAll)
        }
    }
}
