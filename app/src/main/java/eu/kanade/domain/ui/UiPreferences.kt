package eu.kanade.domain.ui

import com.materialkolor.PaletteStyle
import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.domain.ui.model.TabletUiMode
import eu.kanade.domain.ui.model.ThemeMode
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.isDynamicColorAvailable
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class UiPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun themeMode() = preferenceStore.getEnum("pref_theme_mode_key", ThemeMode.SYSTEM)

    fun appTheme() = preferenceStore.getEnum(
        "pref_app_theme",
        if (DeviceUtil.isDynamicColorAvailable) {
            AppTheme.MONET
        } else {
            AppTheme.DEFAULT
        },
    )

    fun themeDarkAmoled() = preferenceStore.getBoolean("pref_theme_dark_amoled_key", false)

    // KMK -->
    fun colorTheme() = preferenceStore.getInt("pref_color_theme", 0xFFDF0090.toInt())

    fun themeCoverBased() = preferenceStore.getBoolean("pref_theme_cover_based_key", true)

    fun themeCoverBasedStyle() = preferenceStore.getEnum("pref_theme_cover_based_style_key", PaletteStyle.Vibrant)

    fun preloadLibraryColor() = preferenceStore.getBoolean("pref_preload_library_color_key", true)
    // KMK <--

    fun relativeTime() = preferenceStore.getBoolean("relative_time_v2", true)

    fun dateFormat() = preferenceStore.getString("app_date_format", "")

    fun tabletUiMode() = preferenceStore.getEnum("tablet_ui_mode", TabletUiMode.AUTOMATIC)

    // SY -->

    fun expandFilters() = preferenceStore.getBoolean("eh_expand_filters", false)

    fun hideFeedTab() = preferenceStore.getBoolean("hide_latest_tab", false)

    fun feedTabInFront() = preferenceStore.getBoolean("latest_tab_position", false)

    // KMK -->
    fun expandRelatedTitles() = preferenceStore.getBoolean("expand_related_titles", true)

    fun relatedTitlesInOverflow() = preferenceStore.getBoolean("related_titles_in_overflow", false)

    fun showHomeOnRelatedTitles() = preferenceStore.getBoolean("show_home_on_related_titles", true)
    // KMK <--

    fun recommendsInOverflow() = preferenceStore.getBoolean("recommends_in_overflow", false)

    fun mergeInOverflow() = preferenceStore.getBoolean("merge_in_overflow", true)

    fun previewsRowCount() = preferenceStore.getInt("pref_previews_row_count", 4)

    fun useNewSourceNavigation() = preferenceStore.getBoolean("use_new_source_navigation", true)

    fun bottomBarLabels() = preferenceStore.getBoolean("pref_show_bottom_bar_labels", true)

    fun showNavUpdates() = preferenceStore.getBoolean("pref_show_updates_button", true)

    fun showNavHistory() = preferenceStore.getBoolean("pref_show_history_button", true)

    // SY <--

    companion object {
        fun dateFormat(format: String): DateTimeFormatter = when (format) {
            "" -> DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
            else -> DateTimeFormatter.ofPattern(format, Locale.getDefault())
        }
    }
}
