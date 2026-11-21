package dev.gsprasada.nexoracloud

import android.content.Context
import android.os.Build
import android.util.Base64
import java.io.File
import java.io.IOException
import java.security.SecureRandom
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi

/** Error dari TDLib/Telegram. [code] mengikuti kode error Telegram (mis. 400, 401, 420). */
class TdException(val code: Int, message: String) : Exception(message)

/** State login Telegram yang dipetakan dari TdApi.AuthorizationState. */
sealed interface TdAuthState {
    data object Starting : TdAuthState
    data object MissingCredentials : TdAuthState
    data object WaitPhone : TdAuthState
    data class WaitCode(val phone: String, val delivery: String, val timeoutSeconds: Int) : TdAuthState
    data class WaitPassword(val hint: String) : TdAuthState
    data class WaitRegistration(val terms: String) : TdAuthState
    data class WaitQr(val link: String) : TdAuthState
    data object WaitEmailAddress : TdAuthState
    data object WaitEmailCode : TdAuthState
    data class Ready(val displayName: String) : TdAuthState
    data object LoggingOut : TdAuthState
    data class Failed(val message: String) : TdAuthState
}

/** Kredensial aplikasi Telegram (my.telegram.org/apps): BuildConfig dahulu, lalu konfigurasi di perangkat. */
internal data class TdCredentials(val apiId: Int, val apiHash: String) {
    val valid: Boolean get() = apiId > 0 && apiHash.length == 32

    companion object {
        fun resolve(context: Context): TdCredentials {
            if (BuildConfig.TELEGRAM_API_ID > 0 && BuildConfig.TELEGRAM_API_HASH.length == 32) {
                return TdCredentials(BuildConfig.TELEGRAM_API_ID, BuildConfig.TELEGRAM_API_HASH)
            }
            val config = TelegramAppConfiguration.load(context)
            return TdCredentials(config.apiId.trim().toIntOrNull() ?: 0, config.apiHash.trim())
        }
    }
}

/**
 * Pembungkus tunggal TDLib untuk seluruh proses aplikasi.
 * Mengelola siklus hidup Client, state login, dan menyediakan pemanggilan suspend maupun blocking.
 */
object TdClient {
    private val mutableState = MutableStateFlow<TdAuthState>(TdAuthState.Starting)
    val state: StateFlow<TdAuthState> = mutableState

    private val lock = Any()
    @Volatile private var client: Client? = null
    @Volatile private var appContext: Context? = null
    @Volatile private var needParameters = false
    @Volatile private var wipeOnClose = false
    @Volatile private var restartOnClose = true
    private val parametersSent = AtomicBoolean(false)
    private val listeners = CopyOnWriteArrayList<(TdApi.Object) -> Unit>()

    val isReady: Boolean get() = mutableState.value is TdAuthState.Ready

    fun addUpdateListener(listener: (TdApi.Object) -> Unit) { listeners.add(listener) }
    fun removeUpdateListener(listener: (TdApi.Object) -> Unit) { listeners.remove(listener) }

    /** True bila basis data TDLib dari login sebelumnya masih ada di perangkat. */
    fun hasSavedSession(context: Context): Boolean {
        val db = File(File(context.filesDir, "tdlib"), "db")
        return db.isDirectory && (db.list()?.isNotEmpty() == true)
    }

    /** Memulai TDLib bila belum berjalan. Aman dipanggil berulang kali. */
    fun start(context: Context) {
        synchronized(lock) {
            appContext = context.applicationContext
            if (client != null) return
            mutableState.value = TdAuthState.Starting
            needParameters = false
            parametersSent.set(false)
            restartOnClose = true
            client = Client.create(Client.ResultHandler { onUpdate(it) }, null, null)
            client?.send(
                TdApi.SetLogVerbosityLevel().apply { newVerbosityLevel = 1 },
                Client.ResultHandler { }
            )
        }
        // Update WaitTdlibParameters dapat tiba sebelum field client terisi.
        trySendParameters()
    }

    /** Menunggu sampai state tidak lagi [TdAuthState.Starting]; mengembalikan state terakhir. */
    fun awaitSettled(timeoutMillis: Long): TdAuthState {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (mutableState.value is TdAuthState.Starting && System.currentTimeMillis() < deadline) {
            Thread.sleep(100)
        }
        return mutableState.value
    }

    private fun onUpdate(update: TdApi.Object) {
        if (update is TdApi.UpdateAuthorizationState) {
            handleAuthorization(update.authorizationState)
            return
        }
        for (listener in listeners) runCatching { listener(update) }
    }

