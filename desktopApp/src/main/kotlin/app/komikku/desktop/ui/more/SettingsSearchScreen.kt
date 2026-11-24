package app.komikku.desktop.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.paddingFromBaseline
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.SettingsSearchPage
import eu.kanade.presentation.more.settings.screen.SettingsSearchResult
import eu.kanade.presentation.more.settings.screen.searchSettings
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.track.TrackerManager
import kotlinx.collections.immutable.toImmutableList
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * P15: Android's settings search (`SettingsSearchScreen`) on desktop: a query field and up to ten matching settings
 * (title or subtitle; enabled items only, info items left out) with their breadcrumbs, found by the shared
 * `searchSettings` (`SettingsSearch.kt`, Android's own search). A result replaces this screen with its settings page,
 * which scrolls to the item and highlights it (categories built from the settings model; the tracking and data and
 * storage screens are opened at their top).
 *
 * Index: the desktop settings in Android's search order (appearance, library, reader, downloads, tracking, browse, data
 * and storage, security (P16), advanced; Android's "About" is not searched either). The tracking and data and storage screens are not
 * built from the settings model, so their index lists their entries' titles.
 */
class SettingsSearchScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        var query by rememberSaveable { mutableStateOf("") }
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(focusRequester) { runCatching { focusRequester.requestFocus() } }
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = navigator::pop) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(MR.strings.action_bar_up_description))
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(MR.strings.action_search_settings)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }, modifier = Modifier.testTag("settings-search-clear")) {
                                Icon(Icons.Outlined.Close, contentDescription = stringResource(MR.strings.action_reset))
                            }
                        }
                    },
                    modifier = Modifier.weight(1f).focusRequester(focusRequester).testTag("settings-search-query"),
                )
            }
            HorizontalDivider()
            SettingsSearchResults(query, navigator) { result ->
                navigator.replace(result.route.screen(result.highlightKey))
            }
        }
    }
}

/** Where a settings search result leads. */
internal sealed interface SettingsSearchTarget {
    /** The page of this target, scrolled to and highlighting the item titled [highlightKey] where it can. */
    fun screen(highlightKey: String): Screen

    data class Category(val category: SettingsCategory) : SettingsSearchTarget {
        override fun screen(highlightKey: String): Screen = SettingsCategoryScreen(category, highlightKey)
    }

    data object Tracking : SettingsSearchTarget {
        override fun screen(highlightKey: String): Screen = TrackingSettingsScreen()
    }

    data object DataStorage : SettingsSearchTarget {
        override fun screen(highlightKey: String): Screen = DataStorageScreen()
    }
}

@Composable
private fun SettingsSearchResults(
    query: String,
    navigator: Navigator,
    onClick: (SettingsSearchResult<SettingsSearchTarget>) -> Unit,
) {
    if (query.isEmpty()) return
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val index = settingsSearchIndex(navigator)
    val results = remember(query, index, isLtr) { searchSettings(index, query, isLtr) }
    if (results.isEmpty()) {
        EmptyScreen(stringResource(MR.strings.no_results_found))
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize().testTag("settings-search-results")) {
        itemsIndexed(results, key = { i, item -> "settings-search-$i-${item.hashCode()}" }) { i, item ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick(item) }
                    .padding(horizontal = 24.dp, vertical = 14.dp)
                    .testTag("settings-search-result-$i"),
            ) {
                Text(
                    text = item.title,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    fontWeight = FontWeight.Normal,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = item.breadcrumbs,
                    modifier = Modifier.paddingFromBaseline(top = 16.dp),
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/** The searched pages, in Android's order (`settingScreens` of its `SettingsSearchScreen`). */
@Composable
internal fun settingsSearchIndex(navigator: Navigator): List<SettingsSearchPage<SettingsSearchTarget>> {
    val categories = listOf(
        SettingsCategory.Appearance,
        SettingsCategory.Library,
        SettingsCategory.Reader,
        SettingsCategory.Downloads,
    ).map { category ->
        SettingsSearchPage<SettingsSearchTarget>(
            stringResource(category.title),
            SettingsSearchTarget.Category(category),
            settingsCategoryPreferences(category, navigator),
        )
    }
    val tracking = SettingsSearchPage<SettingsSearchTarget>(
        stringResource(MR.strings.pref_category_tracking),
        SettingsSearchTarget.Tracking,
        trackingSearchEntries(),
    )
    val browse = SettingsSearchPage<SettingsSearchTarget>(
        stringResource(SettingsCategory.Browse.title),
        SettingsSearchTarget.Category(SettingsCategory.Browse),
        settingsCategoryPreferences(SettingsCategory.Browse, navigator),
    )
    val dataStorage = SettingsSearchPage<SettingsSearchTarget>(
        stringResource(MR.strings.label_data_storage),
        SettingsSearchTarget.DataStorage,
        dataStorageSearchEntries(),
    )
    val security = SettingsSearchPage<SettingsSearchTarget>(
        stringResource(SettingsCategory.Security.title),
        SettingsSearchTarget.Category(SettingsCategory.Security),
        settingsCategoryPreferences(SettingsCategory.Security, navigator),
    )
    val advanced = SettingsSearchPage<SettingsSearchTarget>(
        stringResource(SettingsCategory.Advanced.title),
        SettingsSearchTarget.Category(SettingsCategory.Advanced),
        settingsCategoryPreferences(SettingsCategory.Advanced, navigator),
    )
    return categories + tracking + browse + dataStorage + security + advanced
}

/** The tracking screen's entries for the search: its two switches and one entry per tracker (by name). */
@Composable
private fun trackingSearchEntries(): List<Preference> {
    val trackers = remember { Injekt.get<TrackerManager>().trackers }
    return listOf(
        Preference.PreferenceItem.TextPreference(title = stringResource(MR.strings.pref_auto_update_manga_sync)),
        Preference.PreferenceItem.TextPreference(title = stringResource(MR.strings.pref_auto_update_manga_on_mark_read)),
        Preference.PreferenceGroup(
            title = stringResource(MR.strings.services),
            preferenceItems = trackers.map { Preference.PreferenceItem.TextPreference(title = it.name) }.toImmutableList(),
        ),
    )
}

/** The data and storage screen's entries for the search (its storage location, backup and storage usage rows). */
@Composable
private fun dataStorageSearchEntries(): List<Preference> = listOf(
    Preference.PreferenceItem.TextPreference(title = stringResource(MR.strings.pref_storage_location)),
    Preference.PreferenceGroup(
        title = stringResource(MR.strings.label_backup),
        preferenceItems = listOf(
            Preference.PreferenceItem.TextPreference(title = stringResource(MR.strings.pref_create_backup)),
            Preference.PreferenceItem.TextPreference(title = stringResource(MR.strings.pref_restore_backup)),
            Preference.PreferenceItem.TextPreference(title = stringResource(MR.strings.pref_backup_interval)),
        ).toImmutableList(),
    ),
    Preference.PreferenceGroup(
        title = stringResource(MR.strings.pref_storage_usage),
        preferenceItems = listOf(
            Preference.PreferenceItem.TextPreference(title = stringResource(MR.strings.pref_clear_chapter_cache)),
            Preference.PreferenceItem.TextPreference(title = stringResource(MR.strings.pref_auto_clear_chapter_cache)),
        ).toImmutableList(),
    ),
)
