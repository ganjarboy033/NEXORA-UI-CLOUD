package dev.gsprasada.nexoracloud

import android.content.Context
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.OutputStream
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** HTTP client Nexora. API key hanya disimpan di preferensi privat perangkat; token bot tetap di backend. */
class BackendClient(private val context: Context) {
    private val prefs = context.getSharedPreferences("nexora_backend", Context.MODE_PRIVATE)
    private val secrets = SecretStore(context)
    fun baseUrl(): String = prefs.getString("base_url", "")!!.trim().trimEnd('/')
    fun apiKey(): String = secrets.get("backend_api_key").ifBlank {
        // Migrasi satu kali dari versi lama ke Android Keystore.
        prefs.getString("api_key", "").orEmpty().also { old ->
            if (old.isNotBlank()) { secrets.put("backend_api_key", old); prefs.edit().remove("api_key").apply() }
        }
    }
    fun save(baseUrl: String, apiKey: String) {
        prefs.edit().putString("base_url", baseUrl.trim().trimEnd('/')).apply()
        secrets.put("backend_api_key", apiKey.trim())
    }
    private fun connection(path: String, authenticated: Boolean): HttpURLConnection {
        val base = baseUrl()
        require(base.startsWith("https://") || base.startsWith("http://10.0.2.2") || base.startsWith("http://localhost")) {
            "Gunakan URL HTTPS untuk server publik. HTTP hanya diizinkan untuk pengujian lokal."
        }
        return (URL(base + path).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 60_000; requestMethod = "GET"
            if (authenticated) setRequestProperty("Authorization", "Bearer ${apiKey()}")
        }
    }
    fun startPairing(deviceName: String = android.os.Build.MODEL): PairingSession {
        val c = connection("/api/v1/pairing/start", false).apply { requestMethod = "POST"; doOutput = true; setRequestProperty("Content-Type", "application/json") }
        return try {
            c.outputStream.bufferedWriter().use { it.write(JSONObject().put("device_name", deviceName).toString()) }
            val code = c.responseCode; val body = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("Pairing gagal (HTTP $code): $body")
            val json = JSONObject(body); PairingSession(json.getString("pair_id"), json.getString("pair_secret"), json.getString("qr_payload"), json.getString("expires_at"))
        } finally { c.disconnect() }
    }
    fun pollPairing(session: PairingSession): String? {
        val c = connection("/api/v1/pairing/${session.id}/poll", false).apply { requestMethod = "POST"; doOutput = true; setRequestProperty("Content-Type", "application/json") }
        return try {
            c.outputStream.bufferedWriter().use { it.write(JSONObject().put("pair_secret", session.secret).toString()) }
            val code = c.responseCode; val body = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("Pemeriksaan pairing gagal (HTTP $code): $body")
            JSONObject(body).optString("device_token").takeIf { it.isNotBlank() }
        } finally { c.disconnect() }
    }

    fun testConnection(): String {
        val c = connection("/health", false)
        return try { val code = c.responseCode; val body = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty();
            if (code !in 200..299) error("Backend merespons HTTP $code") else "Backend terhubung: $body" } finally { c.disconnect() }
    }
    fun telegramAccountAuthStatus(): TelegramAccountAuthStatus {
        val c = connection("/api/v1/telegram/account-auth/status", true)
        return try {
            val code = c.responseCode
            val body = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("Status login Telegram gagal (HTTP $code): $body")
            val json = JSONObject(body)
            TelegramAccountAuthStatus(json.optBoolean("configured"), json.optBoolean("ready"), json.optString("message"))
        } finally { c.disconnect() }
    }

