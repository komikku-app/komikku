package eu.kanade.tachiyomi

/**
 * KMK: desktop counterpart of the Android-generated `BuildConfig` for borrowed files (backup file names,
 * tracker user agents, about screen). [APPLICATION_ID] is the Android release id, so backups made on
 * desktop are named like Android's (`app.komikku_<date>.tachibk`). The version comes from the
 * `komikku.version` system property (set by packaging), else the jar manifest, else `"dev"`.
 */
object BuildConfig {
    const val APPLICATION_ID = "app.komikku"
    const val BUILD_TYPE = "desktop"
    const val DEBUG = false
    const val PREVIEW = false

    @JvmField
    val VERSION_NAME: String = System.getProperty("komikku.version")
        ?: BuildConfig::class.java.`package`?.implementationVersion
        ?: "dev"

    @JvmField
    val VERSION_CODE: Int = System.getProperty("komikku.versionCode")?.toIntOrNull() ?: 0
}
