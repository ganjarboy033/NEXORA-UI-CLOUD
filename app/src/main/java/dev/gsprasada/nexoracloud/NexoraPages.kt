package dev.gsprasada.nexoracloud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.WorkManager
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

typealias NxConfirm = (String, String, Boolean, () -> Unit) -> Unit

/** Router subhalaman. Halaman lama (Storage, Sampah, Aktivitas, dst.) ditampilkan lewat [legacy] tanpa perubahan logika. */
@Composable
fun NxPageHost(
    id: String,
    vm: ExplorerViewModel,
    actions: NexoraActions,
    records: List<TransferRecord>,
    legacy: @Composable (String) -> Unit,
    detailItem: NxItem?,
    onBack: () -> Unit,
    onOpenPage: (String) -> Unit,
    onConfirm: NxConfirm,
    onViewer: (NxItem) -> Unit
) {
    when (id) {
        "upload" -> NxUploadPage(vm, actions, onBack, onConfirm)
        "tgconfig" -> NxTgConfigPage(onBack, onConfirm)
        "transfer" -> NxTransferPage(vm, records, onBack, onConfirm)
        "duplicates" -> NxDuplicatePage(vm, onBack, onConfirm, onViewer)
        "detail" -> NxDetailPage(detailItem, vm, onBack, onViewer)
        "cache" -> NxCachePage(vm, onBack, onConfirm, onViewer)
        "broadcast" -> NxLinkPage(false, actions, onBack)
        "receive" -> NxLinkPage(true, actions, onBack)
        "google" -> NxGooglePage(actions, onBack)
        "multicloud" -> NxMultiCloudPage(onBack)
        "debug" -> NxDebugPage(vm, onBack)
        "storage" -> NxLegacyPage("StorageScreen & Analytics", "Storage", onBack, legacy)
        "trash" -> NxLegacyPage("TrashScreen (Sampah)", "Sampah", onBack, legacy)
        "activity" -> NxLegacyPage("ActivityScreen & Ekspor CSV", "Aktivitas", onBack, legacy)
        "config" -> NxLegacyPage("ConfigurationScreen & Backend", "Konfigurasi", onBack, legacy)
        "account" -> NxLegacyPage("AccountDeviceScreen & SQLite", "Akun", onBack, legacy)
        "personalization" -> NxLegacyPage("PersonalizationScreen", "Personalisasi", onBack, legacy)
        "legacy:Chat" -> NxLegacyPage("Dasbor", "Chat", onBack, legacy)
        "legacy:Berkas" -> NxLegacyPage("Berkas Lokal", "Berkas", onBack, legacy)
        "legacy:Galeri" -> NxLegacyPage("Galeri Lokal", "Galeri", onBack, legacy)
        "legacy:Cloud" -> NxLegacyPage("Katalog Cloud", "Cloud", onBack, legacy)
        else -> NxSubpage("Halaman", onBack) { NxEmpty("Halaman tidak dikenal.") }
    }
}

@Composable
private fun NxLegacyPage(title: String, name: String, onBack: () -> Unit, legacy: @Composable (String) -> Unit) {
    NxSubpage(title, onBack, scroll = false) {
        Box(Modifier.weight(1f).fillMaxWidth()) { legacy(name) }
    }
}

