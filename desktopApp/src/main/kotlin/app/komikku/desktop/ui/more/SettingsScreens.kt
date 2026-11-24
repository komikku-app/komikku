package app.komikku.desktop.ui.more

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChromeReaderMode
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.GetApp
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.komikku.desktop.ui.browse.BrowseScaffold
import app.komikku.desktop.updater.DesktopAppUpdateChecker
import app.komikku.desktop.webview.DesktopCloudflarePreferences
import app.komikku.desktop.webview.FlareSolverrBackend
import app.komikku.desktop.webview.MaskedAddressPreference
import app.komikku.shared.settings.PreferenceList
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.domain.ui.model.ThemeMode
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.library.model.GroupLibraryMode
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MANGA_HAS_UNREAD
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MANGA_NON_COMPLETED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MANGA_NON_READ
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MANGA_OUTSIDE_RELEASE_PERIOD
import tachiyomi.domain.storage.service.StoragePreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.core.common.i18n.stringResource as localized

/**
 * Desktop settings (Android `SettingsMainScreen` + the most used categories), built from the borrowed
 * data-driven settings model (`Preference` items rendered by the borrowed `PreferenceItem` widgets) and
 * bound to the same preference classes and keys as Android, so settings move with backups.
 */
class SettingsHomeScreen : Screen() {

    private data class Entry(val title: StringResource, val icon: androidx.compose.ui.graphics.vector.ImageVector, val tag: String, val screen: () -> Screen)

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val entries = listOf(
            Entry(MR.strings.pref_category_appearance, Icons.Outlined.Palette, "appearance") { SettingsCategoryScreen(SettingsCategory.Appearance) },
            Entry(MR.strings.pref_category_library, Icons.Outlined.CollectionsBookmark, "library") { SettingsCategoryScreen(SettingsCategory.Library) },
            Entry(MR.strings.pref_category_reader, Icons.Outlined.ChromeReaderMode, "reader") { SettingsCategoryScreen(SettingsCategory.Reader) },
            Entry(MR.strings.pref_category_downloads, Icons.Outlined.GetApp, "downloads") { SettingsCategoryScreen(SettingsCategory.Downloads) },
            Entry(MR.strings.pref_category_tracking, Icons.Outlined.Sync, "tracking") { TrackingSettingsScreen() },
            Entry(MR.strings.browse, Icons.Outlined.Explore, "browse") { SettingsCategoryScreen(SettingsCategory.Browse) },
            Entry(MR.strings.label_data_storage, Icons.Outlined.Storage, "data") { DataStorageScreen() },
            // P16: the app lock (Android's "Security and privacy")
            Entry(MR.strings.pref_category_security, Icons.Outlined.Security, "security") { SettingsCategoryScreen(SettingsCategory.Security) },
            Entry(MR.strings.pref_category_advanced, Icons.Outlined.Code, "advanced") { SettingsCategoryScreen(SettingsCategory.Advanced) },
            Entry(MR.strings.pref_category_about, Icons.Outlined.Info, "about") { SettingsCategoryScreen(SettingsCategory.About) },
        )
        BrowseScaffold(
            title = stringResource(MR.strings.label_settings),
            onBack = navigator::pop,
            // P15: Android's settings search action (`SettingsMainScreen`)
            actions = {
                IconButton(onClick = { navigator.push(SettingsSearchScreen()) }, modifier = Modifier.testTag("settings-search")) {
                    Icon(Icons.Outlined.Search, contentDescription = stringResource(MR.strings.action_search_settings))
                }
            },
        ) {
            LazyColumn(Modifier.fillMaxSize().testTag("settings-home")) {
                entries.forEach { entry ->
                    item {
                        LinkRow(entry.icon, stringResource(entry.title), null, "settings-${entry.tag}") { navigator.push(entry.screen()) }
                    }
                }
            }
        }
    }
}

enum class SettingsCategory(val title: StringResource) {
    Appearance(MR.strings.pref_category_appearance),
    Library(MR.strings.pref_category_library),
    Reader(MR.strings.pref_category_reader),
    Downloads(MR.strings.pref_category_downloads),
    Browse(MR.strings.browse),
    Security(MR.strings.pref_category_security),
    Advanced(MR.strings.pref_category_advanced),
    About(MR.strings.pref_category_about),
}

/**
 * One settings category. [highlightKey] (P15, a settings search result): the title of the item the list scrolls to and
 * highlights, like Android's `SearchableSettings.highlightKey`.
 */
