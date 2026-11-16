package tachiyomi.core.common.desktop

import java.io.File

/**
 * Well-known directories of the desktop app (see docs/kmp-desktop/PLAN.md, D7).
 *
 * - Windows: `%APPDATA%\Komikku`
 * - macOS: `~/Library/Application Support/Komikku`
 * - Linux and others: `$XDG_DATA_HOME/komikku`, falling back to `~/.local/share/komikku`
 *
 * The data dir can be overridden with the `komikku.dataDir` system property (portable installs,
 * tests, running several instances side by side).
 */
object DesktopDirs {

    const val DATA_DIR_PROPERTY = "komikku.dataDir"

    /** Root directory for everything the desktop app persists. Created on first access. */
    val dataDir: File by lazy {
        resolveDataDir(
            osName = System.getProperty("os.name").orEmpty(),
            userHome = System.getProperty("user.home").orEmpty(),
            override = System.getProperty(DATA_DIR_PROPERTY),
            env = System::getenv,
        ).ensureDir()
    }

    /** Directory holding one JSON file per preference store. */
    val preferencesDir: File by lazy { dataDir.resolve("preferences").ensureDir() }

    internal fun resolveDataDir(
        osName: String,
        userHome: String,
        override: String?,
        env: (String) -> String?,
    ): File {
        if (!override.isNullOrBlank()) return File(override)

        val os = osName.lowercase()
        return when {
            os.startsWith("windows") -> {
                val appData = env("APPDATA")?.takeIf { it.isNotBlank() }
                    ?: File(userHome, "AppData/Roaming").path
                File(appData, APP_NAME)
            }
            os.startsWith("mac") || os.contains("darwin") -> {
                File(userHome, "Library/Application Support/$APP_NAME")
            }
            else -> {
                // The XDG spec says relative paths must be ignored.
                val xdgDataHome = env("XDG_DATA_HOME")?.takeIf { File(it).isAbsolute }
                    ?: File(userHome, ".local/share").path
                File(xdgDataHome, APP_NAME.lowercase())
            }
        }
    }

    private fun File.ensureDir(): File = apply {
        if (!isDirectory && !mkdirs() && !isDirectory) {
            error("Could not create directory $absolutePath")
        }
    }

    private const val APP_NAME = "Komikku"
}
