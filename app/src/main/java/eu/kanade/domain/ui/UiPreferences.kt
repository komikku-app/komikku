package eu.kanade.domain.ui

import android.os.Build
import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.domain.ui.model.TabletUiMode
import eu.kanade.domain.ui.model.ThemeMode
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.isDynamicColorAvailable
import tachiyomi.core.preference.PreferenceStore
import tachiyomi.core.preference.getEnum
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale

class UiPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun themeMode() = preferenceStore.getEnum(
        "pref_theme_mode_key",
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { ThemeMode.SYSTEM } else { ThemeMode.LIGHT },
    )

    fun appTheme() = preferenceStore.getEnum(
        "pref_app_theme",
        if (DeviceUtil.isDynamicColorAvailable) { AppTheme.MONET } else { AppTheme.DEFAULT },
    )

    fun themeDarkAmoled() = preferenceStore.getBoolean("pref_theme_dark_amoled_key", false)

    fun relativeTime() = preferenceStore.getBoolean("relative_time_v2", true)

    fun dateFormat() = preferenceStore.getString("app_date_format", "")

    fun tabletUiMode() = preferenceStore.getEnum("tablet_ui_mode", TabletUiMode.AUTOMATIC)

    // SY -->

    fun expandFilters() = preferenceStore.getBoolean("eh_expand_filters", false)

    fun feedTabInFront() = preferenceStore.getBoolean("latest_tab_position", false)

    fun recommendsInOverflow() = preferenceStore.getBoolean("recommends_in_overflow", false)

    fun mergeInOverflow() = preferenceStore.getBoolean("merge_in_overflow", false)

    fun useNewSourceNavigation() = preferenceStore.getBoolean("use_new_source_navigation", true)

    fun bottomBarLabels() = preferenceStore.getBoolean("pref_show_bottom_bar_labels", true)

    fun showNavUpdates() = preferenceStore.getBoolean("pref_show_updates_button", true)

    fun showNavHistory() = preferenceStore.getBoolean("pref_show_history_button", true)

    // SY <--

    companion object {
        fun dateFormat(format: String): DateFormat = when (format) {
            "" -> DateFormat.getDateInstance(DateFormat.SHORT)
            else -> SimpleDateFormat(format, Locale.getDefault())
        }
    }
}
