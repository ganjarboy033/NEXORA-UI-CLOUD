package dev.gsprasada.nexoracloud

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val ViewerBg = Color(0xFF0B0F14)

@Composable
private fun ViewerDownButton(icon: String, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, fontSize = 18.sp)
        Text(label, color = Color(0xFFCBD5E1), fontSize = 10.sp)
    }
}

@Composable
private fun NxImageStage(file: File) {
    val bitmap = rememberNxThumb(file, 1400)
    var scale by remember(file.path) { mutableFloatStateOf(1f) }
    var offset by remember(file.path) { mutableStateOf(Offset.Zero) }
    if (bitmap == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) }
    } else {
        Image(
            bitmap, contentDescription = file.name, contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
                .pointerInput(file.path) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        offset = if (scale > 1f) offset + pan else Offset.Zero
                    }
                }
                .pointerInput(file.path) {
                    detectTapGestures(onDoubleTap = { scale = if (scale > 1f) 1f else 2.5f; offset = Offset.Zero })
                }
                .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y)
        )
    }
}

@Composable
private fun NxTextStage(vm: ExplorerViewModel, item: NxItem, file: File) {
    var original by remember(file.path) { mutableStateOf<String?>(null) }
    var text by remember(file.path) { mutableStateOf("") }
    LaunchedEffect(file.path) {
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = ByteArray(minOf(file.length(), 400_000L).toInt())
                val read = file.inputStream().use { it.read(bytes) }
                String(bytes, 0, maxOf(read, 0), Charsets.UTF_8)
            }.getOrDefault("(Berkas teks tidak dapat dibaca)")
        }
        original = loaded; text = loaded
    }
    val editable = item.source == "lokal"
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Text(
            if (editable) "Editor teks • berkas lokal" else "Pratinjau teks • hanya baca (berkas Telegram)",
            color = Color(0xFF94A3B8), fontSize = 11.sp, modifier = Modifier.padding(bottom = 6.dp)
        )
        NxField(text, { if (editable) text = it }, "Isi berkas", Modifier.weight(1f), singleLine = false, minLines = 12, enabled = editable)
        if (editable && original != null && text != original) {
            NxButton("💾 Simpan Perubahan") { vm.saveInlineText(item, text); original = text }
        }
    }
}