class SettingsCategoryScreen(
    private val category: SettingsCategory,
    private val highlightKey: String? = null,
) : Screen() {

    override val key: String get() = "settings-${category.name}"

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val items = settingsCategoryPreferences(category, navigator)
        BrowseScaffold(title = stringResource(category.title), onBack = navigator::pop) {
            PreferenceList(items, Modifier.fillMaxSize().testTag("settings-${category.name}"), highlightKey = highlightKey)
        }
    }
}

/** The items of [category]'s screen (also the settings search's index of it, P15). */
@Composable
internal fun settingsCategoryPreferences(category: SettingsCategory, navigator: Navigator): List<Preference> = when (category) {
    SettingsCategory.Appearance -> appearancePreferences()
    SettingsCategory.Library -> libraryPreferences(navigator)
    SettingsCategory.Reader -> readerPreferences()
    SettingsCategory.Downloads -> downloadPreferences()
    SettingsCategory.Browse -> browsePreferences()
    SettingsCategory.Security -> securityPreferences()
    SettingsCategory.Advanced -> advancedPreferences(navigator)
    SettingsCategory.About -> aboutPreferences()
}

/** Android `SettingsAppearanceScreen` theme group: mode, theme, pure black (desktop: list instead of previews). */
@Composable
private fun appearancePreferences(): List<Preference> {
    val ui = remember { Injekt.get<UiPreferences>() }
    return listOf(
        Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_theme),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    preference = ui.themeMode(),
                    entries = persistentMapOf(
                        ThemeMode.SYSTEM to stringResource(MR.strings.theme_system),
                        ThemeMode.LIGHT to stringResource(MR.strings.theme_light),
                        ThemeMode.DARK to stringResource(MR.strings.theme_dark),
                    ),
                    title = stringResource(MR.strings.pref_category_theme),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = ui.appTheme(),
                    entries = AppTheme.entries
                        .filter { it.titleRes != null }
                        .associateWith { stringResource(it.titleRes!!) }
                        .toImmutableMap(),
                    title = stringResource(MR.strings.pref_app_theme),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = ui.themeDarkAmoled(),
                    title = stringResource(MR.strings.pref_dark_theme_pure_black),
                ),
            ),
        ),
        Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_display),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = ui.relativeTime(),
                    title = stringResource(MR.strings.pref_relative_format),
                ),
            ),
        ),
    )
}

/**
 * Android `SettingsLibraryScreen`: categories, default category, update interval (P3) / scope / restrictions / grouped
 * updates, metadata.
 */
@Composable
private fun libraryPreferences(navigator: Navigator): List<Preference> {
    val library = remember { Injekt.get<LibraryPreferences>() }
    val getCategories = remember { Injekt.get<GetCategories>() }
    val categories by remember { getCategories.subscribe() }.collectAsState(emptyList())
    val userCategories = categories.filterNot { it.isSystemCategory }
    val defaultLabel = stringResource(MR.strings.label_default)
    val categoryNames = (listOf(0L to defaultLabel) + userCategories.map { it.id to it.name })
    return listOf(
        Preference.PreferenceGroup(
            title = stringResource(MR.strings.categories),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.action_edit_categories),
                    subtitle = pluralStringResource(MR.plurals.num_categories, userCategories.size, userCategories.size),
                    onClick = { navigator.push(CategoriesScreen()) },
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = library.defaultCategory(),
                    entries = (listOf(-1 to stringResource(MR.strings.default_category_summary)) + categoryNames.map { it.first.toInt() to it.second })
                        .toMap().toImmutableMap(),
                    title = stringResource(MR.strings.default_category),
                ),
            ),
        ),
        Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_library_update),
            preferenceItems = persistentListOf(
                // P3: Android's update interval, run by the desktop scheduler while the app is open (Android's device
                // restrictions — Wi-Fi, unmetered, charging — have no desktop counterpart and are not offered)
                Preference.PreferenceItem.ListPreference(
                    preference = library.autoUpdateInterval(),
                    entries = persistentMapOf(
                        0 to stringResource(MR.strings.update_never),
                        12 to stringResource(MR.strings.update_12hour),
                        24 to stringResource(MR.strings.update_24hour),
                        48 to stringResource(MR.strings.update_48hour),
                        72 to stringResource(MR.strings.update_72hour),
                        168 to stringResource(MR.strings.update_weekly),
                    ),
                    title = stringResource(MR.strings.pref_library_update_interval),
                ),
                Preference.PreferenceItem.MultiSelectListPreference(
                    preference = library.updateCategories(),
                    entries = categoryNames.associate { it.first.toString() to it.second }.toImmutableMap(),
                    title = stringResource(MR.strings.categories),
                ),
                Preference.PreferenceItem.MultiSelectListPreference(
                    preference = library.autoUpdateMangaRestrictions(),
                    entries = persistentMapOf(
                        MANGA_HAS_UNREAD to stringResource(MR.strings.pref_update_only_completely_read),
                        MANGA_NON_READ to stringResource(MR.strings.pref_update_only_started),
                        MANGA_NON_COMPLETED to stringResource(MR.strings.pref_update_only_non_completed),
                        MANGA_OUTSIDE_RELEASE_PERIOD to stringResource(MR.strings.pref_update_only_in_release_period),
                    ),
                    title = stringResource(MR.strings.pref_library_update_smart_update),
                ),
                // Android SY: what "update" on a page of a library grouped by something other than category updates
                Preference.PreferenceItem.ListPreference(
                    preference = library.groupLibraryUpdateType(),
                    entries = persistentMapOf(
                        GroupLibraryMode.GLOBAL to stringResource(SYMR.strings.library_group_updates_global),
                        GroupLibraryMode.ALL_BUT_UNGROUPED to stringResource(SYMR.strings.library_group_updates_all_but_ungrouped),
                        GroupLibraryMode.ALL to stringResource(SYMR.strings.library_group_updates_all),
                    ),
                    title = stringResource(SYMR.strings.library_group_updates),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = library.autoUpdateMetadata(),
                    title = stringResource(MR.strings.pref_library_update_refresh_metadata),
                ),
            ),
        ),
    )
}

