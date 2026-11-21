package dev.gsprasada.nexoracloud

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** State UI layar login di luar state autentikasi TDLib itu sendiri. */
data class LoginUiState(
    val busy: Boolean = false,
    val error: String = "",
    val notice: String = "",
    val storageTarget: TdStorageTarget? = null
)

/** ViewModel login Telegram: meneruskan aksi UI ke [TdClient] dan [TdCloud]. */
class TelegramAuthViewModel(application: Application) : AndroidViewModel(application) {
    /** State autentikasi TDLib (WaitPhone, WaitCode, WaitPassword, ..., Ready). */
    val authState: StateFlow<TdAuthState> = TdClient.state

    private val mutableUi = MutableStateFlow(LoginUiState())
    val ui: StateFlow<LoginUiState> = mutableUi

    private val cloud: TdCloud get() = TdCloud.get(getApplication())

    init { TdClient.start(application) }

    /** Kredensial yang sedang dipakai (BuildConfig lebih dulu, lalu konfigurasi perangkat). */
    fun currentApiId(): String = TdCredentials.resolve(getApplication()).apiId.takeIf { it > 0 }?.toString().orEmpty()
    fun currentApiHash(): String = TdCredentials.resolve(getApplication()).apiHash

    /**
     * Menyimpan API ID/Hash dari input pengguna lalu memulai ulang sesi login lokal.
     * Catatan: nilai TELEGRAM_API_ID/HASH yang diberikan saat build tetap diprioritaskan.
     */
    fun saveCredentials(apiId: String, apiHash: String) {
        val id = apiId.trim()
        val hash = apiHash.trim()
        if ((id.toIntOrNull() ?: 0) <= 0 || !hash.matches(Regex("[0-9a-fA-F]{32}"))) {
            mutableUi.update { it.copy(error = "API ID harus angka dan API Hash harus 32 karakter heksadesimal.") }
            return
        }
        val app = getApplication<Application>()
        TelegramAppConfiguration.save(app, TelegramAppConfiguration.load(app).copy(apiId = id, apiHash = hash))
        mutableUi.update { it.copy(error = "", notice = "Kredensial disimpan. Sesi login dimulai ulang.") }
        TdClient.resetLocalSession()
    }

    fun clearError() = mutableUi.update { it.copy(error = "") }

    private fun launchOp(block: suspend () -> Unit) {
        viewModelScope.launch {
            mutableUi.update { it.copy(busy = true, error = "") }
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableUi.update { it.copy(error = describeError(e)) }
            } finally {
                mutableUi.update { it.copy(busy = false) }
            }
        }
    }

    fun submitPhone(countryCode: String, nationalNumber: String) =
        launchOp { TdClient.submitPhone("+" + countryCode.filter(Char::isDigit) + nationalNumber.filter(Char::isDigit).trimStart('0')) }

    fun requestQr() = launchOp { TdClient.requestQr() }
    fun submitCode(code: String) = launchOp { TdClient.submitCode(code.trim()) }
    fun resendCode() = launchOp { TdClient.resendCode(); mutableUi.update { it.copy(notice = "Permintaan kode baru dikirim.") } }
    fun submitPassword(password: String) = launchOp { TdClient.submitPassword(password) }
    fun submitRegistration(first: String, last: String) = launchOp { TdClient.submitRegistration(first.trim(), last.trim()) }
    fun submitEmail(email: String) = launchOp { TdClient.submitEmail(email.trim()) }
    fun submitEmailCode(code: String) = launchOp { TdClient.submitEmailCode(code.trim()) }
    fun logOut() = launchOp { TdClient.logOut(); mutableUi.update { it.copy(storageTarget = null) } }
    fun leaveQr() = TdClient.resetLocalSession()
    fun retryStart() = TdClient.restart()

    /** Memuat chat penyimpanan aktif setelah AuthorizationStateReady. */
    fun loadStorageTarget() = launchOp {
        val target = withContext(Dispatchers.IO) { cloud.currentTarget() }
        mutableUi.update { it.copy(storageTarget = target) }
    }

    fun useSavedMessages() = changeTarget { cloud.useSavedMessages() }
    fun createChannel() = changeTarget { cloud.createPrivateChannel("Nexora Cloud") }
    fun useChat(reference: String) = changeTarget { cloud.useChat(reference) }

    private fun changeTarget(block: () -> TdStorageTarget) = launchOp {
        val target = withContext(Dispatchers.IO) { block() }
        mutableUi.update { it.copy(storageTarget = target, notice = "Chat penyimpanan: ${target.title}") }
    }

    private fun describeError(e: Throwable): String {
        val raw = e.message.orEmpty()
        return when {
            raw.contains("PHONE_NUMBER_INVALID") -> "Nomor telepon tidak valid. Periksa kode negara dan nomornya."
            raw.contains("PHONE_NUMBER_BANNED") -> "Nomor ini dibatasi oleh Telegram."
            raw.contains("PHONE_CODE_INVALID") || raw.contains("PHONE_CODE_EMPTY") -> "Kode salah. Periksa kembali."
            raw.contains("PHONE_CODE_EXPIRED") -> "Kode sudah kedaluwarsa. Minta kode baru."
            raw.contains("PASSWORD_HASH_INVALID") -> "Password verifikasi dua langkah salah."
            raw.contains("EMAIL_ADDRESS_INVALID") -> "Alamat email tidak valid."
            raw.contains("FLOOD_WAIT") || raw.contains("Too Many Requests") -> "Terlalu banyak percobaan. Tunggu sebentar lalu coba lagi. ($raw)"
            raw.isBlank() -> "Terjadi kesalahan tak dikenal."
            else -> raw
        }
    }
}