@Composable
private fun NxGenericStage(item: NxItem, file: File, onOpenExternal: (File, String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(nxEmoji(item.kind), fontSize = 64.sp)
        Spacer(Modifier.height(10.dp))
        Text(item.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text("${nxSize(file.length())} • ${item.mime}", color = Color(0xFF94A3B8), fontSize = 12.sp)
        Spacer(Modifier.height(14.dp))
        NxButton("Buka dengan aplikasi lain") { onOpenExternal(file, item.mime) }
    }
}

/** Viewer imersif layar penuh: pratinjau gambar (zoom cubit), teks, HUD buffering dengan pembatalan, filmstrip, dan bar aksi bawah. */
@Composable
fun NxViewerOverlay(
    vm: ExplorerViewModel,
    tdTransfers: List<TdTransfer>,
    onMore: (NxItem) -> Unit,
    onDownload: (NxItem) -> Unit,
    onInfo: (NxItem) -> Unit,
    onShare: (NxItem, File) -> Unit,
    onDelete: (NxItem) -> Unit,
    onOpenExternal: (File, String) -> Unit
) {
    val current = vm.viewerItem
    var last by remember { mutableStateOf<NxItem?>(null) }
    LaunchedEffect(current) { if (current != null) last = current }
    AnimatedVisibility(current != null, enter = fadeIn(tween(180)), exit = fadeOut(tween(140))) {
        val item = last ?: return@AnimatedVisibility
        val state = vm.viewerState
        Box(Modifier.fillMaxSize().background(ViewerBg)) {
            // ---- panggung
            Box(Modifier.fillMaxSize().statusBarsPadding().padding(top = 56.dp, bottom = 150.dp), contentAlignment = Alignment.Center) {
                when (state) {
                    is NxViewerState.Ready -> when (item.kind) {
                        "photo" -> NxImageStage(state.file)
                        "text" -> NxTextStage(vm, item, state.file)
                        else -> NxGenericStage(item, state.file, onOpenExternal)
                    }
                    NxViewerState.Loading -> {
                        val t = tdTransfers.lastOrNull { !it.finished && it.name == item.name && it.direction == TdTransferDirection.DOWNLOAD }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(contentAlignment = Alignment.Center) {
                                if (t != null && t.totalBytes > 0) CircularProgressIndicator(progress = { t.fraction }, color = Color.White, modifier = Modifier.size(78.dp))
                                else CircularProgressIndicator(color = Color.White, modifier = Modifier.size(78.dp))
                                Box(
                                    Modifier.size(44.dp).clip(CircleShape).background(Color(0x33FFFFFF)).clickable { vm.cancelViewerLoad() },
                                    contentAlignment = Alignment.Center
                                ) { Text("✕", color = Color.White, fontSize = 18.sp) }
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                if (t != null) "${nxSize(t.doneBytes)} / ${nxSize(t.totalBytes)}" else "Menghubungi Telegram…",
                                color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                            )
                            Text("Ketuk ✕ untuk membatalkan unduhan pratinjau", color = Color(0xFF94A3B8), fontSize = 11.sp)
                        }
                    }
                    NxViewerState.Cancelled -> Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Pratinjau Dibatalkan (✕)", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("Unduhan dihentikan untuk menghemat kuota.", color = Color(0xFF94A3B8), fontSize = 12.sp, textAlign = TextAlign.Center)
                        NxButton("↻ Muat Ulang Pratinjau") { vm.retryViewer() }
                    }
                    is NxViewerState.Failed -> Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Pratinjau gagal", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(state.message, color = Color(0xFFFCA5A5), fontSize = 12.sp, textAlign = TextAlign.Center)
                        NxButton("↻ Coba Lagi") { vm.retryViewer() }
                    }
                    NxViewerState.Idle -> Unit
                }
            }

            // ---- bar atas
            Row(
                Modifier.fillMaxWidth().statusBarsPadding()
                    .background(Brush.verticalGradient(listOf(Color(0xEB0B0F14), Color.Transparent))).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                NxCircleButton("←", bg = Color(0x33FFFFFF), fg = Color.White, size = 38) { vm.closeViewer() }
                Text(item.name, Modifier.weight(1f), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                NxCircleButton(if (vm.isFav(item)) "♥" else "♡", bg = Color(0x33FFFFFF), fg = if (vm.isFav(item)) Color(0xFFF87171) else Color.White, size = 38) { vm.toggleFavorite(item) }
                NxCircleButton("⋮", bg = Color(0x33FFFFFF), fg = Color.White, size = 38) { onMore(item) }
            }

            // ---- filmstrip + bar bawah
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xF2111820)).navigationBarsPadding()) {
                val siblings = remember(item.id, vm.cloudItems, vm.localItems) {
                    vm.allFiles().filter { it.source == item.source && it.folder == item.folder }.take(40)
                }
                if (siblings.size > 1) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        siblings.forEach { sib ->
                            val thumb = rememberNxThumb(nxThumbFile(vm, sib), 80)
                            Box(
                                Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF1E293B))
                                    .border(if (sib.id == item.id) 2.dp else 0.dp, if (sib.id == item.id) Color.White else Color.Transparent, RoundedCornerShape(8.dp))
                                    .clickable { vm.switchViewer(sib) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (thumb != null) Image(thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                else Text(nxEmoji(sib.kind), fontSize = 18.sp)
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
                    ViewerDownButton("⬇️", "Unduh", Modifier.weight(1f)) { onDownload(item) }
                    ViewerDownButton("⚡", "Cache Sesi", Modifier.weight(1f)) { vm.pinOffline(item) }
                    ViewerDownButton("ℹ️", "Info & Label", Modifier.weight(1f)) { onInfo(item) }
                    ViewerDownButton("🔗", "Bagikan", Modifier.weight(1f)) {
                        val file = (state as? NxViewerState.Ready)?.file
                        if (file != null) onShare(item, file) else vm.showToast("Tunggu berkas selesai dimuat untuk membagikan.")
                    }
                    ViewerDownButton("🗑️", "Hapus", Modifier.weight(1f)) { onDelete(item) }
                }
            }
        }
    }
}
