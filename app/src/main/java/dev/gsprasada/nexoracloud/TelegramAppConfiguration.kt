package dev.gsprasada.nexoracloud

import android.content.Context

/** Profil konfigurasi aplikasi Telegram. API hash disimpan terenkripsi melalui Android Keystore. */
data class TelegramAppConfiguration(
    val appTitle: String,
    val shortName: String,
    val platform: String,
    val appUrl: String,
    val apiId: String,
    val apiHash: String,
    val testDc2: String,
    val productionDc2: String
) {
    companion object {
        const val PREFS = "nexora_telegram_app_config"
        val FACTORY = TelegramAppConfiguration(
            appTitle = "NEXORA CLOUD",
            shortName = "nexoracloud",
            platform = "Android",
            appUrl = "https://nexoracloud.com",
            apiId = "31474812",
            apiHash = "f06187d4e65ffd9f4ac8a6fba1075d6d",
            testDc2 = "149.154.167.40:443",
            productionDc2 = "149.154.167.50:443"
        )

        fun load(context: Context): TelegramAppConfiguration {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val secret = SecretStore(context)
            return TelegramAppConfiguration(
                p.getString("app_title", FACTORY.appTitle) ?: FACTORY.appTitle,
                p.getString("short_name", FACTORY.shortName) ?: FACTORY.shortName,
                p.getString("platform", FACTORY.platform) ?: FACTORY.platform,
                p.getString("app_url", FACTORY.appUrl) ?: FACTORY.appUrl,
                p.getString("api_id", FACTORY.apiId) ?: FACTORY.apiId,
                secret.get("telegram_app_api_hash").ifBlank { FACTORY.apiHash },
                p.getString("test_dc2", FACTORY.testDc2) ?: FACTORY.testDc2,
                p.getString("production_dc2", FACTORY.productionDc2) ?: FACTORY.productionDc2
            )
        }

        fun save(context: Context, value: TelegramAppConfiguration) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("app_title", value.appTitle.trim())
                .putString("short_name", value.shortName.trim())
                .putString("platform", value.platform.trim())
                .putString("app_url", value.appUrl.trim())
                .putString("api_id", value.apiId.trim())
                .putString("test_dc2", value.testDc2.trim())
                .putString("production_dc2", value.productionDc2.trim())
                .apply()
            SecretStore(context).put("telegram_app_api_hash", value.apiHash.trim())
        }

        fun reset(context: Context) {
            save(context, FACTORY)
        }
    }
}
