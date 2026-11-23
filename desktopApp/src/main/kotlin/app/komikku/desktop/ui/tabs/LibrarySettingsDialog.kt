package app.komikku.desktop.ui.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.ViewModule
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import eu.kanade.presentation.more.settings.widget.TriStateListDialog
import eu.kanade.tachiyomi.ui.library.LibrarySettingsScreenModel
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.model.LibraryGroup
import tachiyomi.domain.library.model.LibrarySort
import tachiyomi.domain.library.model.sort
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.BaseSortItem
import tachiyomi.presentation.core.components.CheckboxItem
import tachiyomi.presentation.core.components.HeadingItem
import tachiyomi.presentation.core.components.IconItem
import tachiyomi.presentation.core.components.SettingsChipRow
import tachiyomi.presentation.core.components.SliderItem
import tachiyomi.presentation.core.components.SortItem
import tachiyomi.presentation.core.components.TriStateItem
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

/**
 * Desktop counterpart of Android's `LibrarySettingsDialog` (Filter / Sort / Display tabs), driven by the
 * borrowed [LibrarySettingsScreenModel] so every change goes to the same preferences / category flags as on
 * Android (and round-trips through backups), including the SY "Group" tab (P2). Not on desktop: the "continue reading"
 * button, the source / language-icon badges and the category-tabs switch (desktop always shows tabs).
 *
 * @param category the shown category (per-category sort, Android's `categorizedDisplaySettings` rules).
 * @param landscape whether the window is wider than tall (landscape / portrait column preference).
 */
@Composable
fun LibrarySettingsDialog(
    screenModel: LibrarySettingsScreenModel,
    category: Category?,
    categories: List<Category>,
    landscape: Boolean,
    onDismissRequest: () -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.widthIn(max = 560.dp).heightIn(max = 640.dp).testTag("library-settings-dialog"),
        ) {
            var page by remember { mutableIntStateOf(0) }
            Column {
                PrimaryTabRow(selectedTabIndex = page) {
                    listOf(MR.strings.action_filter, MR.strings.action_sort, MR.strings.action_display, SYMR.strings.group)
                        .forEachIndexed { i, title ->
                            Tab(
                                selected = page == i,
                                onClick = { page = i },
                                text = { Text(stringResource(title)) },
                                modifier = Modifier.testTag("library-settings-tab-$i"),
                            )
                        }
                }
                // weight(fill = false): long pages scroll instead of pushing the Close row out of the dialog
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                ) {
                    when (page) {
                        0 -> FilterPage(screenModel, categories)
                        1 -> SortPage(screenModel, category)
                        2 -> DisplayPage(screenModel, landscape)
                        else -> GroupPage(screenModel, hasCategories = categories.any { !it.isSystemCategory })
                    }
                }
                Row(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismissRequest) { Text(stringResource(MR.strings.action_close)) }
                }
            }
        }
    }
}

@Composable
private fun FilterPage(screenModel: LibrarySettingsScreenModel, categories: List<Category>) {
    val prefs = screenModel.libraryPreferences
    val downloadedOnly by screenModel.preferences.downloadedOnly().collectAsState()
    val filterDownloaded by prefs.filterDownloaded().collectAsState()
    TriStateItem(
        label = stringResource(MR.strings.label_downloaded),
        state = if (downloadedOnly) TriState.ENABLED_IS else filterDownloaded,
        enabled = !downloadedOnly,
        onClick = { screenModel.toggleFilter(LibraryPreferences::filterDownloaded) },
    )
    FilterItem(screenModel, stringResource(MR.strings.action_filter_unread), LibraryPreferences::filterUnread)
    FilterItem(screenModel, stringResource(MR.strings.label_started), LibraryPreferences::filterStarted)
    FilterItem(screenModel, stringResource(MR.strings.action_filter_bookmarked), LibraryPreferences::filterBookmarked)
    FilterItem(screenModel, stringResource(MR.strings.completed), LibraryPreferences::filterCompleted)
    val restrictions by prefs.autoUpdateMangaRestrictions().collectAsState()
    if (LibraryPreferences.MANGA_OUTSIDE_RELEASE_PERIOD in restrictions) {
        FilterItem(screenModel, stringResource(MR.strings.action_filter_interval_custom), LibraryPreferences::filterIntervalCustom)
    }
    FilterItem(screenModel, stringResource(SYMR.strings.lewd), LibraryPreferences::filterLewd)
    CategoriesFilter(prefs, categories)

    val trackers by screenModel.trackersFlow.collectAsState()
    if (trackers.size > 1) HeadingItem(MR.strings.action_filter_tracked)
    trackers.forEach { tracker ->
        val state by prefs.filterTracking(tracker.id.toInt()).collectAsState()
        TriStateItem(
            label = if (trackers.size == 1) stringResource(MR.strings.action_filter_tracked) else tracker.name,
            state = state,
            onClick = { screenModel.toggleTracker(tracker.id.toInt()) },
        )
    }
}

