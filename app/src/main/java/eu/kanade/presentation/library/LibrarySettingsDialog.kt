package eu.kanade.presentation.library

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.util.fastForEach
import eu.kanade.presentation.components.TabbedDialog
import eu.kanade.presentation.components.TabbedDialogPaddings
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.library.LibrarySettingsScreenModel
import kotlinx.coroutines.flow.map
import tachiyomi.core.preference.TriState
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.model.LibraryGroup
import tachiyomi.domain.library.model.LibrarySort
import tachiyomi.domain.library.model.sort
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.presentation.core.components.CheckboxItem
import tachiyomi.presentation.core.components.HeadingItem
import tachiyomi.presentation.core.components.IconItem
import tachiyomi.presentation.core.components.SettingsChipRow
import tachiyomi.presentation.core.components.SliderItem
import tachiyomi.presentation.core.components.SortItem
import tachiyomi.presentation.core.components.TriStateItem
import tachiyomi.presentation.core.util.collectAsState

@Composable
fun LibrarySettingsDialog(
    onDismissRequest: () -> Unit,
    screenModel: LibrarySettingsScreenModel,
    category: Category?,
    // SY -->
    hasCategories: Boolean,
    // SY <--
) {
    TabbedDialog(
        onDismissRequest = onDismissRequest,
        tabTitles = listOf(
            stringResource(R.string.action_filter),
            stringResource(R.string.action_sort),
            stringResource(R.string.action_display),
            // SY -->
            stringResource(R.string.group),
            // SY <--
        ),
    ) { page ->
        Column(
            modifier = Modifier
                .padding(vertical = TabbedDialogPaddings.Vertical)
                .verticalScroll(rememberScrollState()),
        ) {
            when (page) {
                0 -> FilterPage(
                    screenModel = screenModel,
                )
                1 -> SortPage(
                    category = category,
                    screenModel = screenModel,
                )
                2 -> DisplayPage(
                    screenModel = screenModel,
                )
                // SY -->
                3 -> GroupPage(
                    screenModel = screenModel,
                    hasCategories = hasCategories,
                )
                // SY <--
            }
        }
    }
}