@Composable
private fun CardTitle(text: String) {
    val c = LocalNexora.current
    Text(text, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
}

@Composable
private fun Note(text: String) {
    val c = LocalNexora.current
    Text(text, color = c.muted, fontSize = 11.5.sp, modifier = Modifier.padding(vertical = 2.dp))
}

// ------------------------------------------------------------------ Upload

@Composable
private fun NxUploadPage(vm: ExplorerViewModel, actions: NexoraActions, onBack: () -> Unit, onConfirm: NxConfirm) {
    val ctx = LocalContext.current
    val c = LocalNexora.current
    val scope = rememberCoroutineScope()
    val sources by NxUploadPicker.sources.collectAsState()
    val authState by TdClient.state.collectAsState()
    val ready = authState is TdAuthState.Ready
    var provider by remember { mutableStateOf("tdlib") }
    var folder by remember { mutableStateOf("") }
    var extract by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val choices = remember(vm.cloudItems, vm.emptyFolders) { vm.folderChoices() }
    NxSubpage("Unggah ke Nexora Cloud", onBack) {
        NxCard {
            CardTitle("1. Penyedia Cloud & Folder Tujuan")
            NxDropdown(
                listOf(
                    "tdlib" to "✈️ Telegram TDLib — Saved Messages / Channel",
                    "gdrive" to "🇬 Google Drive (belum tersedia)",
                    "github" to "🐙 GitHub Releases (belum tersedia)",
                    "lokal" to "📱 Pustaka Lokal HP (Offline)"
                ),
                provider, { provider = it }, disabled = setOf("gdrive", "github")
            )
            if (provider == "tdlib") {
                NxDropdown(choices.map { it to (if (it.isEmpty()) "📁 Root (Utama)" else "📁 $it") }, folder, { folder = it })
                Note("Kosongkan/Root: berkas mengikuti pola arsip tanggal yang diatur di Personalisasi.")
                if (!ready) {
                    Text("⚠️ Login akun Telegram diperlukan untuk upload ke Telegram.", color = c.warning, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    NxButton("✈️ Login Telegram", kind = NxKind.Outline) { actions.openLogin() }
                }
            }
        }
        NxCard {
            CardTitle("2. Sumber Berkas & Folder")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { NxButton("📄 Pilih File (Biasa)", kind = NxKind.Outline) { actions.pickUploadFiles() } }
                Box(Modifier.weight(1f)) { NxButton("📂 Pilih Folder (Batch)", kind = NxKind.Outline) { actions.pickUploadFolder() } }
            }
            if (sources.isEmpty()) {
                Note("Belum ada berkas dipilih.")
            } else {
                Text(
                    "Terpilih: ${sources.size} berkas (${nxSize(sources.sumOf { it.size })})", color = c.primary, fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp)
                )
                sources.take(6).forEach { Text("• " + (if (it.relativeDir.isEmpty()) "" else it.relativeDir + "/") + it.name, color = c.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                if (sources.size > 6) Note("… dan ${sources.size - 6} berkas lainnya")
                NxButton("Kosongkan Pilihan", kind = NxKind.Warn) { NxUploadPicker.clear() }
            }
        }
        NxCard {
            CardTitle("3. Mode Pemrosesan Upload")
            NxSwitchRow("📦 Ekstrak ZIP Otomatis Saat Upload", "Bongkar isi .zip menjadi folder & berkas terpisah (entri berbahaya diabaikan)", extract, enabled = provider == "tdlib") { extract = it }
            NxSwitchRow("☁️ Upload Biasa (Simpan Utuh + Chunking)", "Berkas > 1900 MiB dipecah otomatis menjadi beberapa bagian", checked = !extract, enabled = false) { }
        }
        NxButton(
            if (busy) "Memproses…" else "🚀 Mulai Upload Sekarang",
            enabled = !busy && sources.isNotEmpty() && (provider != "tdlib" || ready)
        ) {
            val picked = sources
            onConfirm("Mulai Unggah Berkas?", "${picked.size} berkas akan diproses ke " + nxSourceLabel(provider) + ".", false) {
                scope.launch {
                    busy = true
                    val message = withContext(Dispatchers.IO) { NxTransferEngine.startUpload(ctx, vm.db, picked, provider, folder, extract) }
                    NxUploadPicker.clear()
                    vm.showToast(message)
                    vm.refresh()
                    busy = false
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Transfer

@Composable
private fun NxTransferPage(vm: ExplorerViewModel, records: List<TransferRecord>, onBack: () -> Unit, onConfirm: NxConfirm) {
    val ctx = LocalContext.current
    val c = LocalNexora.current
    val scope = rememberCoroutineScope()
    val waiting = records.count { it.status == "Menunggu" }
    val running = records.count { it.status == "Berjalan" }
    val done = records.count { it.status == "Selesai" }
    val failed = records.filter { it.status == "Gagal" }
    val active = records.filter { it.status == "Menunggu" || it.status == "Berjalan" }

    fun retry(list: List<TransferRecord>) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { list.count { NxTransferEngine.retry(ctx, vm.db, it) } }
            vm.showToast(if (ok > 0) "$ok transfer diulang dan masuk antrean." else "Tidak ada transfer yang dapat diulang (data pemicu tidak tersedia).")
        }
    }

    NxSubpage("Transfer & Upload Gagal (Retry)", onBack, actions = {
        NxMiniButton("↻ Ulangi Semua") {
            onConfirm("Ulangi Semua Upload Gagal?", "Semua transfer gagal yang datanya tersimpan akan dimasukkan ulang ke antrean.", false) { retry(failed) }
        }
    }) {
        NxCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                NxStat("$waiting", "Menunggu")
                NxStat("$running", "Berjalan", tint = c.primary)
                NxStat("$done", "Selesai", tint = c.success)
                NxStat("${failed.size}", "Gagal", tint = if (failed.isNotEmpty()) c.danger else null)
            }
        }
        NxCard {
            CardTitle("⚠️ Daftar Upload Gagal (Bisa Diulangi)")
            Note("Jika sinyal terputus atau terjadi galat, ketuk ↻ Ulangi untuk memasukkan kembali ke antrean.")
            if (failed.isEmpty()) Note("Tidak ada transfer gagal. 🎉")
            failed.forEach { r ->
                val canRetry = NxTransferEngine.canRetry(ctx, r)
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(r.name, color = c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(r.direction + " • " + r.message, color = c.danger, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (canRetry) NxMiniButton("↻ Ulangi") { retry(listOf(r)) }
                    else Text("tidak dapat diulang", color = c.muted, fontSize = 10.sp)
                }
            }
        }
        NxCard {
            CardTitle("Antrean Berjalan & Selesai")
            if (active.isEmpty() && done == 0) Note("Antrean kosong.")
            active.forEach { r ->
                Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(r.name, Modifier.weight(1f), color = c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        NxMiniButton("Batalkan") {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    val workId = vm.db.workId(r.id)
                                    if (workId != null) runCatching {
                                        WorkManager.getInstance(ctx).cancelWorkById(UUID.fromString(workId))
                                        vm.db.updateTransfer(r.id, "Dibatalkan", "Pembatalan diminta")
                                    }
                                }
                                vm.showToast("Pembatalan diminta untuk ${r.name}.")
                            }
                        }
                    }
                    Text("${r.direction} • ${r.status} • ${r.progress}% • ${nxSize(r.bytesDone)} / ${nxSize(r.bytesTotal)}", color = c.muted, fontSize = 10.5.sp)
                    LinearProgressIndicator(progress = { (r.progress / 100f).coerceIn(0f, 1f) }, color = c.primary, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                }
            }
            records.filter { it.status == "Selesai" }.take(8).forEach { r ->
                Text("✓ ${r.name} • ${r.direction}", color = c.success, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = 2.dp))
            }
            NxButton("Bersihkan Riwayat Selesai", kind = NxKind.Outline) {
                scope.launch {
                    val removed = withContext(Dispatchers.IO) { vm.db.clearFinishedTransfers() }
                    vm.showToast("$removed riwayat selesai dihapus.")
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Duplikat

@Composable
private fun NxDuplicatePage(vm: ExplorerViewModel, onBack: () -> Unit, onConfirm: NxConfirm, onViewer: (NxItem) -> Unit) {
    val c = LocalNexora.current
    var type by remember { mutableStateOf("sha256") }
    val groups = remember(type, vm.cloudItems, vm.localItems) { vm.duplicateGroups(type) }
    val meta = mapOf(
        "sha256" to ("1. Duplikat Konten Identik (SHA-256)" to "Berkas dengan hash SHA-256 yang sama persis meskipun namanya berbeda. Aman menghapus salah satunya untuk menghemat ruang."),
        "name" to ("2. Nama & Ukuran Mirip" to "Berkas dengan nama dan ukuran yang sama. Periksa dulu sebelum menghapus karena isinya belum tentu identik."),
        "cross" to ("3. Duplikat Lintas Cloud" to "Berkas yang sama yang tersimpan di lebih dari satu sumber (mis. Telegram dan lokal)."),
        "media" to ("4. Foto / Video Serupa" to "Membutuhkan analisis visual (perceptual hash) dan belum tersedia di versi ini.")
    )
    NxSubpage("🧬 Pusat Deteksi Duplikat", onBack, actions = { NxMiniButton("🔍 Pindai Ulang") { vm.refresh() } }) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NxChip("SHA-256", type == "sha256") { type = "sha256" }
            NxChip("Nama", type == "name") { type = "name" }
            NxChip("Lintas", type == "cross") { type = "cross" }
            NxChip("Media", type == "media") { type = "media" }
        }
        NxCard {
            CardTitle(meta.getValue(type).first)
            Note(meta.getValue(type).second)
        }
        if (type == "media") return@NxSubpage
        if (groups.isEmpty()) NxEmpty("Tidak ditemukan duplikat untuk kategori ini. ✅")
        groups.forEachIndexed { index, group ->
            NxCard {
                Text("Kelompok ${index + 1} • ${group.size} berkas • ${nxSize(group.first().sizeBytes)} masing-masing", color = c.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                group.forEachIndexed { i, item ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable { onViewer(item) }) {
                            Text((if (i == 0) "⭐ " else "") + item.name, color = c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(nxSourceLabel(item.source) + (if (item.folder.isNotEmpty()) " • " + item.folder else ""), color = c.muted, fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (i > 0) NxMiniButton("🗑️ Sampah") {
                            onConfirm("Pindahkan ke Sampah?", "${item.name} dipindahkan ke Sampah (dapat dipulihkan).", true) { vm.trash(listOf(item)) }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Detail & Cache

@Composable
private fun NxDetailPage(item: NxItem?, vm: ExplorerViewModel, onBack: () -> Unit, onViewer: (NxItem) -> Unit) {
    val c = LocalNexora.current
    NxSubpage("Detail Berkas", onBack) {
        if (item == null) { NxEmpty("Tidak ada berkas dipilih."); return@NxSubpage }
        NxCard {
            Text(item.name, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("${nxSize(item.sizeBytes)} • ${item.mime}", color = c.muted, fontSize = 12.sp)
            Text("Sumber: ${nxSourceLabel(item.source)}" + if (item.folder.isNotEmpty()) " • ${item.folder}" else "", color = c.muted, fontSize = 12.sp)
            Text("Tanggal: ${item.dateText}", color = c.muted, fontSize = 12.sp)
            Text("SHA-256:", color = c.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
            Text(item.sha256.ifBlank { "—" }, color = c.text, fontSize = 11.sp)
            Text(
                if (vm.isCached(item)) "⚡ Tersedia offline (cache sesi / lokal)" else "Belum ada di cache sesi",
                color = if (vm.isCached(item)) c.success else c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)
            )
            NxButton("Unduh ke Folder Pilihan") { vm.enqueueDownload(item, true) }
            NxButton("Pratinjau / Buka File", kind = NxKind.Outline) { onViewer(item) }
            NxButton("Hapus (Pindahkan ke Sampah)", kind = NxKind.Danger) { vm.trash(listOf(item)); onBack() }
        }
    }
}

@Composable
private fun NxCachePage(vm: ExplorerViewModel, onBack: () -> Unit, onConfirm: NxConfirm, onViewer: (NxItem) -> Unit) {
    val c = LocalNexora.current
    val files = vm.sessionCacheFiles()
    NxSubpage("Cache Sesi & Mode Offline", onBack) {
        NxCard {
            CardTitle("Cache Sesi Pembukaan Aplikasi (session_preview)")
            Note("Berkas Telegram yang dipratinjau disimpan di sini dan tetap tersedia saat offline sampai cache dibersihkan.")
            if (files.isEmpty()) Note("Belum ada berkas di cache sesi.")
            files.forEach { f ->
                val item = vm.cloudItems.firstOrNull { it.cloudId.toString() == f.parentFile?.name }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp)).clickable(enabled = item != null) { if (item != null) onViewer(item) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(nxEmoji(nxKind("", f.name)), fontSize = 16.sp)
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(f.name, color = c.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(nxSize(f.length()), color = c.muted, fontSize = 10.5.sp)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("Total: ${nxSize(files.sumOf { it.length() })} dalam ${files.size} berkas", color = c.muted, fontSize = 11.sp)
            NxButton("Bersihkan Cache Sesi Manual", kind = NxKind.Danger, enabled = files.isNotEmpty()) {
                onConfirm("Bersihkan Semua Cache Sesi?", "Berkas yang tersimpan offline dihapus dari perangkat. Berkas asli di Telegram tidak terpengaruh.", true) { vm.clearSessionCache() }
            }
        }
    }
}