@Composable
private fun FilterItem(
    screenModel: LibrarySettingsScreenModel,
    label: String,
    preference: (LibraryPreferences) -> Preference<TriState>,
) {
    val state by preference(screenModel.libraryPreferences).collectAsState()
    TriStateItem(label = label, state = state, onClick = { screenModel.toggleFilter(preference) })
}

/** KMK category include / exclude filter (Android `CategoriesFilter`). */
@Composable
private fun CategoriesFilter(prefs: LibraryPreferences, categories: List<Category>) {
    val enabled by prefs.filterCategories().collectAsState()
    val included by prefs.filterCategoriesInclude().collectAsState()
    val excluded by prefs.filterCategoriesExclude().collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    if (showDialog) {
        // Like Android: user categories only (the filter ignores the default category, id 0).
        val userCategories = remember(categories) { categories.filterNot(Category::isSystemCategory) }
        TriStateListDialog(
            title = stringResource(MR.strings.categories),
            message = stringResource(KMR.strings.pref_library_filter_categories_details),
            items = userCategories,
            initialChecked = userCategories.filter { it.id.toString() in included },
            initialInversed = userCategories.filter { it.id.toString() in excluded },
            itemLabel = { it.name },
            onDismissRequest = { showDialog = false },
            onValueChanged = { newIncluded, newExcluded ->
                prefs.filterCategoriesInclude().set(newIncluded.map { it.id.toString() }.toSet())
                prefs.filterCategoriesExclude().set(newExcluded.map { it.id.toString() }.toSet())
                showDialog = false
            },
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            CheckboxItem(label = stringResource(MR.strings.categories), checked = enabled, onClick = { prefs.filterCategories().set(!enabled) })
        }
        TextButton(onClick = { showDialog = true }, modifier = Modifier.padding(end = 16.dp)) {
            Text(stringResource(MR.strings.action_edit))
        }
    }
}

@Composable
private fun SortPage(screenModel: LibrarySettingsScreenModel, category: Category?) {
    val prefs = screenModel.libraryPreferences
    val trackers by screenModel.trackersFlow.collectAsState()
    val grouping by prefs.groupLibraryBy().collectAsState()
    val globalSort by prefs.sortingMode().collectAsState()
    // Android SortPage: the category's sort when grouped by category, else the global sort.
    val current = if (grouping == LibraryGroup.BY_DEFAULT) category.sort else globalSort
    val sortDescending = !current.isAscending
    val hasSortTags by prefs.sortTagsForLibrary().collectAsState()
    val options = listOfNotNull(
        MR.strings.action_sort_alpha to LibrarySort.Type.Alphabetical,
        MR.strings.action_sort_total to LibrarySort.Type.TotalChapters,
        MR.strings.action_sort_last_read to LibrarySort.Type.LastRead,
        MR.strings.action_sort_last_manga_update to LibrarySort.Type.LastUpdate,
        MR.strings.action_sort_unread_count to LibrarySort.Type.UnreadCount,
        MR.strings.action_sort_latest_chapter to LibrarySort.Type.LatestChapter,
        MR.strings.action_sort_chapter_fetch_date to LibrarySort.Type.ChapterFetchDate,
        MR.strings.action_sort_date_added to LibrarySort.Type.DateAdded,
        (MR.strings.action_sort_tracker_score to LibrarySort.Type.TrackerMean).takeIf { trackers.isNotEmpty() },
        (SYMR.strings.tag_sorting to LibrarySort.Type.TagList).takeIf { hasSortTags.isNotEmpty() },
        MR.strings.action_sort_random to LibrarySort.Type.Random,
    )
    options.forEach { (title, mode) ->
        if (mode == LibrarySort.Type.Random) {
            BaseSortItem(
                label = stringResource(title),
                icon = Icons.Default.Refresh.takeIf { current.type == LibrarySort.Type.Random },
                onClick = { screenModel.setSort(category, mode, LibrarySort.Direction.Ascending) },
            )
        } else {
            SortItem(
                label = stringResource(title),
                sortDescending = sortDescending.takeIf { current.type == mode },
                onClick = {
                    // Same as Android: toggle the direction of the current mode, keep it for a new mode.
                    val descending = if (current.type == mode) !sortDescending else sortDescending
                    screenModel.setSort(
                        category,
                        mode,
                        if (descending) LibrarySort.Direction.Descending else LibrarySort.Direction.Ascending,
                    )
                },
            )
        }
    }
}