    private fun handleAuthorization(authState: TdApi.AuthorizationState) {
        AppLog.info("TDLib auth: ${authState.javaClass.simpleName}")
        when (authState) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                needParameters = true
                trySendParameters()
            }
            is TdApi.AuthorizationStateWaitPhoneNumber -> mutableState.value = TdAuthState.WaitPhone
            is TdApi.AuthorizationStateWaitCode -> {
                val info = authState.codeInfo
                mutableState.value = TdAuthState.WaitCode(
                    phone = info?.phoneNumber.orEmpty(),
                    delivery = describeCodeType(info?.type?.javaClass?.simpleName.orEmpty()),
                    timeoutSeconds = info?.timeout ?: 0
                )
            }
            is TdApi.AuthorizationStateWaitOtherDeviceConfirmation -> mutableState.value = TdAuthState.WaitQr(authState.link)
            is TdApi.AuthorizationStateWaitPassword -> mutableState.value = TdAuthState.WaitPassword(authState.passwordHint.orEmpty())
            is TdApi.AuthorizationStateWaitRegistration -> mutableState.value =
                TdAuthState.WaitRegistration(authState.termsOfService?.text?.text.orEmpty())
            is TdApi.AuthorizationStateWaitEmailAddress -> mutableState.value = TdAuthState.WaitEmailAddress
            is TdApi.AuthorizationStateWaitEmailCode -> mutableState.value = TdAuthState.WaitEmailCode
            is TdApi.AuthorizationStateReady -> {
                mutableState.value = TdAuthState.Ready("")
                send(TdApi.GetMe()) { result ->
                    if (result is TdApi.User && mutableState.value is TdAuthState.Ready) {
                        val name = listOf(result.firstName, result.lastName).filter { !it.isNullOrBlank() }.joinToString(" ")
                        mutableState.value = TdAuthState.Ready(name.ifBlank { "Akun Telegram" })
                    }
                }
            }
            is TdApi.AuthorizationStateLoggingOut -> mutableState.value = TdAuthState.LoggingOut
            is TdApi.AuthorizationStateClosing -> Unit
            is TdApi.AuthorizationStateClosed -> onClosed()
            else -> mutableState.value = TdAuthState.Failed(
                "Telegram meminta langkah login yang belum didukung (${authState.javaClass.simpleName})."
            )
        }
    }

    private fun describeCodeType(typeName: String): String = when {
        typeName.contains("TelegramMessage") -> "aplikasi Telegram di perangkat Anda yang lain"
        typeName.contains("Sms") -> "SMS"
        typeName.contains("MissedCall") -> "panggilan tidak terjawab"
        typeName.contains("FlashCall") -> "panggilan singkat"
        typeName.contains("Call") -> "panggilan telepon"
        typeName.contains("Fragment") -> "Fragment"
        typeName.contains("Firebase") -> "verifikasi perangkat"
        else -> "Telegram"
    }

    private fun onClosed() {
        val ctx = appContext
        synchronized(lock) {
            client = null
            needParameters = false
            parametersSent.set(false)
        }
        if (wipeOnClose && ctx != null) {
            wipeOnClose = false
            File(ctx.filesDir, "tdlib").deleteRecursively()
        }
        if (restartOnClose && ctx != null) start(ctx)
    }

    private fun trySendParameters() {
        val activeClient = client ?: return
        val ctx = appContext ?: return
        if (!needParameters || !parametersSent.compareAndSet(false, true)) return
        val credentials = TdCredentials.resolve(ctx)
        if (!credentials.valid) {
            mutableState.value = TdAuthState.MissingCredentials
            return
        }
        val root = File(ctx.filesDir, "tdlib")
        // Mode Test Server (opsional, default mati): memakai direktori terpisah agar sesi produksi tidak tersentuh.
        val testDc = ctx.getSharedPreferences(TelegramAppConfiguration.PREFS, Context.MODE_PRIVATE).getBoolean("use_test_dc", false)
        val suffix = if (testDc) "_test" else ""
        val params = TdApi.SetTdlibParameters()
        params.useTestDc = testDc
        params.databaseDirectory = File(root, "db$suffix").apply { mkdirs() }.absolutePath
        params.filesDirectory = File(root, "files$suffix").apply { mkdirs() }.absolutePath
        params.databaseEncryptionKey = databaseKey(ctx)
        params.useFileDatabase = true
        params.useChatInfoDatabase = true
        params.useMessageDatabase = true
        params.useSecretChats = false
        params.apiId = credentials.apiId
        params.apiHash = credentials.apiHash
        params.systemLanguageCode = Locale.getDefault().language.ifBlank { "en" }
        params.deviceModel = Build.MODEL.ifBlank { "Android" }
        params.systemVersion = Build.VERSION.RELEASE.orEmpty()
        params.applicationVersion = BuildConfig.VERSION_NAME
        activeClient.send(params, Client.ResultHandler { result ->
            if (result is TdApi.Error) onParametersRejected(result)
        })
    }

    private fun onParametersRejected(error: TdApi.Error) {
        val wrongKey = error.code == 401 || error.message.contains("encryption", ignoreCase = true)
        if (wrongKey) {
            // Kunci basis data hilang/berubah (mis. Keystore direset). Sesi lokal tidak dapat dipulihkan.
            wipeOnClose = true
            client?.send(TdApi.Close(), Client.ResultHandler { })
        } else {
            mutableState.value = TdAuthState.Failed(error.message)
        }
    }

    private fun databaseKey(context: Context): ByteArray {
        val secrets = SecretStore(context)
        val saved = secrets.get("tdlib_db_key")
        if (saved.isNotBlank()) return Base64.decode(saved, Base64.NO_WRAP)
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        secrets.put("tdlib_db_key", Base64.encodeToString(key, Base64.NO_WRAP))
        return key
    }

    // ---------------------------------------------------------------- permintaan

    /** Kirim permintaan tanpa menunggu; hasil (termasuk TdApi.Error) diberikan ke [onResult]. */
    fun send(function: TdApi.Function<*>, onResult: (TdApi.Object) -> Unit = {}) {
        val active = client
        if (active == null) {
            onResult(TdApi.Error().apply { code = -1; message = "TDLib belum berjalan." })
            return
        }
        active.send(function, Client.ResultHandler { onResult(it) })
    }

    /** Panggilan suspend; melempar [TdException] untuk TdApi.Error. */
    @Suppress("UNCHECKED_CAST")
    suspend fun <T : TdApi.Object> request(function: TdApi.Function<T>): T = suspendCancellableCoroutine { cont ->
        val active = client
        if (active == null) {
            cont.resumeWithException(TdException(-1, "TDLib belum berjalan."))
            return@suspendCancellableCoroutine
        }
        active.send(function, Client.ResultHandler { result ->
            if (result is TdApi.Error) cont.resumeWithException(TdException(result.code, result.message))
            else cont.resume(result as T)
        })
    }

    /** Panggilan blocking untuk thread latar (IO/Worker). Jangan dipanggil dari thread utama. */
    @Suppress("UNCHECKED_CAST")
    fun <T : TdApi.Object> call(function: TdApi.Function<T>, timeoutSeconds: Long = 60): T {
        val active = client ?: throw TdException(-1, "TDLib belum berjalan.")
        val latch = CountDownLatch(1)
        val box = arrayOfNulls<TdApi.Object>(1)
        active.send(function, Client.ResultHandler { result -> box[0] = result; latch.countDown() })
        if (!latch.await(timeoutSeconds, TimeUnit.SECONDS)) throw IOException("TDLib tidak merespons dalam $timeoutSeconds detik.")
        val result = box[0] ?: throw IOException("TDLib tidak mengembalikan hasil.")
        if (result is TdApi.Error) throw TdException(result.code, result.message)
        return result as T
    }

    // ---------------------------------------------------------------- aksi login

    suspend fun submitPhone(phone: String) { request(TdApi.SetAuthenticationPhoneNumber(phone, null)) }
    suspend fun requestQr() { request(TdApi.RequestQrCodeAuthentication(LongArray(0))) }
    suspend fun submitCode(code: String) { request(TdApi.CheckAuthenticationCode(code)) }
    suspend fun resendCode() { request(TdApi.ResendAuthenticationCode(null)) }
    suspend fun submitPassword(password: String) { request(TdApi.CheckAuthenticationPassword(password)) }
    suspend fun submitRegistration(firstName: String, lastName: String) { request(TdApi.RegisterUser(firstName, lastName, false)) }
    suspend fun submitEmail(email: String) { request(TdApi.SetAuthenticationEmailAddress(email)) }
    suspend fun submitEmailCode(code: String) { request(TdApi.CheckAuthenticationEmailCode(TdApi.EmailAddressAuthenticationCode(code))) }

    /** Keluar dari akun. TDLib menghapus datanya sendiri, lalu klien baru dimulai untuk login berikutnya. */
    suspend fun logOut() {
        request(TdApi.LogOut())
        appContext?.let { TdCloud.get(it).onLoggedOut() }
    }

    /** Menutup lalu memulai ulang TDLib tanpa menghapus sesi (mis. untuk mencoba ulang setelah gangguan). */
    fun restart() {
        mutableState.value = TdAuthState.Starting
        val active = client
        if (active == null) appContext?.let { start(it) } else active.send(TdApi.Close(), Client.ResultHandler { })
    }

    /**
     * Membuang sesi lokal yang belum selesai login (mis. keluar dari mode QR atau salah memulai)
     * dan memulai ulang TDLib dari awal. Tidak menyentuh data di server Telegram.
     */
    fun resetLocalSession() {
        wipeOnClose = true
        mutableState.value = TdAuthState.Starting
        val active = client
        if (active == null) {
            appContext?.let { start(it) }
        } else {
            active.send(TdApi.Close(), Client.ResultHandler { })
        }
    }
}
