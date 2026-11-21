package dev.gsprasada.nexoracloud

import android.content.Context
import java.io.OutputStream

/**
 * Satu pintu untuk semua operasi cloud yang dipakai UI.
 * Bila akun Telegram sudah login (TDLib) maka operasi berjalan langsung di Telegram; bila belum,
 * operasi jatuh ke backend Nexora seperti sebelumnya. Dipanggil dari thread latar (Dispatchers.IO).
 */
class CloudGateway(context: Context, private val backend: BackendClient) {
    private val appContext = context.applicationContext
    private val cloud: TdCloud get() = TdCloud.get(appContext)

    /** True bila akun Telegram (TDLib) aktif. Dapat menunggu singkat saat TDLib baru dimulai. */
    fun usingTelegramAccount(): Boolean {
        if (TdClient.isReady) return true
        if (!TdClient.hasSavedSession(appContext)) return false
        TdClient.start(appContext)
        return TdClient.awaitSettled(8_000) is TdAuthState.Ready
    }

    fun sourceLabel(): String = if (TdClient.isReady) "Akun Telegram (langsung, TDLib)" else "Backend Nexora"

    fun listCloudFiles(limit: Int = 100, offset: Int = 0, query: String = "", type: String = "all", sort: String = "date", direction: String = "desc"): List<CloudFileItem> =
        if (usingTelegramAccount()) cloud.list(limit, offset, query, type, sort, direction)
        else backend.listCloudFiles(limit, offset, query, type, sort, direction)

    fun cloudStats(): CloudCatalogStats = if (usingTelegramAccount()) cloud.stats() else backend.cloudStats()
    fun storageBreakdown(): StorageBreakdown = if (usingTelegramAccount()) cloud.storageBreakdown() else backend.storageBreakdown()

    fun listActivity(action: String = "all", limit: Int = 100): List<ActivityLogItem> =
        if (usingTelegramAccount()) cloud.listActivity(action, limit) else backend.listActivity(action, limit)

    fun exportActivityCsv(sinceIso: String = "", untilIso: String = "", action: String = "all"): ByteArray =
        if (usingTelegramAccount()) cloud.exportActivityCsv(sinceIso, untilIso, action) else backend.exportActivityCsv(sinceIso, untilIso, action)

    fun listTrash(): List<TrashFileItem> = if (usingTelegramAccount()) cloud.listTrash() else backend.listTrash()
    fun restoreTrashFile(id: Long): String = if (usingTelegramAccount()) cloud.restore(id) else backend.restoreTrashFile(id)
    fun softDeleteCloudFile(id: Long): String = if (usingTelegramAccount()) cloud.softDelete(id) else backend.softDeleteCloudFile(id)

    fun downloadCloudFile(id: Long, output: OutputStream, onProgress: (Long, Long) -> Unit = { _, _ -> }): Long =
        if (usingTelegramAccount()) cloud.download(id, output, onProgress) else backend.downloadCloudFile(id, output, onProgress)

    fun telegramStatus(): String = if (usingTelegramAccount()) cloud.statusText() else backend.telegramStatus()

    fun telegramAccountAuthStatus(): TelegramAccountAuthStatus {
        val credentials = TdCredentials.resolve(appContext)
        if (!credentials.valid) {
            return if (backend.baseUrl().isBlank()) {
                TelegramAccountAuthStatus(false, false, "API ID dan API Hash Telegram belum diisi.")
            } else {
                backend.telegramAccountAuthStatus()
            }
        }
        TdClient.start(appContext)
        return when (val state = TdClient.awaitSettled(5_000)) {
            is TdAuthState.Ready -> TelegramAccountAuthStatus(true, true, "Akun Telegram terhubung: ${state.displayName.ifBlank { "akun Anda" }}.")
            is TdAuthState.Failed -> TelegramAccountAuthStatus(true, false, state.message)
            else -> TelegramAccountAuthStatus(true, false, "Belum login. Ketuk Login Telegram untuk masuk.")
        }
    }

    // ---- Folder & penghapusan permanen: hanya tersedia pada mode akun Telegram ----

    fun supportsFolders(): Boolean = TdClient.isReady
    fun emptyFolders(): List<String> = if (TdClient.isReady) cloud.emptyFolders() else emptyList()
    fun createFolder(path: String): String = requireAccount().createFolder(path)
    fun moveCloudFile(id: Long, folder: String): String = requireAccount().moveFile(id, folder)
    fun renameFolder(from: String, to: String): String = requireAccount().renameFolder(from, to)
    fun deleteFolder(path: String): String = requireAccount().deleteFolder(path)
    fun purgeCloudFile(id: Long): String = requireAccount().purge(id)
    fun renameCloudFile(id: Long, newName: String): String = requireAccount().renameFile(id, newName)
    fun uploadFileToFolder(file: java.io.File, folder: String, onProgress: (Long, Long) -> Unit = { _, _ -> }): String =
        requireAccount().upload(file, folder, onProgress)

    private fun requireAccount(): TdCloud {
        check(usingTelegramAccount()) { "Fitur ini memerlukan login akun Telegram." }
        return cloud
    }
}
