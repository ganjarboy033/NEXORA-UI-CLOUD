package dev.gsprasada.nexoracloud

import android.content.Context
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.drinkless.tdlib.TdApi
import org.json.JSONArray
import org.json.JSONObject

enum class TdTransferDirection { UPLOAD, DOWNLOAD }

/** Progres satu transfer yang dapat diamati UI Compose lewat [TdCloud.transfers]. */
data class TdTransfer(
    val id: String,
    val name: String,
    val direction: TdTransferDirection,
    val doneBytes: Long,
    val totalBytes: Long,
    val finished: Boolean = false,
    val error: String? = null
) {
    val fraction: Float get() = if (totalBytes > 0) (doneBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
}

/** Chat Telegram tempat file disimpan: Saved Messages atau channel/grup pilihan pengguna. */
data class TdStorageTarget(val chatId: Long, val title: String, val isSavedMessages: Boolean)

/**
 * Metadata file disimpan di caption pesan dokumen (kompatibel dengan caption backend Nexora):
 *
 *     NEXORA CLOUD
 *     Folder: 2026/11/20/03 WIB     (atau jalur folder kustom, "/" = akar)
 *     File: nama.ext
 *     SHA256: <hex>
 *     Chunk: <grup> <indeks>/<total> <ukuranTotal>   (hanya untuk file besar yang dipecah)
 *     Trash: <epoch detik>                           (hanya untuk file di Sampah)
 */
internal data class TdCaption(
    val folder: String?,
    val fileName: String?,
    val sha256: String?,
    val trashedAt: Long,
    val chunkGroup: String?,
    val chunkIndex: Int,
    val chunkTotal: Int,
    val chunkTotalSize: Long
) {
    fun render(): String = buildString {
        append(HEADER)
        if (folder != null) append("\nFolder: ").append(if (folder.isEmpty()) "/" else folder)
        if (!fileName.isNullOrEmpty()) append("\nFile: ").append(fileName)
        if (!sha256.isNullOrEmpty()) append("\nSHA256: ").append(sha256)
        if (chunkGroup != null) {
            append("\nChunk: ").append(chunkGroup).append(' ').append(chunkIndex).append('/').append(chunkTotal)
                .append(' ').append(chunkTotalSize)
        }
        if (trashedAt > 0L) append("\nTrash: ").append(trashedAt)
    }

    companion object {
        const val HEADER = "NEXORA CLOUD"
        private val CHUNK = Regex("""^(\S+)\s+(\d+)/(\d+)\s+(\d+)$""")

        fun parse(text: String): TdCaption {
            var folder: String? = null
            var file: String? = null
            var sha: String? = null
            var trashed = 0L
            var group: String? = null
            var index = 0
            var total = 0
            var totalSize = 0L
            for (raw in text.split('\n')) {
                val line = raw.trim()
                when {
                    line.startsWith("Folder:") -> {
                        val value = line.substringAfter(':').trim().removeSuffix("WIB").trim().trim('/')
                        folder = if (value == "-") "" else value
                    }
                    line.startsWith("File:") -> file = line.substringAfter(':').trim()
                    line.startsWith("SHA256:") -> sha = line.substringAfter(':').trim()
                    line.startsWith("Trash:") -> trashed = line.substringAfter(':').trim().toLongOrNull() ?: 0L
                    line.startsWith("Chunk:") -> CHUNK.matchEntire(line.substringAfter(':').trim())?.let { m ->
                        group = m.groupValues[1]
                        index = m.groupValues[2].toInt()
                        total = m.groupValues[3].toInt()
                        totalSize = m.groupValues[4].toLong()
                    }
                }
            }
            return TdCaption(folder, file, sha, trashed, group, index, total, totalSize)
        }
    }
}

/** Satu file cloud (pesan dokumen) beserta metadata hasil parse caption. */
internal data class TdEntry(
    val messageId: Long,
    val name: String,
    val mime: String,
    val size: Long,
    val dateSeconds: Int,
    val folder: String,
    val sha256: String,
    val trashedAt: Long,
    val fileId: Int,
    val caption: TdCaption
)

private class TdIndex(
    val heads: List<TdEntry>,
    val parts: Map<String, List<TdEntry>>,
    val byId: Map<Long, TdEntry>
)

private class SendOutcome(val message: TdApi.Message?, val error: String?)

private class SendWaiter(val fileId: Int, val onProgress: (Long) -> Unit) {
    private val latch = CountDownLatch(1)
    @Volatile var outcome: SendOutcome? = null
    fun finish(result: SendOutcome) { outcome = result; latch.countDown() }
    fun await() { latch.await() }
}

/**
 * Implementasi cloud berbasis akun Telegram (TDLib), mengikuti mekanisme Unlim Cloud:
 * file = pesan dokumen di Saved Messages / channel, metadata di caption, file besar dipecah menjadi chunk.
 */
class TdCloud private constructor(private val context: Context) {
    private val prefs = context.getSharedPreferences("nexora_tdlib_cloud", Context.MODE_PRIVATE)
    private val activity = TdActivityLog(context)
    private val indexLock = Any()
    @Volatile private var index: TdIndex? = null
    @Volatile private var indexChatId = 0L
    @Volatile private var meId = 0L
    @Volatile private var savedMessagesChatId = 0L

    private val transferState = MutableStateFlow<List<TdTransfer>>(emptyList())
    /** Daftar transfer aktif/selesai secara real-time (untuk UI). */
    val transfers: StateFlow<List<TdTransfer>> = transferState

    private fun transferStart(id: String, name: String, direction: TdTransferDirection, total: Long) {
        AppLog.info("${if (direction == TdTransferDirection.UPLOAD) "Upload" else "Download"} mulai: $name (${total} B)")
        transferState.update { list -> list.filterNot { it.id == id } + TdTransfer(id, name, direction, 0L, total) }
    }

    private fun transferProgress(id: String, done: Long, total: Long) =
        transferState.update { list -> list.map { if (it.id == id) it.copy(doneBytes = done, totalBytes = total) else it } }

    private fun transferEnd(id: String, error: String?) {
        val name = transferState.value.firstOrNull { it.id == id }?.name.orEmpty()
        if (error == null) AppLog.ok("Transfer selesai: $name") else AppLog.err("Transfer gagal: $name — $error")
        transferState.update { list ->
            list.map { if (it.id == id) it.copy(finished = true, error = error, doneBytes = if (error == null) it.totalBytes else it.doneBytes) else it }
        }
    }

    /** Menghapus transfer yang sudah selesai/gagal dari daftar. */
    fun clearFinishedTransfers() = transferState.update { list -> list.filterNot { it.finished } }

    /** Pembungkus progres: meneruskan ke [target] dan memperbarui StateFlow (dibatasi agar tidak membanjiri UI). */
    private fun reporter(id: String, target: (Long, Long) -> Unit): (Long, Long) -> Unit {
        var last = -1L
        return { done, total ->
            target(done, total)
            if (last < 0 || done - last >= 256 * 1024 || done >= total) { last = done; transferProgress(id, done, total) }
        }
    }

    private val sendWaiters = ConcurrentHashMap<Long, SendWaiter>()
    private val earlyOutcomes = ConcurrentHashMap<Long, SendOutcome>()
    private val downloadProgress = ConcurrentHashMap<Int, (Long) -> Unit>()

    init {
        TdClient.addUpdateListener { update -> onUpdate(update) }
    }

    // ------------------------------------------------------------ update TDLib

    private fun onUpdate(update: TdApi.Object) {
        when (update) {
            is TdApi.UpdateFile -> {
                val file = update.file
                for (waiter in sendWaiters.values) if (waiter.fileId == file.id) waiter.onProgress(file.remote.uploadedSize)
                downloadProgress[file.id]?.invoke(file.local.downloadedSize)
            }
            is TdApi.UpdateMessageSendSucceeded -> {
                deliver(update.oldMessageId, SendOutcome(update.message, null))
                if (update.message.chatId == indexChatId) invalidate()
            }
            is TdApi.UpdateMessageSendFailed -> {
                deliver(update.oldMessageId, SendOutcome(null, update.error?.message ?: "Pengiriman ditolak Telegram."))
            }
            is TdApi.UpdateNewMessage -> if (update.message.chatId == indexChatId) invalidate()
            is TdApi.UpdateDeleteMessages -> if (update.chatId == indexChatId) invalidate()
            is TdApi.UpdateMessageContent -> if (update.chatId == indexChatId) invalidate()
            else -> Unit
        }
    }

    private fun deliver(oldMessageId: Long, outcome: SendOutcome) {
        val waiter = sendWaiters[oldMessageId]
        if (waiter != null) {
            waiter.finish(outcome)
        } else {
            // Update dapat tiba sebelum pemanggil sempat mendaftar; simpan sebentar.
            if (earlyOutcomes.size > 200) earlyOutcomes.clear()
            earlyOutcomes[oldMessageId] = outcome
        }
    }

    fun invalidate() { index = null }

    // ------------------------------------------------------------ chat penyimpanan

    private fun ensureOwner(): Long {
        if (meId != 0L) return meId
        val me = TdClient.call(TdApi.GetMe())
        val owner = prefs.getLong(KEY_OWNER, 0L)
        if (owner != 0L && owner != me.id) prefs.edit().remove(KEY_CHAT).remove(KEY_FOLDERS).apply()
        prefs.edit().putLong(KEY_OWNER, me.id).apply()
        meId = me.id
        return me.id
    }

    /** ID akun yang sedang login (hasil TdApi.GetMe); sekaligus ID chat Saved Messages. */
    fun selfUserId(): Long = ensureOwner()

    /** ID chat penyimpanan aktif (Saved Messages bila belum dipilih chat lain). */
    fun chatId(): Long {
        val owner = ensureOwner()
        val stored = prefs.getLong(KEY_CHAT, 0L)
        if (stored != 0L) return stored
        if (savedMessagesChatId != 0L) return savedMessagesChatId
        val chat = TdClient.call(TdApi.CreatePrivateChat(owner, false))
        savedMessagesChatId = chat.id
        return chat.id
    }

    fun currentTarget(): TdStorageTarget {
        val stored = prefs.getLong(KEY_CHAT, 0L)
        if (stored == 0L) return TdStorageTarget(chatId(), "Saved Messages", true)
        val chat = TdClient.call(TdApi.GetChat(stored))
        return TdStorageTarget(stored, chat.title, false)
    }

    fun useSavedMessages(): TdStorageTarget {
        ensureOwner()
        prefs.edit().remove(KEY_CHAT).apply()
        invalidate()
        return currentTarget()
    }

    /** Memakai chat yang sudah ada: ID numerik (mis. -1001234567890) atau @username channel publik. */
    fun useChat(reference: String): TdStorageTarget {
        ensureOwner()
        val ref = reference.trim()
        require(ref.isNotEmpty()) { "Isi ID chat atau @username." }
        runCatching { TdClient.call(TdApi.LoadChats(null, 100)) } // 404 berarti semua chat sudah termuat
        val chat = ref.toLongOrNull()?.let { TdClient.call(TdApi.GetChat(it)) }
            ?: TdClient.call(TdApi.SearchPublicChat(ref.removePrefix("https://t.me/").removePrefix("@")))
        prefs.edit().putLong(KEY_CHAT, chat.id).apply()
        invalidate()
        return TdStorageTarget(chat.id, chat.title, false)
    }

    /** Membuat channel privat baru khusus penyimpanan Nexora dan langsung memakainya. */
    fun createPrivateChannel(title: String = "Nexora Cloud"): TdStorageTarget {
        ensureOwner()
        val chat = TdClient.call(
            TdApi.CreateNewSupergroupChat(title.trim().ifBlank { "Nexora Cloud" }, false, true, "Penyimpanan Nexora Cloud", null, 0, false)
        )
        prefs.edit().putLong(KEY_CHAT, chat.id).apply()
        invalidate()
        return TdStorageTarget(chat.id, chat.title, false)
    }

    fun onLoggedOut() {
        prefs.edit().clear().apply()
        activity.clear()
        meId = 0L
        savedMessagesChatId = 0L
        invalidate()
    }

    fun statusText(): String {
        val target = currentTarget()
        return "Akun Telegram terhubung langsung (TDLib). Chat penyimpanan: ${target.title}."
    }

    // ------------------------------------------------------------ indeks & daftar

    private fun entries(forceRefresh: Boolean): TdIndex {
        synchronized(indexLock) {
            val chat = chatId()
            val cached = index
            if (!forceRefresh && cached != null && indexChatId == chat) return cached
            val messages = ArrayList<TdApi.Message>()
            val seen = HashSet<Long>()
            var from = 0L
            var pages = 0
            while (pages++ < 2000) {
                // offset 0 menyertakan from_message_id itu sendiri, jadi duplikat disaring lewat [seen].
                val page = TdClient.call(TdApi.GetChatHistory(chat, from, 0, 100, false), 120)
                if (page.messages.isEmpty()) break
                var added = 0
                for (m in page.messages) if (seen.add(m.id)) { messages.add(m); added++ }
                if (added == 0) break
                from = page.messages.minOf { it.id }
            }
            val built = buildIndex(messages)
            index = built
            indexChatId = chat
            return built
        }
    }

    private fun buildIndex(messages: List<TdApi.Message>): TdIndex {
        val all = ArrayList<TdEntry>()
        for (message in messages) toEntry(message)?.let { all.add(it) }
        val parts = all.filter { it.caption.chunkGroup != null }
            .groupBy { it.caption.chunkGroup!! }
            .mapValues { (_, list) -> list.sortedBy { it.caption.chunkIndex } }
        val heads = ArrayList<TdEntry>()
        for (entry in all) {
            val group = entry.caption.chunkGroup
            if (group == null) {
                heads.add(entry)
            } else if (entry.caption.chunkIndex == 1) {
                val total = if (entry.caption.chunkTotalSize > 0) entry.caption.chunkTotalSize else parts[group].orEmpty().sumOf { it.size }
                heads.add(entry.copy(size = total))
            }
        }
        return TdIndex(heads, parts, heads.associateBy { it.messageId })
    }

    /** Mengenali pesan berkonten file: dokumen, video, audio, dan foto. Pesan lain diabaikan. */
    private fun toEntry(message: TdApi.Message): TdEntry? {
        val file: TdApi.File
        val rawName: String?
        val rawMime: String?
        val captionText: String
        when (val content = message.content) {
            is TdApi.MessageDocument -> {
                file = content.document.document
                rawName = content.document.fileName
                rawMime = content.document.mimeType
                captionText = content.caption?.text.orEmpty()
            }
            is TdApi.MessageVideo -> {
                file = content.video.video
                rawName = content.video.fileName
                rawMime = content.video.mimeType.orEmpty().ifBlank { "video/mp4" }
                captionText = content.caption?.text.orEmpty()
            }
            is TdApi.MessageAudio -> {
                file = content.audio.audio
                rawName = content.audio.fileName
                rawMime = content.audio.mimeType.orEmpty().ifBlank { "audio/mpeg" }
                captionText = content.caption?.text.orEmpty()
            }
            is TdApi.MessagePhoto -> {
                val best = content.photo.sizes.maxByOrNull { it.width.toLong() * it.height } ?: return null
                file = best.photo
                rawName = "foto-${message.id}.jpg"
                rawMime = "image/jpeg"
                captionText = content.caption?.text.orEmpty()
            }
            else -> return null
        }
        val caption = TdCaption.parse(captionText)
        val name = caption.fileName?.takeIf { it.isNotBlank() }
            ?: rawName.orEmpty().ifBlank { "berkas-${message.id}" }
        val size = if (file.size > 0) file.size else file.expectedSize
        return TdEntry(
            messageId = message.id,
            name = name,
            mime = rawMime.orEmpty().ifBlank { "application/octet-stream" },
            size = size,
            dateSeconds = message.date,
            folder = caption.folder ?: dateFolder(message.date),
            sha256 = caption.sha256.orEmpty(),
            trashedAt = caption.trashedAt,
            fileId = file.id,
            caption = caption
        )
    }

    private fun toItem(e: TdEntry) = CloudFileItem(e.messageId, e.name, e.mime, e.size, wibTime(e.dateSeconds.toLong()), e.folder, e.sha256)

    private fun matchesType(e: TdEntry, type: String): Boolean = when (type) {
        "image" -> e.mime.startsWith("image/", true)
        "video" -> e.mime.startsWith("video/", true)
        "audio" -> e.mime.startsWith("audio/", true)
        "document" -> e.mime.startsWith("text/", true) || e.mime in DOCUMENT_MIMES
        "archive" -> e.mime.contains("zip", true) || e.mime.contains("compressed", true) ||
            Regex("\\.(zip|rar|7z|tar|gz|bz2|xz)$", RegexOption.IGNORE_CASE).containsMatchIn(e.name)
        else -> true
    }

    fun list(limit: Int = 100, offset: Int = 0, query: String = "", type: String = "all", sort: String = "date", direction: String = "desc"): List<CloudFileItem> {
        val idx = entries(forceRefresh = offset == 0)
        val q = query.trim()
        var rows = idx.heads.filter { it.trashedAt == 0L && matchesType(it, type.lowercase()) && (q.isEmpty() || it.name.contains(q, true)) }
        val comparator: Comparator<TdEntry> = when (sort.lowercase()) {
            "name" -> compareBy<TdEntry, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
            "size" -> compareBy<TdEntry> { it.size }
            else -> compareBy<TdEntry> { it.dateSeconds }
        }
        rows = if (direction.equals("asc", true)) rows.sortedWith(comparator.thenBy { it.messageId })
        else rows.sortedWith(comparator.thenBy { it.messageId }.reversed())
        return rows.drop(offset.coerceAtLeast(0)).take(limit.coerceIn(1, 100)).map(::toItem)
    }

    fun stats(): CloudCatalogStats {
        val active = entries(false).heads.filter { it.trashedAt == 0L }
        val latest = active.maxOfOrNull { it.dateSeconds }
        return CloudCatalogStats(
            totalFiles = active.size.toLong(),
            totalBytes = active.sumOf { it.size },
            images = active.count { it.mime.startsWith("image/", true) }.toLong(),
            videos = active.count { it.mime.startsWith("video/", true) }.toLong(),
            audios = active.count { it.mime.startsWith("audio/", true) }.toLong(),
            documents = active.count { it.mime.startsWith("application/", true) || it.mime.startsWith("text/", true) }.toLong(),
            latestUpload = latest?.let { wibTime(it.toLong()) }
        )
    }

    fun storageBreakdown(): StorageBreakdown {
        val active = entries(false).heads.filter { it.trashedAt == 0L }
        fun group(pattern: String, max: Int): List<StoragePeriod> {
            val format = wibFormat(pattern)
            return active.groupBy { format.format(java.util.Date(it.dateSeconds * 1000L)) }
                .map { (period, list) -> StoragePeriod(period, list.size.toLong(), list.sumOf { it.size }) }
                .sortedByDescending { it.period }.take(max)
        }
        return StorageBreakdown("Asia/Jakarta", group("yyyy-MM", 24), group("yyyy", 10))
    }

    fun listTrash(): List<TrashFileItem> =
        entries(true).heads.filter { it.trashedAt > 0L }.sortedByDescending { it.trashedAt }.take(200)
            .map { TrashFileItem(it.messageId, it.name, it.mime, it.size, wibTime(it.trashedAt)) }

    // ------------------------------------------------------------ upload

    fun upload(file: File, folder: String? = null, onProgress: (Long, Long) -> Unit = { _, _ -> }): String {
        require(file.isFile && file.canRead()) { "File lokal tidak dapat dibaca." }
        val total = file.length()
        require(total > 0L) { "File kosong tidak dapat diunggah ke Telegram." }
        val chat = chatId()
        val targetFolder = normalizeFolder(folder ?: defaultFolder())
        val tid = UUID.randomUUID().toString()
        transferStart(tid, file.name, TdTransferDirection.UPLOAD, total)
        val report = reporter(tid, onProgress)
        try {
            val sha = sha256Hex(file)
            report(0L, total)
            val headId: Long
            if (total <= CHUNK_BYTES) {
                val caption = TdCaption(targetFolder, file.name, sha, 0L, null, 0, 0, 0L)
                headId = sendDocument(chat, file, caption.render()) { sent -> report(sent.coerceAtMost(total), total) }.id
            } else {
                headId = uploadChunked(chat, file, total, targetFolder, sha, report)
            }
            report(total, total)
            activity.record("upload", headId, file.name, JSONObject().put("byte_size", total).put("folder", targetFolder))
            invalidate()
            transferEnd(tid, null)
            return "Unggahan selesai: ${file.name}"
        } catch (e: Throwable) {
            transferEnd(tid, e.message ?: "Unggahan gagal")
            throw e
        }
    }

    private fun uploadChunked(chat: Long, file: File, total: Long, folder: String, sha: String, onProgress: (Long, Long) -> Unit): Long {
        val group = UUID.randomUUID().toString().replace("-", "").take(10)
        val count = ((total + CHUNK_BYTES - 1) / CHUNK_BYTES).toInt()
        val dir = File(File(context.cacheDir, "td_chunks"), group).apply { mkdirs() }
        val sentIds = ArrayList<Long>()
        var uploaded = 0L
        try {
            file.inputStream().buffered().use { input ->
                for (i in 1..count) {
                    val partSize = minOf(CHUNK_BYTES, total - (i - 1) * CHUNK_BYTES)
                    val part = File(dir, file.name + ".part" + i.toString().padStart(3, '0'))
                    copyExactly(input, part, partSize)
                    try {
                        val caption = TdCaption(folder, file.name, sha, 0L, group, i, count, total)
                        val base = uploaded
                        val sent = sendDocument(chat, part, caption.render()) { s -> onProgress((base + s).coerceAtMost(total), total) }
                        sentIds.add(sent.id)
                    } finally {
                        part.delete()
                    }
                    uploaded += partSize
                }
            }
            return sentIds.first()
        } catch (e: Exception) {
            if (sentIds.isNotEmpty()) runCatching { TdClient.call(TdApi.DeleteMessages(chat, sentIds.toLongArray(), true)) }
            throw e
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun sendDocument(chat: Long, file: File, caption: String, onSent: (Long) -> Unit): TdApi.Message {
        val document = TdApi.InputDocument(TdApi.InputFileLocal(file.absolutePath), null, false)
        val content = TdApi.InputMessageDocument(document, TdApi.FormattedText(caption, emptyArray<TdApi.TextEntity>()))
        val pending = TdClient.call(TdApi.SendMessage(chat, null, null, null, null, content), 60)
        val fileId = (pending.content as? TdApi.MessageDocument)?.document?.document?.id ?: -1
        val waiter = SendWaiter(fileId, onSent)
        sendWaiters[pending.id] = waiter
        earlyOutcomes.remove(pending.id)?.let { waiter.finish(it) }
        try {
            waiter.await()
        } catch (e: InterruptedException) {
            // Menghapus pesan yang masih pending otomatis membatalkan unggahannya di TDLib.
            runCatching { TdClient.send(TdApi.DeleteMessages(chat, longArrayOf(pending.id), true)) }
            throw e
        } finally {
            sendWaiters.remove(pending.id)
        }
        val result = waiter.outcome ?: throw IOException("Hasil pengiriman tidak diketahui.")
        result.error?.let { throw TdException(400, it) }
        return result.message ?: throw IOException("Telegram tidak mengembalikan pesan hasil unggah.")
    }

    // ------------------------------------------------------------ download

    fun download(messageId: Long, output: OutputStream, onProgress: (Long, Long) -> Unit = { _, _ -> }): Long {
        val idx = entries(false).byId[messageId] ?: entries(true).byId[messageId]
            ?: throw IOException("File tidak ditemukan di chat penyimpanan.")
        val group = idx.caption.chunkGroup
        val parts: List<TdEntry> = if (group == null) {
            listOf(idx)
        } else {
            val all = entries(false).parts[group].orEmpty()
            if (all.size != idx.caption.chunkTotal) {
                throw IOException("Chunk tidak lengkap: ditemukan ${all.size} dari ${idx.caption.chunkTotal} bagian.")
            }
            all
        }
        val expected = parts.sumOf { it.size }
        val tid = UUID.randomUUID().toString()
        transferStart(tid, idx.name, TdTransferDirection.DOWNLOAD, expected)
        val report = reporter(tid, onProgress)
        var done = 0L
        var copied = 0L
        try {
            for (part in parts) {
                val base = done
                val local = fetchFile(part.fileId) { bytes -> report((base + bytes).coerceAtMost(expected), expected) }
                File(local).inputStream().use { input -> copied += copyStream(input, output) }
                runCatching { TdClient.call(TdApi.DeleteFile(part.fileId)) } // bebaskan cache TDLib; pesan tetap ada
                done += part.size
            }
            output.flush()
            report(expected, expected)
            transferEnd(tid, null)
        } catch (e: Throwable) {
            transferEnd(tid, e.message ?: "Unduhan gagal")
            throw e
        }
        activity.record("download_requested", messageId, idx.name, JSONObject().put("byte_size", copied))
        return copied
    }

    private fun fetchFile(fileId: Int, onBytes: (Long) -> Unit): String {
        downloadProgress[fileId] = onBytes
        try {
            val file = TdClient.call(TdApi.DownloadFile(fileId, 32, 0L, 0L, true), 6L * 3600L)
            if (!file.local.isDownloadingCompleted || file.local.path.isNullOrEmpty()) {
                throw IOException("Unduhan Telegram belum selesai.")
            }
            return file.local.path
        } catch (e: InterruptedException) {
            runCatching { TdClient.send(TdApi.CancelDownloadFile(fileId, false)) }
            throw e
        } finally {
            downloadProgress.remove(fileId)
        }
    }

    // ------------------------------------------------------------ sampah & hapus

    private fun requireEntry(id: Long): TdEntry =
        entries(false).byId[id] ?: entries(true).byId[id] ?: throw IOException("File tidak ditemukan di chat penyimpanan.")

    private fun partsOf(head: TdEntry): List<TdEntry> {
        val group = head.caption.chunkGroup ?: return listOf(head)
        return entries(false).parts[group].orEmpty().ifEmpty { listOf(head) }
    }

    private fun rewrite(head: TdEntry, transform: (TdCaption) -> TdCaption) {
        val chat = chatId()
        for (part in partsOf(head)) {
            val updated = transform(part.caption.copy(fileName = part.caption.fileName ?: head.name)).render()
            try {
                TdClient.call(TdApi.EditMessageCaption(chat, part.messageId, null, TdApi.FormattedText(updated, emptyArray<TdApi.TextEntity>()), false))
            } catch (e: TdException) {
                if (!e.message.orEmpty().contains("not modified", ignoreCase = true)) throw e
            }
        }
        invalidate()
    }

    fun softDelete(id: Long): String {
        val head = requireEntry(id)
        val now = System.currentTimeMillis() / 1000L
        rewrite(head) { it.copy(trashedAt = now) }
        activity.record("delete", id, head.name, JSONObject())
        return "${head.name} dipindahkan ke Sampah."
    }

    fun restore(id: Long): String {
        val head = requireEntry(id)
        rewrite(head) { it.copy(trashedAt = 0L) }
        activity.record("restore", id, head.name, JSONObject())
        return "${head.name} dipulihkan."
    }

    /** Mengganti nama tampilan file (disimpan di caption; nama dokumen asli di Telegram tidak berubah). */
    fun renameFile(id: Long, newName: String): String {
        val clean = newName.trim().replace('/', '_').replace('\\', '_')
        require(clean.isNotEmpty() && clean.length <= 200) { "Nama file tidak valid (maksimal 200 karakter)." }
        val head = requireEntry(id)
        rewrite(head) { it.copy(fileName = clean) }
        activity.record("rename", id, clean, JSONObject().put("old_name", head.name))
        return "${head.name} diganti nama menjadi $clean."
    }

    /** Menghapus permanen dari Telegram (tidak dapat dipulihkan). */
    fun purge(id: Long): String {
        val head = requireEntry(id)
        val ids = partsOf(head).map { it.messageId }.toLongArray()
        TdClient.call(TdApi.DeleteMessages(chatId(), ids, true))
        activity.record("delete_permanent", id, head.name, JSONObject())
        invalidate()
        return "${head.name} dihapus permanen dari Telegram."
    }

    // ------------------------------------------------------------ folder

    fun emptyFolders(): List<String> = (prefs.getStringSet(KEY_FOLDERS, emptySet()) ?: emptySet()).toList().sorted()

    fun createFolder(path: String): String {
        val clean = normalizeFolder(path)
        require(clean.isNotEmpty()) { "Nama folder tidak boleh kosong." }
        prefs.edit().putStringSet(KEY_FOLDERS, (emptyFolders() + clean).toSet()).apply()
        return "Folder $clean dibuat. Folder kosong hanya tersimpan di perangkat ini sampai berisi file."
    }

    fun moveFile(id: Long, folder: String): String {
        val target = normalizeFolder(folder)
        val head = requireEntry(id)
        rewrite(head) { it.copy(folder = target) }
        return "${head.name} dipindahkan ke ${target.ifEmpty { "akar" }}."
    }

    fun renameFolder(from: String, to: String): String {
        val old = normalizeFolder(from)
        val new = normalizeFolder(to)
        require(old.isNotEmpty() && new.isNotEmpty()) { "Nama folder tidak valid." }
        require(old != new) { "Nama folder baru sama dengan nama lama." }
        require(!new.startsWith("$old/")) { "Folder tidak dapat dipindahkan ke dalam dirinya sendiri." }
        val affected = entries(true).heads.filter { it.folder == old || it.folder.startsWith("$old/") }
        for (head in affected) {
            val rest = head.folder.removePrefix(old)
            rewrite(head) { it.copy(folder = new + rest) }
        }
        val renamed = emptyFolders().map { if (it == old || it.startsWith("$old/")) new + it.removePrefix(old) else it }
        prefs.edit().putStringSet(KEY_FOLDERS, renamed.toSet()).apply()
        return "Folder diganti nama ($old → $new), ${affected.size} file diperbarui."
    }

    fun deleteFolder(path: String): String {
        val clean = normalizeFolder(path)
        val inUse = entries(true).heads.any { it.folder == clean || it.folder.startsWith("$clean/") }
        require(!inUse) { "Folder masih berisi file (termasuk di Sampah). Pindahkan atau hapus file terlebih dahulu." }
        prefs.edit().putStringSet(KEY_FOLDERS, emptyFolders().filterNot { it == clean || it.startsWith("$clean/") }.toSet()).apply()
        return "Folder $clean dihapus."
    }

    // ------------------------------------------------------------ aktivitas

    fun listActivity(action: String, limit: Int): List<ActivityLogItem> = activity.list(action, limit)
    fun exportActivityCsv(since: String, until: String, action: String): ByteArray = activity.exportCsv(since, until, action)

    // ------------------------------------------------------------ util

    private fun defaultFolder(): String {
        val pattern = LocalFileDatabase(context).getSetting("archive_pattern", "yyyy/MM/dd")
        if (pattern == "flat") return ""
        return runCatching { wibFormat(pattern).format(java.util.Date()) }.getOrDefault("")
    }

    private fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun copyExactly(input: InputStream, target: File, bytes: Long) {
        target.outputStream().buffered().use { out ->
            val buffer = ByteArray(256 * 1024)
            var left = bytes
            while (left > 0) {
                val n = input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                if (n < 0) throw IOException("Akhir file tidak terduga saat memecah chunk.")
                out.write(buffer, 0, n)
                left -= n
            }
        }
    }

    private fun copyStream(input: InputStream, output: OutputStream): Long {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            output.write(buffer, 0, n)
            total += n
        }
        return total
    }

    companion object {
        /** Batas aman di bawah 2000 MiB (batas unggah akun non-Premium). */
        const val CHUNK_BYTES = 1900L * 1024L * 1024L
        private const val KEY_CHAT = "storage_chat_id"
        private const val KEY_OWNER = "owner_user_id"
        private const val KEY_FOLDERS = "empty_folders"
        private val DOCUMENT_MIMES = setOf(
            "application/pdf", "application/msword", "application/vnd.ms-excel", "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        )

        @Volatile private var instance: TdCloud? = null
        fun get(context: Context): TdCloud = instance ?: synchronized(this) {
            instance ?: TdCloud(context.applicationContext).also { instance = it }
        }

        internal fun wibFormat(pattern: String): SimpleDateFormat =
            SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("Asia/Jakarta") }

        internal fun wibTime(epochSeconds: Long): String =
            wibFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(epochSeconds * 1000L))

        internal fun dateFolder(epochSeconds: Int): String =
            wibFormat("yyyy/MM/dd/HH").format(java.util.Date(epochSeconds * 1000L))

        /** Merapikan jalur folder: pemisah "/", tanpa segmen kosong/"."/"..", maksimal 8 level. */
        fun normalizeFolder(raw: String): String {
            val segments = raw.replace('\\', '/').split('/').map { it.trim() }.filter { it.isNotEmpty() }
            require(segments.size <= 8) { "Folder terlalu dalam (maksimal 8 level)." }
            for (s in segments) {
                require(s != "." && s != "..") { "Nama folder tidak valid." }
                require(s.none { it.isISOControl() }) { "Nama folder mengandung karakter kontrol." }
            }
            val joined = segments.joinToString("/")
            require(joined.length <= 150) { "Jalur folder terlalu panjang (maksimal 150 karakter)." }
            return joined
        }
    }
}

/** Log aktivitas lokal (tidak ada server): disimpan di SharedPreferences, maksimal 1000 entri terbaru. */
internal class TdActivityLog(context: Context) {
    private val prefs = context.getSharedPreferences("nexora_tdlib_activity", Context.MODE_PRIVATE)

    @Synchronized fun record(action: String, fileId: Long, fileName: String, details: JSONObject) {
        val current = load()
        val next = (prefs.getLong("next_id", 1L))
        val entry = JSONObject().put("id", next).put("action", action).put("file_id", fileId)
            .put("file_name", fileName).put("details", details).put("created_at", Instant.now().toString())
        val trimmed = JSONArray()
        trimmed.put(entry)
        for (i in 0 until minOf(current.length(), 999)) trimmed.put(current.get(i))
        prefs.edit().putString("log", trimmed.toString()).putLong("next_id", next + 1).apply()
    }

    @Synchronized fun clear() { prefs.edit().clear().apply() }

    private fun load(): JSONArray = runCatching { JSONArray(prefs.getString("log", "[]")) }.getOrDefault(JSONArray())

    @Synchronized fun list(action: String, limit: Int): List<ActivityLogItem> {
        val array = load()
        val result = ArrayList<ActivityLogItem>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            if (action != "all" && o.optString("action") != action) continue
            result.add(
                ActivityLogItem(o.optLong("id"), o.optString("action"), o.optLong("file_id"), o.optString("file_name", "Berkas"),
                    o.optString("created_at"), o.optJSONObject("details")?.toString().orEmpty())
            )
            if (result.size >= limit.coerceIn(1, 200)) break
        }
        return result
    }

    @Synchronized fun exportCsv(since: String, until: String, action: String): ByteArray {
        val from = parseInstant(since, false)
        val to = parseInstant(until, true)
        val array = load()
        fun cell(value: Any?) = "\"" + (value?.toString() ?: "").replace("\"", "\"\"") + "\""
        val lines = ArrayList<String>()
        lines.add(listOf("id", "action", "file_id", "file_name", "details", "created_at").joinToString(",") { cell(it) })
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            if (action != "all" && o.optString("action") != action) continue
            val at = runCatching { Instant.parse(o.optString("created_at")) }.getOrNull()
            if (at != null && ((from != null && at.isBefore(from)) || (to != null && !at.isBefore(to)))) continue
            lines.add(listOf(o.opt("id"), o.opt("action"), o.opt("file_id"), o.opt("file_name"), o.optJSONObject("details") ?: "{}", o.opt("created_at"))
                .joinToString(",") { cell(it) })
        }
        return ("\uFEFF" + lines.joinToString("\r\n")).toByteArray(Charsets.UTF_8)
    }

    private fun parseInstant(value: String, endOfDay: Boolean): Instant? {
        if (value.isBlank()) return null
        return runCatching { Instant.parse(value) }.getOrNull() ?: runCatching {
            val day = LocalDate.parse(value.trim().take(10))
            (if (endOfDay) day.plusDays(1) else day).atStartOfDay(ZoneId.of("Asia/Jakarta")).toInstant()
        }.getOrNull()
    }
}
