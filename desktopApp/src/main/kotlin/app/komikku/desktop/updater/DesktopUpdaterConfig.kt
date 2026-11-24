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
 * A release only counts when its tag can be compared with this build's version ([isComparableTag]) and it carries an
 * installer for this OS and CPU architecture ([installerLink]); Android's APK-only releases are skipped.
 */
data class DesktopUpdaterConfig(
    val enabled: Boolean,
    val isPreview: Boolean,
    /** The desktop app's own version (`BuildConfig.VERSION_NAME`, e.g. `1.13.6`). */
    val versionName: String,
    /** The commit count of a preview build (0 = unknown). */
    val commitCount: Int,
    val os: DesktopOs,
    /** The JVM's CPU architecture (`os.arch`); `null` = unknown, then only the OS is matched. */
    val arch: DesktopArch? = DesktopArch.current(),
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

    /**
     * The download link of the installer for [os] and [arch] in [release], or `null` when the release has none. An
     * asset name that states no architecture counts for any; one that states another architecture does not count.
     * The Linux package format (`.deb` / `.rpm` / `.tar.gz` / `.AppImage`) is not distinguished: the first matching asset
     * wins.
     */
    fun installerLink(release: GithubRelease): String? = release.assets
        .firstOrNull { asset ->
            os.installerExtensions.any { asset.name.endsWith(it, ignoreCase = true) } &&
                DesktopArch.of(asset.name).let { it == null || arch == null || it == arch }
        }
        ?.downloadLink

    /**
     * Whether `GetApplicationRelease` can compare [tag] with this build's version without failing: preview, a whole
     * number after removing everything but digits and dots (`r1234`); stable, as many or more dot-separated numbers as
     * [versionName] (`v1.14.0`). The interactor would throw on any other tag and fail the whole check.
     */
    fun isComparableTag(tag: String): Boolean {
        val digits = tag.replace(NON_VERSION_CHARS, "")
        return if (isPreview) {
            digits.toIntOrNull() != null
        } else {
            val parts = digits.split(".")
            parts.all { it.toIntOrNull() != null } &&
                parts.size >= versionName.replace(NON_VERSION_CHARS, "").split(".").size
        }
    }

    /** The desktop `ReleaseService` selection: [installerLink] of a release whose tag [isComparableTag]. */
    fun downloadLink(release: GithubRelease): String? =
        if (isComparableTag(release.version)) installerLink(release) else null

    companion object {
        const val STABLE_REPOSITORY = "komikku-app/komikku"
        const val PREVIEW_REPOSITORY = "komikku-app/komikku-preview"

        /** `x.y.z` (what jpackage accepts; `GetApplicationRelease` compares the numbers part by part). */
        private val RELEASE_VERSION = Regex("""\d+(\.\d+)*""")

        /** What `GetApplicationRelease.isNewVersion` removes from a tag before comparing. */
        private val NON_VERSION_CHARS = Regex("""[^\d.]""")

        fun fromSystemProperties(): DesktopUpdaterConfig = DesktopUpdaterConfig(
            enabled = System.getProperty("komikku.updater").toBoolean(),
            isPreview = System.getProperty("komikku.preview").toBoolean(),
            versionName = BuildConfig.VERSION_NAME,
            commitCount = System.getProperty("komikku.commitCount")?.toIntOrNull() ?: 0,
            os = DesktopOs.current(),
        )
    }
}

/**
 * The CPU architectures desktop installers are built for, with the spellings asset names commonly use. Matched as
 * separate words in the name (`Komikku-1.14.0-arm64.dmg`, `komikku_1.14.0_amd64.deb`, `komikku-1.14.0.x86_64.rpm`).
 */
enum class DesktopArch(spellings: List<String>) {
    X64(listOf("x64", "amd64", "x86_64", "x86-64")),
    Arm64(listOf("arm64", "aarch64")),
    ;

    /** A spelling between non-alphanumeric characters (or the ends of the name), ignoring case. */
    private val pattern = Regex(
        spellings.joinToString("|", prefix = "(?<![a-z0-9])(", postfix = ")(?![a-z0-9])") { Regex.escape(it) },
        RegexOption.IGNORE_CASE,
    )

    companion object {
        /** The architecture an asset name states, or `null` when it states none. */
        fun of(assetName: String): DesktopArch? = entries.firstOrNull { it.pattern.containsMatchIn(assetName) }

        /** [os.arch][System.getProperty] as a [DesktopArch], or `null` for another architecture. */
        fun current(osArch: String = System.getProperty("os.arch").orEmpty()): DesktopArch? = when (osArch.lowercase()) {
            "amd64", "x86_64", "x64", "x86-64" -> X64
            "aarch64", "arm64" -> Arm64
            else -> null
        }
    }
}

/**
 * The OS families the desktop packages are built for, with the installer file types of `build_desktop.yml` (P9: the
 * release assets `Komikku-<version>-<windows|macos|linux>-<x64|arm64>.<ext>`, the Linux app image packed as `.tar.gz`;
 * `.AppImage` for a single-file AppImage, which the pipeline does not build yet).
 */
enum class DesktopOs(val installerExtensions: List<String>) {
    Windows(listOf(".msi", ".exe")),
    MacOs(listOf(".dmg")),
    Linux(listOf(".deb", ".rpm", ".tar.gz", ".AppImage")),
    ;

    companion object {
        fun current(osName: String = System.getProperty("os.name").orEmpty()): DesktopOs = when {
            osName.startsWith("Windows", ignoreCase = true) -> Windows
            osName.startsWith("Mac", ignoreCase = true) -> MacOs
            else -> Linux
        }
    }
}