/** Android `SettingsReaderScreen` defaults the desktop reader honours. */
@Composable
private fun readerPreferences(): List<Preference> {
    val reader = remember { Injekt.get<ReaderPreferences>() }
    return listOf(
        Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_general),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    preference = reader.defaultReadingMode(),
                    entries = ReadingMode.entries.drop(1).associate { it.flagValue to stringResource(it.stringRes) }.toImmutableMap(),
                    title = stringResource(MR.strings.pref_viewer_type),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = reader.imageScaleType(),
                    entries = ReaderPreferences.ImageScaleType.mapIndexed { index, it -> index + 1 to stringResource(it) }.toMap().toImmutableMap(),
                    title = stringResource(MR.strings.pref_image_scale_type),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = reader.readerTheme(),
                    entries = persistentMapOf(
                        1 to stringResource(MR.strings.black_background),
                        2 to stringResource(MR.strings.gray_background),
                        0 to stringResource(MR.strings.white_background),
                        3 to stringResource(MR.strings.automatic_background),
                    ),
                    title = stringResource(MR.strings.pref_reader_theme),
                ),
                Preference.PreferenceItem.SwitchPreference(reader.showPageNumber(), stringResource(MR.strings.pref_show_page_number)),
            ),
        ),
        Preference.PreferenceGroup(
            title = stringResource(MR.strings.chapters),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(reader.skipRead(), stringResource(MR.strings.pref_skip_read_chapters)),
                Preference.PreferenceItem.SwitchPreference(reader.skipFiltered(), stringResource(MR.strings.pref_skip_filtered_chapters)),
                Preference.PreferenceItem.SwitchPreference(reader.skipDupe(), stringResource(MR.strings.pref_skip_dupe_chapters)),
            ),
        ),
    )
}

