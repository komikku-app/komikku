package eu.kanade.tachiyomi.data.preference

/**
 * This class stores the values for the preferences in the application.
 */
object PreferenceValues {

    // Keys are lowercase to match legacy string values
    enum class ThemeMode {
        light,
        dark,
        system,
    }

    // Keys are lowercase to match legacy string values
    enum class LightThemeVariant {
        default,
        blue,
        smoothie,
    }

    // Keys are lowercase to match legacy string values
    enum class DarkThemeVariant {
        default,
        blue,
        amoled,
        red,
    }

    enum class DisplayMode {
        COMPACT_GRID,
        COMFORTABLE_GRID,
        LIST,
    }

    enum class NsfwAllowance {
        ALLOWED,
        PARTIAL,
        BLOCKED
    }

    enum class ExtensionInstaller {
        LEGACY,
        PACKAGEINSTALLER,
        SHIZUKU
    }
}
