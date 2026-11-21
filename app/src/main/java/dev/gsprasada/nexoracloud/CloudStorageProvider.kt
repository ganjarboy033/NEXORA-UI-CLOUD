package dev.gsprasada.nexoracloud

import android.content.Context
import java.io.File
import java.io.IOException
import java.io.OutputStream

/**
 * Kontrak operasi penyimpanan yang dipakai lapisan transfer Nexora.
 * Implementasi provider harus menerjemahkan hasil API masing-masing ke model Nexora.
 */
interface CloudStorageProvider {
    val id: CloudProviderId
    fun testConnection(): String
    fun listFiles(limit: Int = 100, offset: Int = 0, query: String = ""): List<CloudFileItem>
    fun upload(file: File, onProgress: (Long, Long) -> Unit = { _, _ -> }): String
    fun download(fileId: Long, output: OutputStream, onProgress: (Long, Long) -> Unit = { _, _ -> }): Long
    fun delete(fileId: Long): String
    fun restore(fileId: Long): String
}

/** Adaptasi implementasi Telegram yang sudah digunakan backend Nexora saat ini. */
class TelegramCloudProvider(private val backend: BackendClient) : CloudStorageProvider {
    override val id: CloudProviderId = CloudProviderId.TELEGRAM

    override fun testConnection(): String = backend.telegramStatus()

    override fun listFiles(limit: Int, offset: Int, query: String): List<CloudFileItem> =
        backend.listCloudFiles(limit = limit, offset = offset, query = query)

    override fun upload(file: File, onProgress: (Long, Long) -> Unit): String =
        backend.upload(file, onProgress)

    override fun download(fileId: Long, output: OutputStream, onProgress: (Long, Long) -> Unit): Long =
        backend.downloadCloudFile(fileId, output, onProgress)

    override fun delete(fileId: Long): String = backend.softDeleteCloudFile(fileId)

    override fun restore(fileId: Long): String = backend.restoreTrashFile(fileId)
}

/** Provider Telegram langsung lewat akun pengguna (TDLib): tanpa backend, mendukung file besar via chunk. */
class TdlibCloudProvider(private val cloud: TdCloud) : CloudStorageProvider {
    override val id: CloudProviderId = CloudProviderId.TELEGRAM

    override fun testConnection(): String = cloud.statusText()

    override fun listFiles(limit: Int, offset: Int, query: String): List<CloudFileItem> =
        cloud.list(limit = limit, offset = offset, query = query)

    override fun upload(file: File, onProgress: (Long, Long) -> Unit): String = cloud.upload(file, null, onProgress)

    /** Upload ke folder tertentu ([folder] null = ikuti pola arsip). */
    fun uploadTo(file: File, folder: String?, onProgress: (Long, Long) -> Unit): String = cloud.upload(file, folder, onProgress)

    override fun download(fileId: Long, output: OutputStream, onProgress: (Long, Long) -> Unit): Long =
        cloud.download(fileId, output, onProgress)

    override fun delete(fileId: Long): String = cloud.softDelete(fileId)

    override fun restore(fileId: Long): String = cloud.restore(fileId)
}

/**
 * Titik pembuatan provider. Provider yang belum memiliki konektor nyata sengaja
 * ditolak agar aplikasi tidak menampilkan operasi palsu atau mengirim kredensial.
 */
object CloudStorageProviderFactory {
    fun create(id: CloudProviderId, backend: BackendClient): CloudStorageProvider = when (id) {
        CloudProviderId.TELEGRAM -> TelegramCloudProvider(backend)
        else -> throw UnsupportedOperationException(
            "${CloudProviderRegistry.find(id)?.id?.displayName ?: id.displayName} belum memiliki konektor aktif."
        )
    }

    /**
     * Memilih provider Telegram terbaik: akun Telegram (TDLib) bila sudah login, selain itu backend Nexora.
     * Bila sesi tersimpan tetapi TDLib belum siap, melempar IOException agar WorkManager mencoba ulang.
     */
    fun createPreferred(context: Context, backend: BackendClient): CloudStorageProvider {
        val app = context.applicationContext
        if (TdClient.isReady || TdClient.hasSavedSession(app)) {
            TdClient.start(app)
            when (TdClient.awaitSettled(15_000)) {
                is TdAuthState.Ready -> return TdlibCloudProvider(TdCloud.get(app))
                is TdAuthState.Starting -> throw IOException("Akun Telegram belum siap; akan dicoba ulang.")
                else -> Unit
            }
        }
        return create(CloudProviderId.TELEGRAM, backend)
    }
}