/** Android `SettingsDownloadScreen`: format, delete after reading, download ahead. */
@Composable
private fun downloadPreferences(): List<Preference> {
    val downloads = remember { Injekt.get<DownloadPreferences>() }
    val storageDir by remember { Injekt.get<StoragePreferences>().baseStorageDirectory() }.collectAsState()
    return listOf(
        Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_general),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.InfoPreference(
                    title = stringResource(MR.strings.pref_storage_location) + ": " +
                        (android.net.Uri.parse(storageDir).path ?: storageDir) + "/downloads",
                ),
                Preference.PreferenceItem.SwitchPreference(downloads.saveChaptersAsCBZ(), stringResource(MR.strings.save_chapter_as_cbz)),
                Preference.PreferenceItem.SwitchPreference(downloads.splitTallImages(), stringResource(MR.strings.split_tall_images)),
            ),
        ),
        Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_delete_chapters),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(downloads.removeAfterMarkedAsRead(), stringResource(MR.strings.pref_remove_after_marked_as_read)),
                Preference.PreferenceItem.ListPreference(
                    preference = downloads.removeAfterReadSlots(),
                    entries = persistentMapOf(
                        -1 to stringResource(MR.strings.disabled),
                        0 to stringResource(MR.strings.last_read_chapter),
                        1 to stringResource(MR.strings.second_to_last),
                        2 to stringResource(MR.strings.third_to_last),
                        3 to stringResource(MR.strings.fourth_to_last),
                        4 to stringResource(MR.strings.fifth_to_last),
                    ),
                    title = stringResource(MR.strings.pref_remove_after_read),
                ),
                Preference.PreferenceItem.SwitchPreference(downloads.removeBookmarkedChapters(), stringResource(MR.strings.pref_remove_bookmarked_chapters)),
            ),
        ),
        Preference.PreferenceGroup(
            title = stringResource(MR.strings.download_ahead),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    preference = downloads.autoDownloadWhileReading(),
                    entries = listOf(0, 2, 3, 5, 10)
                        .associateWith { if (it == 0) stringResource(MR.strings.disabled) else pluralStringResource(MR.plurals.next_unread_chapters, it, it) }
                        .toImmutableMap(),
                    title = stringResource(MR.strings.auto_download_while_reading),
                ),
            ),
        ),
    )
}

/** Android `SettingsBrowseScreen` subset + the More tab's incognito toggle. */
@Composable
private fun browsePreferences(): List<Preference> {
    val sources = remember { Injekt.get<SourcePreferences>() }
    val base = remember { Injekt.get<BasePreferences>() }
    val ui = remember { Injekt.get<UiPreferences>() }
    val hideFeedTab by ui.hideFeedTab().collectAsState()
    return listOf(
        Preference.PreferenceGroup(
            title = stringResource(MR.strings.label_sources),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(sources.hideInLibraryItems(), stringResource(MR.strings.pref_hide_in_library_items)),
                // P13: SY's "new source navigation": a source opens its feed (landing page) instead of its catalogue
                Preference.PreferenceItem.SwitchPreference(
                    ui.useNewSourceNavigation(),
                    stringResource(SYMR.strings.pref_source_navigation),
                    subtitle = stringResource(SYMR.strings.pref_source_navigation_summery),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    base.incognitoMode(),
                    stringResource(MR.strings.pref_incognito_mode),
                    subtitle = stringResource(MR.strings.pref_incognito_mode_summary),
                ),
                // P4's delegated sources toggle and P8's Cloudflare solver are in Settings → Advanced (P12, Android's place)
            ),
        ),
        // P13: Android's feed group (the Feed tab of Browse)
        Preference.PreferenceGroup(
            title = stringResource(SYMR.strings.feed),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(ui.hideFeedTab(), stringResource(SYMR.strings.pref_hide_feed)),
                Preference.PreferenceItem.SwitchPreference(
                    ui.feedTabInFront(),
                    stringResource(SYMR.strings.pref_feed_position),
                    subtitle = stringResource(SYMR.strings.pref_feed_position_summery),
                    enabled = !hideFeedTab,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    sources.hideInLibraryFeedItems(),
                    stringResource(MR.strings.pref_hide_in_library_items),
                ),
            ),
        ),
        Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_nsfw_content),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    sources.showNsfwSource(),
                    stringResource(MR.strings.pref_show_nsfw_source),
                    subtitle = stringResource(MR.strings.requires_app_restart),
                ),
            ),
        ),
    )
}

/**
 * P8: the automatic Cloudflare backend, a FlareSolverr-compatible service the user runs (desktop only; Android has no
 * such setting). Empty address = off (the manual "Open in WebView" flow); "Test connection" asks the service. In
 * Settings → Advanced next to the network group since P12.
 */
