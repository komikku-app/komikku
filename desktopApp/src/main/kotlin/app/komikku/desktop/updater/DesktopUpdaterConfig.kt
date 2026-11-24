package app.komikku.desktop.updater

import eu.kanade.tachiyomi.BuildConfig
import tachiyomi.data.release.GithubRelease

/**
 * KMK: how the desktop app checks for a newer Komikku version (P6), the counterpart of Android's `UPDATER_ENABLED` /
 * build type / `COMMIT_COUNT` build fields. Set by `desktopApp/build.gradle.kts` as system properties of the launcher
 * (`run` and the packaged app):
 *
 * - `komikku.updater=true` — only with the Gradle flag `-Penable-updater` (Android's flag; off by default, as on
 *   Android). Without it the app never contacts GitHub and hides "Check for updates".
 * - `komikku.preview=true` (Gradle `-Pdesktop-preview`) — the preview channel: releases of
 *   `komikku-app/komikku-preview` tagged `r<commit count>`, compared with `komikku.commitCount` (Android preview
 *   builds). Otherwise the stable channel: releases of `komikku-app/komikku` tagged `v<version name>`, compared with
 *   the app's own `komikku.version`.
 *
 * A release only counts when it carries an installer for this OS ([installerLink]); Android's APK-only releases are
 * skipped.
 */
data class DesktopUpdaterConfig(
    val enabled: Boolean,
    val isPreview: Boolean,
    /** The desktop app's own version (`BuildConfig.VERSION_NAME`, e.g. `1.13.6`). */
    val versionName: String,
    /** The commit count of a preview build (0 = unknown). */
    val commitCount: Int,
    val os: DesktopOs,
) {

    val repository: String
        get() = if (isPreview) PREVIEW_REPOSITORY else STABLE_REPOSITORY

    /**
     * Whether this build's version can be compared with a release tag at all: a development run without
     * `komikku.version` is `"dev"`, a preview build without a commit count has nothing to compare (Android always has
     * both). Such builds do not check.
     */
    val canCompare: Boolean
        get() = if (isPreview) commitCount > 0 else RELEASE_VERSION.matches(versionName)

    /** Whether update checks run: the build flag is on and the version is comparable. */
    val isActive: Boolean
        get() = enabled && canCompare

    /** The release page of this build's own tag (About screen), like Android's `RELEASE_URL`. */
    val currentReleaseUrl: String
        get() = "https://github.com/$repository/releases/tag/" + if (isPreview) "r$commitCount" else "v$versionName"

    /** The download link of the installer for [os] in [release], or `null` when the release has none. */
    fun installerLink(release: GithubRelease): String? = release.assets
        .firstOrNull { asset -> os.installerExtensions.any { asset.name.endsWith(it, ignoreCase = true) } }
        ?.downloadLink

    companion object {
        const val STABLE_REPOSITORY = "komikku-app/komikku"
        const val PREVIEW_REPOSITORY = "komikku-app/komikku-preview"

        /** `x.y.z` (what jpackage accepts; `GetApplicationRelease` compares the numbers part by part). */
        private val RELEASE_VERSION = Regex("""\d+(\.\d+)*""")

        fun fromSystemProperties(): DesktopUpdaterConfig = DesktopUpdaterConfig(
            enabled = System.getProperty("komikku.updater").toBoolean(),
            isPreview = System.getProperty("komikku.preview").toBoolean(),
            versionName = BuildConfig.VERSION_NAME,
            commitCount = System.getProperty("komikku.commitCount")?.toIntOrNull() ?: 0,
            os = DesktopOs.current(),
        )
    }
}

/** The OS families the desktop packages are built for, with the installer file types of `build_desktop.yml`. */
enum class DesktopOs(val installerExtensions: List<String>) {
    Windows(listOf(".msi", ".exe")),
    MacOs(listOf(".dmg")),
    Linux(listOf(".deb", ".rpm", ".AppImage")),
    ;

    companion object {
        fun current(osName: String = System.getProperty("os.name").orEmpty()): DesktopOs = when {
            osName.startsWith("Windows", ignoreCase = true) -> Windows
            osName.startsWith("Mac", ignoreCase = true) -> MacOs
            else -> Linux
        }
    }
}