@Composable
private fun ColumnScope.FilterPage(
    screenModel: LibrarySettingsScreenModel,
) {
    val filterDownloaded by screenModel.libraryPreferences.filterDownloaded().collectAsState()
    val downloadedOnly by screenModel.preferences.downloadedOnly().collectAsState()
    TriStateItem(
        label = stringResource(R.string.label_downloaded),
        state = if (downloadedOnly) {
            TriState.ENABLED_IS
        } else {
            filterDownloaded
        },
        enabled = !downloadedOnly,
        onClick = { screenModel.toggleFilter(LibraryPreferences::filterDownloaded) },
    )
    val filterUnread by screenModel.libraryPreferences.filterUnread().collectAsState()
    TriStateItem(
        label = stringResource(R.string.action_filter_unread),
        state = filterUnread,
        onClick = { screenModel.toggleFilter(LibraryPreferences::filterUnread) },
    )
    val filterStarted by screenModel.libraryPreferences.filterStarted().collectAsState()
    TriStateItem(
        label = stringResource(R.string.label_started),
        state = filterStarted,
        onClick = { screenModel.toggleFilter(LibraryPreferences::filterStarted) },
    )
    val filterBookmarked by screenModel.libraryPreferences.filterBookmarked().collectAsState()
    TriStateItem(
        label = stringResource(R.string.action_filter_bookmarked),
        state = filterBookmarked,
        onClick = { screenModel.toggleFilter(LibraryPreferences::filterBookmarked) },
    )
    val filterCompleted by screenModel.libraryPreferences.filterCompleted().collectAsState()
    TriStateItem(
        label = stringResource(R.string.completed),
        state = filterCompleted,
        onClick = { screenModel.toggleFilter(LibraryPreferences::filterCompleted) },
    )
    // SY -->
    val filterLewd by screenModel.libraryPreferences.filterLewd().collectAsState()
    TriStateItem(
        label = stringResource(R.string.lewd),
        state = filterLewd,
        onClick = { screenModel.toggleFilter(LibraryPreferences::filterLewd) },
    )
    // SY <--

    val trackServices = remember { screenModel.trackServices }
    when (trackServices.size) {
        0 -> {
            // No trackers
        }
        1 -> {
            val service = trackServices[0]
            val filterTracker by screenModel.libraryPreferences.filterTracking(service.id.toInt()).collectAsState()
            TriStateItem(
                label = stringResource(R.string.action_filter_tracked),
                state = filterTracker,
                onClick = { screenModel.toggleTracker(service.id.toInt()) },
            )
        }
        else -> {
            HeadingItem(R.string.action_filter_tracked)
            trackServices.map { service ->
                val filterTracker by screenModel.libraryPreferences.filterTracking(service.id.toInt()).collectAsState()
                TriStateItem(
                    label = stringResource(service.nameRes()),
                    state = filterTracker,
                    onClick = { screenModel.toggleTracker(service.id.toInt()) },
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.SortPage(
    category: Category?,
    screenModel: LibrarySettingsScreenModel,
) {
    // SY -->
    val globalSortMode by screenModel.libraryPreferences.sortingMode().collectAsState()
    val sortingMode = if (screenModel.grouping == LibraryGroup.BY_DEFAULT) {
        category.sort.type
    } else {
        globalSortMode.type
    }
    val sortDescending = if (screenModel.grouping == LibraryGroup.BY_DEFAULT) {
        category.sort.isAscending
    } else {
        globalSortMode.isAscending
    }.not()
    val hasSortTags by remember {
        screenModel.libraryPreferences.sortTagsForLibrary().changes()
            .map { it.isNotEmpty() }
    }.collectAsState(initial = screenModel.libraryPreferences.sortTagsForLibrary().get().isNotEmpty())
    // SY <--

    listOfNotNull(
        R.string.action_sort_alpha to LibrarySort.Type.Alphabetical,
        R.string.action_sort_total to LibrarySort.Type.TotalChapters,
        R.string.action_sort_last_read to LibrarySort.Type.LastRead,
        R.string.action_sort_last_manga_update to LibrarySort.Type.LastUpdate,
        R.string.action_sort_unread_count to LibrarySort.Type.UnreadCount,
        R.string.action_sort_latest_chapter to LibrarySort.Type.LatestChapter,
        R.string.action_sort_chapter_fetch_date to LibrarySort.Type.ChapterFetchDate,
        R.string.action_sort_date_added to LibrarySort.Type.DateAdded,
        if (hasSortTags) {
            R.string.tag_sorting to LibrarySort.Type.TagList
        } else {
            null
        },
    ).map { (titleRes, mode) ->
        SortItem(
            label = stringResource(titleRes),
            sortDescending = sortDescending.takeIf { sortingMode == mode },
            onClick = {
                val isTogglingDirection = sortingMode == mode
                val direction = when {
                    isTogglingDirection -> if (sortDescending) LibrarySort.Direction.Ascending else LibrarySort.Direction.Descending
                    else -> if (sortDescending) LibrarySort.Direction.Descending else LibrarySort.Direction.Ascending
                }
                screenModel.setSort(category, mode, direction)
            },
        )
    }
}

private val displayModes = listOf(
    R.string.action_display_grid to LibraryDisplayMode.CompactGrid,
    R.string.action_display_comfortable_grid to LibraryDisplayMode.ComfortableGrid,
    R.string.action_display_cover_only_grid to LibraryDisplayMode.CoverOnlyGrid,
    R.string.action_display_list to LibraryDisplayMode.List,
)

@Composable
private fun ColumnScope.DisplayPage(
    screenModel: LibrarySettingsScreenModel,
) {
    val displayMode by screenModel.libraryPreferences.displayMode().collectAsState()
    SettingsChipRow(R.string.action_display_mode) {
        displayModes.map { (titleRes, mode) ->
            FilterChip(
                selected = displayMode == mode,
                onClick = { screenModel.setDisplayMode(mode) },
                label = { Text(stringResource(titleRes)) },
            )
        }
    }

    if (displayMode != LibraryDisplayMode.List) {
        val configuration = LocalConfiguration.current
        val columnPreference = remember {
            if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                screenModel.libraryPreferences.landscapeColumns()
            } else {
                screenModel.libraryPreferences.portraitColumns()
            }
        }

        val columns by columnPreference.collectAsState()
        SliderItem(
            label = stringResource(R.string.pref_library_columns),
            max = 10,
            value = columns,
            valueText = if (columns > 0) {
                stringResource(R.string.pref_library_columns_per_row, columns)
            } else {
                stringResource(R.string.label_default)
            },
            onChange = columnPreference::set,
        )
    }

    HeadingItem(R.string.overlay_header)
    CheckboxItem(
        label = stringResource(R.string.action_display_download_badge),
        pref = screenModel.libraryPreferences.downloadBadge(),
    )
    CheckboxItem(
        label = stringResource(R.string.action_display_local_badge),
        pref = screenModel.libraryPreferences.localBadge(),
    )
    CheckboxItem(
        label = stringResource(R.string.action_display_language_badge),
        pref = screenModel.libraryPreferences.languageBadge(),
    )
    CheckboxItem(
        label = stringResource(R.string.action_display_show_continue_reading_button),
        pref = screenModel.libraryPreferences.showContinueReadingButton(),
    )

    HeadingItem(R.string.tabs_header)
    CheckboxItem(
        label = stringResource(R.string.action_display_show_tabs),
        pref = screenModel.libraryPreferences.categoryTabs(),
    )
    CheckboxItem(
        label = stringResource(R.string.action_display_show_number_of_items),
        pref = screenModel.libraryPreferences.categoryNumberOfItems(),
    )
}

data class GroupMode(
    val int: Int,
    val nameRes: Int,
    val drawableRes: Int,
)

private fun groupTypeDrawableRes(type: Int): Int {
    return when (type) {
        LibraryGroup.BY_STATUS -> R.drawable.ic_progress_clock_24dp
        LibraryGroup.BY_TRACK_STATUS -> R.drawable.ic_sync_24dp
        LibraryGroup.BY_SOURCE -> R.drawable.ic_browse_filled_24dp
        LibraryGroup.UNGROUPED -> R.drawable.ic_ungroup_24dp
        else -> R.drawable.ic_label_24dp
    }
}

@Composable
private fun ColumnScope.GroupPage(
    screenModel: LibrarySettingsScreenModel,
    hasCategories: Boolean,
) {
    val groups = remember(hasCategories, screenModel.trackServices) {
        buildList {
            add(LibraryGroup.BY_DEFAULT)
            add(LibraryGroup.BY_SOURCE)
            add(LibraryGroup.BY_STATUS)
            if (screenModel.trackServices.isNotEmpty()) {
                add(LibraryGroup.BY_TRACK_STATUS)
            }
            if (hasCategories) {
                add(LibraryGroup.UNGROUPED)
            }
        }.map {
            GroupMode(
                it,
                LibraryGroup.groupTypeStringRes(it, hasCategories),
                groupTypeDrawableRes(it),
            )
        }
    }

    groups.fastForEach {
        IconItem(
            label = stringResource(it.nameRes),
            icon = painterResource(it.drawableRes),
            selected = it.int == screenModel.grouping,
            onClick = {
                screenModel.setGrouping(it.int)
            },
        )
    }
}
