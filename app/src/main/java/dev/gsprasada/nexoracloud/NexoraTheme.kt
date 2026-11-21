package dev.gsprasada.nexoracloud

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** Token warna desain Nexora v12 (terang/gelap) yang diturunkan dari `nexora-cloud-v12-ultimate.html`. */
@Immutable
data class NexoraColors(
    val isDark: Boolean,
    val bg: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val primary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val text: Color,
    val muted: Color,
    val border: Color,
    val danger: Color,
    val dangerBg: Color,
    val warning: Color,
    val warningBg: Color,
    val success: Color,
    val successBg: Color
)

/** Preset warna aksen: (primer, container terang, teks container terang). Sama dengan titik warna di desain. */
object NexoraAccents {
    val presets: List<Triple<String, String, String>> = listOf(
        Triple("#2787C7", "#E4F3FB", "#0F4C75"),
        Triple("#059669", "#D1FAE5", "#064E3B"),
        Triple("#7C3AED", "#EDE9FE", "#4C1D95"),
        Triple("#DB2777", "#FCE7F3", "#831843"),
        Triple("#D97706", "#FEF3C7", "#78350F")
    )
}

private fun parseColor(hex: String, fallback: Color): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(fallback)

fun buildNexoraColors(isDark: Boolean, accentHex: String): NexoraColors {
    val accent = parseColor(accentHex, Color(0xFF2787C7))
    val preset = NexoraAccents.presets.firstOrNull { it.first.equals(accentHex, ignoreCase = true) }
    return if (!isDark) {
        NexoraColors(
            isDark = false,
            bg = Color(0xFFF4F7FA), surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFE9EFF5),
            primary = accent,
            primaryContainer = preset?.let { parseColor(it.second, Color(0xFFE4F3FB)) } ?: lerp(Color.White, accent, 0.14f),
            onPrimaryContainer = preset?.let { parseColor(it.third, Color(0xFF0F4C75)) } ?: lerp(accent, Color.Black, 0.45f),
            text = Color(0xFF17212B), muted = Color(0xFF71808F), border = Color(0xFFDFE6ED),
            danger = Color(0xFFDC2626), dangerBg = Color(0xFFFEE2E2),
            warning = Color(0xFFD97706), warningBg = Color(0xFFFEF3C7),
            success = Color(0xFF059669), successBg = Color(0xFFD1FAE5)
        )
    } else {
        val surface = Color(0xFF1B2732)
        NexoraColors(
            isDark = true,
            bg = Color(0xFF101820), surface = surface, surfaceVariant = Color(0xFF243342),
            primary = lerp(accent, Color.White, 0.30f),
            primaryContainer = lerp(surface, accent, 0.30f),
            onPrimaryContainer = Color(0xFFE6ECEC),
            text = Color(0xFFE7EDF4), muted = Color(0xFFACB8C5), border = Color(0xFF2C3E50),
            danger = Color(0xFFFF8A80), dangerBg = Color(0xFF3B1818),
            warning = Color(0xFFFBBF24), warningBg = Color(0xFF3B2E13),
            success = Color(0xFF34D399), successBg = Color(0xFF12362A)
        )
    }
}

val LocalNexora = staticCompositionLocalOf { buildNexoraColors(false, "#2787C7") }

/** Log aplikasi ringan (di memori) untuk Panel Debug. Tidak pernah memuat rahasia (token, hash, kode OTP). */
object AppLog {
    private val state = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = state

    fun add(level: String, message: String) {
        val stamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        state.update { (it + "[$stamp] $level $message").takeLast(300) }
    }

    fun info(message: String) = add("INFO", message)
    fun ok(message: String) = add("OK", message)
    fun warn(message: String) = add("WARN", message)
    fun err(message: String) = add("ERR", message)
    fun clear() { state.value = emptyList() }
}
