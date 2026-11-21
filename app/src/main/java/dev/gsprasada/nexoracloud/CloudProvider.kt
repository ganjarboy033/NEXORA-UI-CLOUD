package dev.gsprasada.nexoracloud

/** Identitas provider penyimpanan yang dikenali Nexora. */
enum class CloudProviderId(val key: String, val displayName: String) {
    TELEGRAM("telegram", "Telegram Cloud"),
    GOOGLE_DRIVE("google_drive", "Google Drive"),
    ONEDRIVE("onedrive", "Microsoft OneDrive"),
    WEBDAV("webdav", "WebDAV"),
    S3("s3", "S3 Compatible")
}

/** Status integrasi dibedakan dari preferensi aktif/nonaktif pengguna. */
enum class ProviderIntegrationStatus {
    IMPLEMENTED,
    PLANNED,
    CONFIGURATION_REQUIRED,
    CONNECTED,
    ERROR
}

data class ProviderCapabilities(
    val listFiles: Boolean = false,
    val upload: Boolean = false,
    val download: Boolean = false,
    val delete: Boolean = false,
    val restore: Boolean = false,
    val serverSideSearch: Boolean = false
)

data class CloudProviderDescriptor(
    val id: CloudProviderId,
    val status: ProviderIntegrationStatus,
    val capabilities: ProviderCapabilities,
    val description: String
)

/**
 * Registry pusat untuk identitas dan kemampuan provider.
 * Registry ini tidak mengklaim provider PLANNED sudah dapat digunakan.
 */
object CloudProviderRegistry {
    val providers: List<CloudProviderDescriptor> = listOf(
        CloudProviderDescriptor(
            CloudProviderId.TELEGRAM,
            ProviderIntegrationStatus.IMPLEMENTED,
            ProviderCapabilities(
                listFiles = true, upload = true, download = true,
                delete = true, restore = true, serverSideSearch = true
            ),
            "Login akun Telegram langsung (TDLib) atau melalui backend Nexora. Token bot tetap berada di server."
        ),
        CloudProviderDescriptor(
            CloudProviderId.GOOGLE_DRIVE,
            ProviderIntegrationStatus.PLANNED,
            ProviderCapabilities(),
            "Konektor OAuth dan operasi file belum tersedia."
        ),
        CloudProviderDescriptor(
            CloudProviderId.ONEDRIVE,
            ProviderIntegrationStatus.PLANNED,
            ProviderCapabilities(),
            "Konektor Microsoft Graph dan OAuth belum tersedia."
        ),
        CloudProviderDescriptor(
            CloudProviderId.WEBDAV,
            ProviderIntegrationStatus.PLANNED,
            ProviderCapabilities(),
            "Konektor WebDAV dan pengelolaan kredensial belum tersedia."
        ),
        CloudProviderDescriptor(
            CloudProviderId.S3,
            ProviderIntegrationStatus.PLANNED,
            ProviderCapabilities(),
            "Konektor S3-compatible dan pengelolaan secret belum tersedia."
        )
    )

    fun find(id: CloudProviderId): CloudProviderDescriptor? = providers.firstOrNull { it.id == id }
}
