package eu.kanade.tachiyomi.core.security

import android.content.Context
import eu.kanade.tachiyomi.core.R
import tachiyomi.core.preference.PreferenceStore
import tachiyomi.core.preference.getEnum

class SecurityPreferences(
    private val preferenceStore: PreferenceStore,
    private val context: Context,
) {

    fun useAuthenticator() = preferenceStore.getBoolean("use_biometric_lock", false)

    fun lockAppAfter() = preferenceStore.getInt("lock_app_after", 0)

    fun secureScreen() = preferenceStore.getEnum("secure_screen_v2", SecureScreenMode.INCOGNITO)

    fun hideNotificationContent() = preferenceStore.getBoolean("hide_notification_content", false)

    // SY -->
    fun authenticatorTimeRanges() = this.preferenceStore.getStringSet("biometric_time_ranges", mutableSetOf())

    fun authenticatorDays() = this.preferenceStore.getInt("biometric_days", 0x7F)

    fun encryptDatabase() = this.preferenceStore.getBoolean("encrypt_database", !context.getDatabasePath("tachiyomi.db").exists())

    fun sqlPassword() = this.preferenceStore.getString("sql_password", "")

    fun passwordProtectDownloads() = preferenceStore.getBoolean("password_protect_downloads", false)

    fun encryptionType() = this.preferenceStore.getEnum("encryption_type", EncryptionType.AES_256)

    fun cbzPassword() = this.preferenceStore.getString("cbz_password", "")

    fun localCoverLocation() = this.preferenceStore.getEnum("local_cover_location", CoverCacheLocation.IN_MANGA_DIRECTORY)

    // SY <--

    /**
     * For app lock. Will be set when there is a pending timed lock.
     * Otherwise this pref should be deleted.
     */
    fun lastAppClosed() = preferenceStore.getLong("last_app_closed", 0)

    enum class SecureScreenMode(val titleResId: Int) {
        ALWAYS(R.string.lock_always),
        INCOGNITO(R.string.pref_incognito_mode),
        NEVER(R.string.lock_never),
    }

    // SY -->
    enum class EncryptionType(val titleResId: Int) {
        AES_256(R.string.aes_256),
        AES_192(R.string.aes_192),
        AES_128(R.string.aes_128),
        ZIP_STANDARD(R.string.standard_zip_encryption),
    }
    enum class CoverCacheLocation(val titleResId: Int) {
        IN_MANGA_DIRECTORY(R.string.save_in_manga_directory),
        INTERNAL(R.string.save_internally),
        NEVER(R.string.save_never),
    }

    // SY <--
}
