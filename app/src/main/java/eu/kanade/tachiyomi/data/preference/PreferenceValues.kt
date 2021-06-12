package eu.kanade.tachiyomi.data.preference

const val UNMETERED_NETWORK = "wifi"
const val CHARGING = "ac"

/**
 * This class stores the values for the preferences in the application.
 */
object PreferenceValues {

    /* ktlint-disable experimental:enum-entry-name-case */

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
        strawberrydaiquiri,
        yotsuba
    }

    // Keys are lowercase to match legacy string values
    enum class DarkThemeVariant {
        default,
        blue,
        greenapple,
        midnightdusk,
        amoled,
        hotpink,
        amoledblue,
        red,
    }

    /* ktlint-enable experimental:enum-entry-name-case */

    enum class DisplayMode {
        COMPACT_GRID,
        COMFORTABLE_GRID,
        LIST,

        // SY -->
        NO_TITLE_GRID,
        // SY <--
    }

    enum class TappingInvertMode(val shouldInvertHorizontal: Boolean = false, val shouldInvertVertical: Boolean = false) {
        NONE,
        HORIZONTAL(shouldInvertHorizontal = true),
        VERTICAL(shouldInvertVertical = true),
        BOTH(shouldInvertHorizontal = true, shouldInvertVertical = true)
    }

    // SY -->
    enum class GroupLibraryMode {
        GLOBAL,
        ALL_BUT_UNGROUPED,
        ALL
    }
    // SY <--
}
