package eu.kanade.presentation.more.settings.screen

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.materialkolor.PaletteStyle
import eu.kanade.core.preference.asState
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.domain.ui.model.TabletUiMode
import eu.kanade.domain.ui.model.ThemeMode
import eu.kanade.domain.ui.model.setAppCompatDelegateThemeMode
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.appearance.AppCustomThemeColorPickerScreen
import eu.kanade.presentation.more.settings.screen.appearance.AppLanguageScreen
import eu.kanade.presentation.more.settings.widget.AppThemeModePreferenceWidget
import eu.kanade.presentation.more.settings.widget.AppThemePreferenceWidget
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableMap
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.LocalDate

object SettingsAppearanceScreen : SearchableSettings {
    private fun readResolve(): Any = SettingsAppearanceScreen

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_appearance

    @Composable
    override fun getPreferences(): List<Preference> {
        val uiPreferences = remember { Injekt.get<UiPreferences>() }

        return listOf(
            getThemeGroup(uiPreferences = uiPreferences),
            // KMK -->
            getMangaInfoThemeGroup(uiPreferences = uiPreferences),
            // KMK <--
            getDisplayGroup(uiPreferences = uiPreferences),
            // SY -->
            getNavbarGroup(uiPreferences = uiPreferences),
            getForkGroup(uiPreferences = uiPreferences),
            // SY <--
        )
    }