    fun telegramStatus(): String {
        val c = connection("/api/v1/telegram/status", true)
        return try { val code = c.responseCode; val body = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty();
            if (code !in 200..299) error("Pemeriksaan Telegram gagal (HTTP $code): $body") else body } finally { c.disconnect() }
    }
    fun listCloudFiles(limit: Int = 100, offset: Int = 0, query: String = "", type: String = "all", sort: String = "date", direction: String = "desc"): List<CloudFileItem> {
        val params = "limit=${limit.coerceIn(1, 100)}&offset=${offset.coerceAtLeast(0)}" +
            "&q=${java.net.URLEncoder.encode(query, "UTF-8")}" +
            "&type=${java.net.URLEncoder.encode(type, "UTF-8")}" +
            "&sort=${java.net.URLEncoder.encode(sort, "UTF-8")}" +
            "&direction=${java.net.URLEncoder.encode(direction, "UTF-8")}" 
        val c = connection("/api/v1/files?$params", true)
        return try {
            val code = c.responseCode
            val body = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("Daftar cloud gagal (HTTP $code): $body")
            val array = JSONObject(body).optJSONArray("files") ?: org.json.JSONArray()
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                CloudFileItem(item.optLong("id"), item.optString("original_name", "Berkas cloud"),
                    item.optString("mime_type", "application/octet-stream"), item.optLong("byte_size"), item.optString("created_at"), item.optString("archive_path"), item.optString("sha256"))
            }
        } finally { c.disconnect() }
    }


    fun listActivity(action: String = "all", limit: Int = 100): List<ActivityLogItem> {
        val safeAction = action.takeIf { it in setOf("all", "upload", "delete", "restore", "download_requested") } ?: "all"
        val c = connection("/api/v1/activity?action=$safeAction&limit=${limit.coerceIn(1, 200)}", true)
        return try {
            val code = c.responseCode
            val body = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("Riwayat aktivitas gagal dimuat (HTTP $code): $body")
            val array = JSONObject(body).optJSONArray("activities") ?: org.json.JSONArray()
            (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let { item ->
                ActivityLogItem(item.optLong("id"), item.optString("action"), item.optLong("file_id"),
                    item.optString("file_name", "Berkas"), item.optString("created_at"), item.optJSONObject("details")?.toString().orEmpty())
            } }
        } finally { c.disconnect() }
    }

    fun storageBreakdown(): StorageBreakdown {
        val c = connection("/api/v1/stats/storage", true)
        return try {
            val code = c.responseCode
            val body = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("Statistik kapasitas gagal (HTTP $code): $body")
            val root = JSONObject(body)
            fun parse(key: String): List<StoragePeriod> {
                val array = root.optJSONArray(key) ?: org.json.JSONArray()
                return (0 until array.length()).mapNotNull { index -> array.optJSONObject(index)?.let { row ->
                    StoragePeriod(row.optString("period"), row.optLong("total_files"), row.optLong("total_bytes"))
                } }
            }
            StorageBreakdown(root.optString("timezone", "Asia/Jakarta"), parse("monthly"), parse("yearly"))
        } finally { c.disconnect() }
    }

    fun exportActivityCsv(sinceIso: String = "", untilIso: String = "", action: String = "all"): ByteArray {
        val allowed = setOf("all", "upload", "delete", "restore", "download_requested")
        val safeAction = action.takeIf { it in allowed } ?: "all"
        val params = buildList {
            add("action=$safeAction")
            if (sinceIso.isNotBlank()) add("since=${java.net.URLEncoder.encode(sinceIso, "UTF-8")}")
            if (untilIso.isNotBlank()) add("until=${java.net.URLEncoder.encode(untilIso, "UTF-8")}")
        }.joinToString("&")
        val c = connection("/api/v1/activity/export.csv?$params", true)
        return try {
            val code = c.responseCode
            if (code !in 200..299) error("Ekspor CSV gagal (HTTP $code): ${c.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()}")
            c.inputStream.use { it.readBytes() }
        } finally { c.disconnect() }
    }

    fun listTrash(): List<TrashFileItem> {
        val c = connection("/api/v1/trash", true)
        return try {
            val code = c.responseCode
            val body = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("Arsip terhapus gagal dimuat (HTTP $code): $body")
            val array = JSONObject(body).optJSONArray("files") ?: org.json.JSONArray()
            (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let { item ->
                TrashFileItem(item.optLong("id"), item.optString("original_name"), item.optString("mime_type"), item.optLong("byte_size"), item.optString("deleted_at"))
            } }
        } finally { c.disconnect() }
    }

    fun restoreTrashFile(id: Long): String = mutate("/api/v1/files/$id/restore", "POST")
    fun softDeleteCloudFile(id: Long): String = mutate("/api/v1/files/$id", "DELETE")
    private fun mutate(path: String, method: String): String {
        val c = connection(path, true).apply { requestMethod = method; connectTimeout = 15000; readTimeout = 30000 }
        return try {
            val code = c.responseCode
            val body = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("Permintaan gagal (HTTP $code): $body") else body
        } finally { c.disconnect() }
    }

    fun cloudStats(): CloudCatalogStats {
        val c = connection("/api/v1/stats", true)
        return try {
            val code = c.responseCode
            val body = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("Statistik cloud gagal (HTTP $code): $body")
            val obj = JSONObject(body)
            CloudCatalogStats(obj.optLong("total_files"), obj.optLong("total_bytes"), obj.optLong("images"),
                obj.optLong("videos"), obj.optLong("audios"), obj.optLong("documents"), obj.optString("latest_upload").takeIf { it.isNotBlank() && it != "null" })
        } finally { c.disconnect() }
    }

    fun downloadCloudFile(id: Long, output: OutputStream, onProgress: (Long, Long) -> Unit = { _, _ -> }): Long {
        require(id > 0) { "ID file cloud tidak valid." }
        val c = connection("/api/v1/files/$id/download", true).apply { readTimeout = 120_000 }
        return try {
            val code = c.responseCode
            if (code !in 200..299) {
                val body = c.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                error("Unduhan cloud gagal (HTTP $code): $body")
            }
            var total = 0L
            val expected = c.getHeaderFieldLong("Content-Length", -1L).coerceAtLeast(0L)
            c.inputStream.use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer); if (count < 0) break
                    output.write(buffer, 0, count); total += count
                    onProgress(total, expected)
                }
            }
            output.flush(); total
        } finally { c.disconnect() }
    }

    fun upload(file: File, onProgress: (Long, Long) -> Unit = { _, _ -> }): String {
        require(file.isFile && file.canRead()) { "File lokal tidak dapat dibaca." }
        val boundary = "NexoraBoundary${System.currentTimeMillis()}"
        val c = connection("/api/v1/files", true).apply { requestMethod = "POST"; doOutput = true; setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary"); setChunkedStreamingMode(64 * 1024) }
        return try {
            DataOutputStream(BufferedOutputStream(c.outputStream)).use { out ->
                out.writeBytes("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"${file.name.replace(Regex("[\\\"\\r\\n]"), "_")}\"\r\nContent-Type: application/octet-stream\r\n\r\n")
                var sent = 0L
                BufferedInputStream(file.inputStream()).use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer); if (count < 0) break
                        out.write(buffer, 0, count); sent += count
                        onProgress(sent, file.length())
                    }
                }
                out.writeBytes("\r\n--$boundary--\r\n"); out.flush()
            }
            val code = c.responseCode; val body = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("Upload gagal (HTTP $code): $body") else body
        } finally { c.disconnect() }
    }
}

/** Metadata file cloud yang dikembalikan backend. */
data class CloudFileItem(
    val id: Long,
    val name: String,
    val mimeType: String,
    val byteSize: Long,
    val createdAt: String,
    val archivePath: String = "",
    val sha256: String = ""
)

data class CloudCatalogStats(
    val totalFiles: Long, val totalBytes: Long, val images: Long,
    val videos: Long, val audios: Long, val documents: Long,
    val latestUpload: String?
)

data class TrashFileItem(val id: Long, val name: String, val mimeType: String, val byteSize: Long, val deletedAt: String)


data class ActivityLogItem(val id: Long, val action: String, val fileId: Long, val fileName: String, val createdAt: String, val details: String)


data class StoragePeriod(val period: String, val totalFiles: Long, val totalBytes: Long)
data class StorageBreakdown(val timezone: String, val monthly: List<StoragePeriod>, val yearly: List<StoragePeriod>)

/** Data sementara untuk satu proses pairing perangkat. */
data class PairingSession(val id: String, val secret: String, val qrPayload: String, val expiresAt: String)

/** Status kesiapan login akun Telegram melalui Telegram Client API. */
data class TelegramAccountAuthStatus(val configured: Boolean, val ready: Boolean, val message: String)