@Composable
internal fun cloudflarePreferences(): Preference.PreferenceGroup {
    val preferences = remember { Injekt.get<DesktopCloudflarePreferences>() }
    val backend = remember { Injekt.get<FlareSolverrBackend>() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var testing by remember { mutableStateOf(false) }
    // P8 review: the password of a `user:password@` address is never rendered (subtitle and edit field).
    val address = remember { MaskedAddressPreference(preferences.flareSolverrUrl()) }
    val url by address.collectAsState()
    val invalid = stringResource(KMR.strings.desktop_flaresolverr_invalid)
    return Preference.PreferenceGroup(
        title = stringResource(KMR.strings.desktop_flaresolverr_title),
        preferenceItems = persistentListOf(
            Preference.PreferenceItem.EditTextPreference(
                preference = address,
                title = stringResource(KMR.strings.desktop_flaresolverr_url),
                // The widget formats the subtitle: a `%` of the address (an encoded character) stays a `%`.
                subtitle = url.ifBlank { stringResource(MR.strings.off) }.replace("%", "%%"),
                onValueChanged = { value ->
                    DesktopCloudflarePreferences.isValidFlareSolverrUrl(value).also { valid ->
                        if (!valid) context.toast(invalid)
                    }
                },
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(KMR.strings.desktop_flaresolverr_test),
                widget = {
                    if (testing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp).testTag("flaresolverr-test-progress"),
                            strokeWidth = 3.dp,
                        )
                    }
                },
                onClick = {
                    if (!testing) {
                        testing = true
                        scope.launch {
                            try {
                                val result = try {
                                    withIOContext { backend.testConnection() }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    // P8 review: nothing escapes into the composition's scope.
                                    FlareSolverrBackend.ConnectionTest.Failed(e.message ?: e::class.simpleName.orEmpty())
                                }
                                context.toast(flareSolverrTestMessage(context, result))
                            } finally {
                                testing = false
                            }
                        }
                    }
                },
            ),
            // The shared edit dialog cannot save an empty value: this turns the solver off (P8 review fix round).
            Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.action_disable),
                enabled = url.isNotBlank(),
                onClick = { preferences.flareSolverrUrl().set("") },
            ),
            Preference.PreferenceItem.InfoPreference(stringResource(KMR.strings.desktop_flaresolverr_info)),
        ),
    )
}

/** The message for a "Test connection" [result]. */
internal fun flareSolverrTestMessage(context: android.content.Context, result: FlareSolverrBackend.ConnectionTest): String =
    when (result) {
        FlareSolverrBackend.ConnectionTest.NotConfigured -> context.localized(KMR.strings.desktop_flaresolverr_not_configured)
        FlareSolverrBackend.ConnectionTest.InvalidAddress -> context.localized(KMR.strings.desktop_flaresolverr_invalid)
        is FlareSolverrBackend.ConnectionTest.Ok -> context.localized(KMR.strings.desktop_flaresolverr_ok, result.version.orEmpty()).trim()
        is FlareSolverrBackend.ConnectionTest.Unreachable -> context.localized(KMR.strings.desktop_flaresolverr_unreachable, result.detail)
        is FlareSolverrBackend.ConnectionTest.Failed -> context.localized(KMR.strings.desktop_flaresolverr_failed, result.detail)
    }

/**
 * Android `AboutScreen`: the version (with the build's channel) and, when the build has the updater (P6, `-Penable-updater`),
 * "Check for updates" (a forced check; a found update opens the update dialog, otherwise Android's toasts).
 */
@Composable
private fun aboutPreferences(): List<Preference> {
    val checker = remember { Injekt.get<DesktopAppUpdateChecker>() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    val unknownError = stringResource(MR.strings.unknown_error)
    val config = checker.config
    // The commit count only when the preview build has one (a build without it would show "r0")
    val version = if (config.isPreview && config.commitCount > 0) {
        "${BuildConfig.VERSION_NAME} (r${config.commitCount})"
    } else {
        BuildConfig.VERSION_NAME
    }
    return buildList {
        add(
            Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.app_name),
                subtitle = stringResource(MR.strings.version) + " " + version + " (desktop, " +
                    System.getProperty("os.name") + " " + System.getProperty("os.arch") + ", Java " +
                    System.getProperty("java.version") + ")",
            ),
        )
        if (checker.isEnabled) {
            add(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.check_for_updates),
                    widget = {
                        if (checking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp).testTag("about-update-progress"),
                                strokeWidth = 3.dp,
                            )
                        }
                    },
                    onClick = {
                        if (!checking) {
                            checking = true
                            scope.launch {
                                try {
                                    when (val result = checker.check(forceCheck = true)) {
                                        is DesktopAppUpdateChecker.Check.NewUpdate -> Unit // the update dialog shows it
                                        DesktopAppUpdateChecker.Check.NoNewUpdate,
                                        // A forced check is never throttled
                                        DesktopAppUpdateChecker.Check.Throttled,
                                        DesktopAppUpdateChecker.Check.Disabled,
                                        -> context.toast(MR.strings.update_check_no_new_updates)
                                        is DesktopAppUpdateChecker.Check.Failed -> context.toast(
                                            result.error.message ?: unknownError,
                                        )
                                    }
                                } finally {
                                    checking = false
                                }
                            }
                        }
                    },
                ),
            )
        }
    }.toImmutableList()
}