    @Composable
    private fun getThemeGroup(
        uiPreferences: UiPreferences,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        val themeModePref = uiPreferences.themeMode()
        val themeMode by themeModePref.collectAsState()

        val appThemePref = uiPreferences.appTheme()
        val appTheme by appThemePref.collectAsState()

        val amoledPref = uiPreferences.themeDarkAmoled()
        val amoled by amoledPref.collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_theme),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.CustomPreference(
                    title = stringResource(MR.strings.pref_app_theme),
                ) {
                    Column {
                        AppThemeModePreferenceWidget(
                            value = themeMode,
                            onItemClick = {
                                themeModePref.set(it)
                                setAppCompatDelegateThemeMode(it)
                            },
                        )

                        AppThemePreferenceWidget(
                            value = appTheme,
                            amoled = amoled,
                            onItemClick = { appThemePref.set(it) },
                        )
                    }
                },
                // KMK -->
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(KMR.strings.pref_custom_color),
                    enabled = appTheme == AppTheme.CUSTOM,
                    subtitle = stringResource(KMR.strings.custom_color_description),
                    onClick = { navigator.push(AppCustomThemeColorPickerScreen()) },
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = uiPreferences.customThemeStyle(),
                    title = stringResource(KMR.strings.pref_custom_theme_style),
                    enabled = appTheme == AppTheme.CUSTOM,
                    entries = PaletteStyle.entries
                        .associateWith {
                            when (it) {
                                PaletteStyle.TonalSpot ->
                                    stringResource(KMR.strings.pref_theme_cover_based_style_tonalspot)
                                PaletteStyle.Neutral ->
                                    stringResource(KMR.strings.pref_theme_cover_based_style_neutral)
                                PaletteStyle.Vibrant ->
                                    stringResource(KMR.strings.pref_theme_cover_based_style_vibrant)
                                PaletteStyle.Expressive ->
                                    stringResource(KMR.strings.pref_theme_cover_based_style_expressive)
                                PaletteStyle.Rainbow ->
                                    stringResource(KMR.strings.pref_theme_cover_based_style_rainbow)
                                PaletteStyle.FruitSalad ->
                                    stringResource(KMR.strings.pref_theme_cover_based_style_fruitsalad)
                                PaletteStyle.Monochrome ->
                                    stringResource(KMR.strings.pref_theme_cover_based_style_monochrome)
                                PaletteStyle.Fidelity ->
                                    stringResource(KMR.strings.pref_theme_cover_based_style_fidelity)
                                PaletteStyle.Content ->
                                    stringResource(KMR.strings.pref_theme_cover_based_style_content)
                                else -> it.name
                            }
                        }
                        .toImmutableMap(),
                    onValueChanged = {
                        (context as? Activity)?.let { ActivityCompat.recreate(it) }
                        true
                    },
                ),
                // KMK <--
                Preference.PreferenceItem.SwitchPreference(
                    pref = amoledPref,
                    title = stringResource(MR.strings.pref_dark_theme_pure_black),
                    enabled = themeMode != ThemeMode.LIGHT,
                    onValueChanged = {
                        (context as? Activity)?.let { ActivityCompat.recreate(it) }
                        true
                    },
                ),
            ),
        )
    }

    // KMK -->
    @Composable
    private fun getMangaInfoThemeGroup(
        uiPreferences: UiPreferences,
    ): Preference.PreferenceGroup {
        val scope = rememberCoroutineScope()
        val mangaInfoThemeCoverBased by remember {
            Injekt.get<UiPreferences>().themeCoverBased().asState(scope)
        }
        return Preference.PreferenceGroup(
            title = stringResource(KMR.strings.pref_manga_info),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    pref = uiPreferences.themeCoverBased(),
                    title = stringResource(KMR.strings.pref_theme_cover_based),
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = uiPreferences.themeCoverBasedStyle(),
                    title = stringResource(KMR.strings.pref_theme_cover_based_style),
                    enabled = mangaInfoThemeCoverBased,
                    entries = PaletteStyle.entries
                        .associateWith {
                            when (it) {
                                PaletteStyle.TonalSpot ->
                                    stringResource(KMR.strings.pref_theme_cover_based_style_tonalspot)
                                PaletteStyle.Neutral ->
                                    stringResource(KMR.strings.pref_theme_cover_based_style_neutral)
                                PaletteStyle.Vibrant ->
                                    stringResource(KMR.strings.pref_theme_cover_based_style_vibrant)
                                PaletteStyle.Expressive ->
                                    stringResource(KMR.strings.pref_theme_cover_based_style_expressive)
                                PaletteStyle.Rainbow ->
                                    stringResource(KMR.strings.pref_theme_cover_based_style_rainbow)
                                PaletteStyle.FruitSalad ->
                                    stringResource(KMR.strings.pref_theme_cover_based_style_fruitsalad)
                                PaletteStyle.Monochrome ->
                                    stringResource(KMR.strings.pref_theme_cover_based_style_monochrome)
                                PaletteStyle.Fidelity ->
                                    stringResource(KMR.strings.pref_theme_cover_based_style_fidelity)
                                PaletteStyle.Content ->
                                    stringResource(KMR.strings.pref_theme_cover_based_style_content)
                                else -> it.name
                            }
                        }
                        .toImmutableMap(),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = uiPreferences.usePanoramaCoverMangaInfo(),
                    title = stringResource(KMR.strings.pref_panorama_cover),
                    subtitle = stringResource(KMR.strings.pref_panorama_cover_summary),
                ),
            ),
        )
    }
    // KMK <--

    @Composable
    private fun getDisplayGroup(
        uiPreferences: UiPreferences,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        val now = remember { LocalDate.now() }

        val dateFormat by uiPreferences.dateFormat().collectAsState()
        val formattedNow = remember(dateFormat) {
            UiPreferences.dateFormat(dateFormat).format(now)
        }

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_display),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_app_language),
                    onClick = { navigator.push(AppLanguageScreen()) },
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = uiPreferences.tabletUiMode(),
                    title = stringResource(MR.strings.pref_tablet_ui_mode),
                    entries = TabletUiMode.entries
                        .associateWith { stringResource(it.titleRes) }
                        .toImmutableMap(),
                    onValueChanged = {
                        context.toast(MR.strings.requires_app_restart)
                        true
                    },
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = uiPreferences.dateFormat(),
                    title = stringResource(MR.strings.pref_date_format),
                    entries = DateFormats
                        .associateWith {
                            val formattedDate = UiPreferences.dateFormat(it).format(now)
                            "${it.ifEmpty { stringResource(MR.strings.label_default) }} ($formattedDate)"
                        }
                        .toImmutableMap(),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = uiPreferences.relativeTime(),
                    title = stringResource(MR.strings.pref_relative_format),
                    subtitle = stringResource(
                        MR.strings.pref_relative_format_summary,
                        stringResource(MR.strings.relative_time_today),
                        formattedNow,
                    ),
                ),
            ),
        )
    }

    // SY -->
    @Composable
    fun getForkGroup(uiPreferences: UiPreferences): Preference.PreferenceGroup {
        val previewsRowCount by uiPreferences.previewsRowCount().collectAsState()
        // KMK -->
        val sourcePreferences = remember { Injekt.get<SourcePreferences>() }
        val relatedMangasInOverflow by uiPreferences.expandRelatedMangas().collectAsState()
        // KMK <--

        return Preference.PreferenceGroup(
            stringResource(SYMR.strings.pref_category_fork),
            preferenceItems = persistentListOf(
                // KMK -->
                Preference.PreferenceItem.SwitchPreference(
                    pref = uiPreferences.usePanoramaCoverFlow(),
                    title = stringResource(KMR.strings.pref_panorama_cover_flow),
                    subtitle = stringResource(KMR.strings.pref_panorama_cover_flow_summary),
                ),
                // KMK <--
                Preference.PreferenceItem.SwitchPreference(
                    pref = uiPreferences.expandFilters(),
                    title = stringResource(SYMR.strings.toggle_expand_search_filters),
                ),
                // KMK -->
                Preference.PreferenceItem.SwitchPreference(
                    pref = uiPreferences.expandRelatedMangas(),
                    title = stringResource(KMR.strings.pref_expand_related_mangas),
                    subtitle = stringResource(KMR.strings.pref_expand_related_mangas_summary),
                    enabled = sourcePreferences.relatedMangas().get(),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = uiPreferences.relatedMangasInOverflow(),
                    enabled = !relatedMangasInOverflow,
                    title = stringResource(KMR.strings.put_related_mangas_in_overflow),
                    subtitle = stringResource(KMR.strings.put_related_mangas_in_overflow_summary),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = uiPreferences.showHomeOnRelatedMangas(),
                    title = stringResource(KMR.strings.pref_show_home_on_related_mangas),
                    subtitle = stringResource(KMR.strings.pref_show_home_on_related_mangas_summary),
                    enabled = sourcePreferences.relatedMangas().get(),
                ),
                // KMK <--
                Preference.PreferenceItem.SwitchPreference(
                    pref = uiPreferences.recommendsInOverflow(),
                    title = stringResource(SYMR.strings.put_recommends_in_overflow),
                    subtitle = stringResource(SYMR.strings.put_recommends_in_overflow_summary),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = uiPreferences.mergeInOverflow(),
                    title = stringResource(SYMR.strings.put_merge_in_overflow),
                    subtitle = stringResource(SYMR.strings.put_merge_in_overflow_summary),
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = previewsRowCount,
                    title = stringResource(SYMR.strings.pref_previews_row_count),
                    subtitle = if (previewsRowCount > 0) {
                        pluralStringResource(
                            SYMR.plurals.row_count,
                            previewsRowCount,
                            previewsRowCount,
                        )
                    } else {
                        stringResource(MR.strings.disabled)
                    },
                    min = 0,
                    max = 10,
                    onValueChanged = {
                        uiPreferences.previewsRowCount().set(it)
                        true
                    },
                ),
            ),
        )
    }

    @Composable
    fun getNavbarGroup(uiPreferences: UiPreferences): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            stringResource(SYMR.strings.pref_category_navbar),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    pref = uiPreferences.showNavUpdates(),
                    title = stringResource(SYMR.strings.pref_hide_updates_button),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = uiPreferences.showNavHistory(),
                    title = stringResource(SYMR.strings.pref_hide_history_button),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = uiPreferences.bottomBarLabels(),
                    title = stringResource(SYMR.strings.pref_show_bottom_bar_labels),
                ),
            ),
        )
    }
    // SY <--
}

private val DateFormats = listOf(
    "", // Default
    "MM/dd/yy",
    "dd/MM/yy",
    "yyyy-MM-dd",
    "dd MMM yyyy",
    "MMM dd, yyyy",
)