/** Android's SY `GroupPage`: group the library by category, source, status, tracking status (with a tracker) or not. */
@Composable
private fun GroupPage(screenModel: LibrarySettingsScreenModel, hasCategories: Boolean) {
    val trackers by screenModel.trackersFlow.collectAsState()
    val grouping by screenModel.libraryPreferences.groupLibraryBy().collectAsState()
    val groups = buildList {
        add(LibraryGroup.BY_DEFAULT)
        add(LibraryGroup.BY_SOURCE)
        add(LibraryGroup.BY_STATUS)
        if (trackers.isNotEmpty()) {
            add(LibraryGroup.BY_TRACK_STATUS)
        }
        if (hasCategories || grouping == LibraryGroup.UNGROUPED) {
            add(LibraryGroup.UNGROUPED)
        }
    }
    groups.forEach { group ->
        Box(modifier = Modifier.testTag("library-group-$group")) {
            IconItem(
                label = stringResource(LibraryGroup.groupTypeStringRes(group)),
                icon = rememberVectorPainter(groupTypeIcon(group)),
                selected = group == grouping,
                onClick = { screenModel.setGrouping(group) },
            )
        }
    }
}

/** Material counterparts of Android's group drawables. */
private fun groupTypeIcon(type: Int): ImageVector = when (type) {
    LibraryGroup.BY_STATUS -> Icons.Outlined.Schedule
    LibraryGroup.BY_TRACK_STATUS -> Icons.Outlined.Sync
    LibraryGroup.BY_SOURCE -> Icons.Outlined.Explore
    LibraryGroup.UNGROUPED -> Icons.Outlined.ViewModule
    else -> Icons.AutoMirrored.Outlined.Label
}

private val displayModes = listOf(
    MR.strings.action_display_grid to LibraryDisplayMode.CompactGrid,
    MR.strings.action_display_comfortable_grid to LibraryDisplayMode.ComfortableGrid,
    MR.strings.action_display_list to LibraryDisplayMode.List,
    MR.strings.action_display_cover_only_grid to LibraryDisplayMode.CoverOnlyGrid,
    KMR.strings.action_display_comfortable_grid_panorama to LibraryDisplayMode.ComfortableGridPanorama,
)

@Composable
private fun DisplayPage(screenModel: LibrarySettingsScreenModel, landscape: Boolean) {
    val prefs = screenModel.libraryPreferences
    val displayMode by prefs.displayMode().collectAsState()
    SettingsChipRow(MR.strings.action_display_mode) {
        displayModes.forEach { (title, mode) ->
            FilterChip(
                selected = displayMode == mode,
                onClick = { screenModel.setDisplayMode(mode) },
                label = { Text(stringResource(title)) },
                modifier = Modifier.testTag("display-mode-${mode.serialize()}"),
            )
        }
    }
    if (displayMode != LibraryDisplayMode.List) {
        val columnPreference = remember(landscape) {
            if (landscape) prefs.landscapeColumns() else prefs.portraitColumns()
        }
        val columns by columnPreference.collectAsState()
        SliderItem(
            value = columns,
            valueRange = 0..10,
            label = stringResource(MR.strings.pref_library_columns),
            valueString = if (columns > 0) columns.toString() else stringResource(MR.strings.label_auto),
            onChange = columnPreference::set,
            pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    }
    HeadingItem(MR.strings.overlay_header)
    CheckboxItem(label = stringResource(MR.strings.action_display_download_badge), pref = prefs.downloadBadge())
    CheckboxItem(label = stringResource(MR.strings.action_display_unread_badge), pref = prefs.unreadBadge())
    CheckboxItem(label = stringResource(MR.strings.action_display_local_badge), pref = prefs.localBadge())
    CheckboxItem(label = stringResource(MR.strings.action_display_language_badge), pref = prefs.languageBadge())
    HeadingItem(MR.strings.tabs_header)
    CheckboxItem(label = stringResource(KMR.strings.action_show_hidden_categories), pref = prefs.showHiddenCategories())
    CheckboxItem(label = stringResource(MR.strings.action_display_show_number_of_items), pref = prefs.categoryNumberOfItems())
}
