package app.komikku.desktop.ui.tabs

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.komikku.desktop.ui.browse.GlobalSearchScreen
import app.komikku.desktop.ui.components.SingleLineText
import app.komikku.desktop.ui.manga.MangaDetailsScreen
import app.komikku.desktop.ui.migration.MigrationConfigScreen
import app.komikku.desktop.ui.more.CategoriesScreen
import app.komikku.desktop.ui.reader.ReaderLauncher
import app.komikku.shared.library.LibraryUpdateRunner
import app.komikku.shared.navigation.AppNavigatorHost
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.library.DeleteLibraryMangaDialog
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.ui.library.LibraryItem
import eu.kanade.tachiyomi.ui.library.LibrarySettingsScreenModel
import eu.kanade.tachiyomi.util.system.toast
import exh.log.xLogE
import exh.source.MERGED_SOURCE_ID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import tachiyomi.domain.library.model.LibraryGroup
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.collectAsState
import tachiyomi.source.local.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** Library destination: categories as tabs, a cover grid per category; manga open their details. */
data object LibraryTab : Tab {

    override val options: TabOptions
        @Composable get() = TabOptions(
            index = 0u,
            title = stringResource(MR.strings.label_library),
            icon = rememberVectorPainter(Icons.Outlined.CollectionsBookmark),
        )

    @Composable
    override fun Content() {
        AppNavigatorHost(screen = LibraryHomeScreen)
    }
}

/** The library grid; manga open the details screen in the library tab's own navigator. */
data object LibraryHomeScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { LibraryScreenModel() }
        val settingsModel = rememberScreenModel { LibrarySettingsScreenModel() }
        val state by screenModel.state.collectAsState()
        when {
            state.isLoading -> LoadingScreen()
            state.query.isEmpty() && !state.hasActiveFilters && state.isLibraryEmpty ->
                EmptyScreen(stringRes = MR.strings.information_empty_library)
            else -> LibraryPages(
                state = state,
                screenModel = screenModel,
                settingsModel = settingsModel,
                onOpen = { navigator.push(MangaDetailsScreen(it)) },
            )
        }
        // Android's LibraryTab dialogs (borrowed dialogs, same actions)
        when (val dialog = state.dialog) {
            is LibraryScreenModel.Dialog.ChangeCategory -> ChangeCategoryDialog(
                initialSelection = dialog.initialSelection,
                onDismissRequest = screenModel::closeDialog,
                onEditCategories = { navigator.push(CategoriesScreen()) },
                onConfirm = { include, exclude ->
                    screenModel.clearSelection()
                    screenModel.setMangaCategories(dialog.manga, include, exclude)
                },
            )
            is LibraryScreenModel.Dialog.DeleteManga -> DeleteLibraryMangaDialog(
                containsLocalManga = dialog.manga.any(Manga::isLocal),
                onDismissRequest = screenModel::closeDialog,
                onConfirm = { deleteManga, deleteChapter ->
                    screenModel.removeMangas(dialog.manga, deleteManga, deleteChapter)
                    screenModel.clearSelection()
                },
            )
            null -> Unit
        }
    }
}

@Composable
private fun LibraryPages(
    state: LibraryScreenModel.State,
    screenModel: LibraryScreenModel,
    settingsModel: LibrarySettingsScreenModel,
    onOpen: (Long) -> Unit,
) {
    val onQueryChange = screenModel::search
    val onSelectCategory = screenModel::selectCategory
    val pages = state.pages
    val index = state.selectedIndex
    val runner = remember { Injekt.get<LibraryUpdateRunner>() }
    val context = remember { Injekt.get<Application>() }
    val navigator = LocalNavigator.currentOrThrow
    val libraryPreferences = remember { Injekt.get<LibraryPreferences>() }
    val showCounts by libraryPreferences.categoryNumberOfItems().collectAsState()
    val displayMode by libraryPreferences.displayMode().collectAsState()
    val showContinueReading by libraryPreferences.showContinueReadingButton().collectAsState()
    val readerLauncher = remember { Injekt.get<ReaderLauncher>() }
    val scope = rememberCoroutineScope()
    var showSettings by remember { mutableStateOf(false) }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        if (showSettings) {
            LibrarySettingsDialog(
                screenModel = settingsModel,
                category = pages.getOrNull(index)?.category,
                categories = state.categories,
                landscape = landscape,
                onDismissRequest = { showSettings = false },
            )
        }
        val columns by remember(landscape) {
            if (landscape) libraryPreferences.landscapeColumns() else libraryPreferences.portraitColumns()
        }.collectAsState()
        Column(modifier = Modifier.fillMaxSize()) {
            if (state.selectionMode) {
                LibrarySelectionToolbar(
                    count = state.selection.size,
                    onClickUnselectAll = screenModel::clearSelection,
                    onClickSelectAll = screenModel::selectAll,
                    onClickInvertSelection = screenModel::invertSelection,
                )
            } else {
                // Library refresh (6L.2): the shown category (or group), or the whole library; search (8.5)
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                ) {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = onQueryChange,
                        placeholder = { SingleLineText(stringResource(MR.strings.action_search_hint)) },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("library-search"),
                    )
                    // Android's "Open random entry" (P12): a random entry of the shown page
                    IconButton(
                        onClick = {
                            val item = screenModel.getRandomLibraryItemForCurrentCategory()
                            if (item != null) onOpen(item.id) else context.toast(MR.strings.information_no_entries_found)
                        },
                        modifier = Modifier.testTag("library-random"),
                    ) {
                        Icon(Icons.Outlined.Shuffle, contentDescription = stringResource(MR.strings.action_open_random_manga))
                    }
                    // Filter / sort / display settings (8.5); highlighted while a filter is active, like Android
                    IconButton(onClick = { showSettings = true }, modifier = Modifier.testTag("library-settings")) {
                        Icon(
                            Icons.Outlined.FilterList,
                            contentDescription = stringResource(MR.strings.action_filter),
                            tint = if (state.hasActiveFilters) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        )
                    }
                    TextButton(
                        onClick = {
                            // A group that is not a category (source, status, …): Android SY's grouped update (the
                            // group's entries or the whole library by `groupLibraryUpdateType`, with the restrictions)
                            val page = pages[index]
                            val started = runner.start(
                                if (state.groupType == LibraryGroup.BY_DEFAULT) {
                                    LibraryUpdateRunner.Target.Category(page.category.id)
                                } else {
                                    LibraryUpdateRunner.Target.Group(state.groupType, page.items.map { it.id }.toSet())
                                },
                            )
                            if (!started) context.toast(MR.strings.update_already_running)
                        },
                        modifier = Modifier.testTag("update-category"),
                    ) { SingleLineText(stringResource(MR.strings.action_update_category)) }
                    TextButton(
                        onClick = {
                            if (!runner.start(LibraryUpdateRunner.Target.All)) context.toast(MR.strings.update_already_running)
                        },
                        modifier = Modifier.testTag("update-library-all"),
                    ) { SingleLineText(stringResource(MR.strings.action_update_library)) }
                }
            }
            LibraryUpdateBanner(runner)
            if (pages.size > 1) {
                PrimaryScrollableTabRow(selectedTabIndex = index, edgePadding = 0.dp) {
                    pages.forEachIndexed { i, page ->
                        Tab(
                            selected = i == index,
                            onClick = { onSelectCategory(page.category.id) },
                            text = {
                                Text(
                                    // Android shows counts when the preference is on or a search is active.
                                    text = page.title(state.groupType) +
                                        if (showCounts || state.query.isNotEmpty()) " (${page.items.size})" else "",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            modifier = Modifier.testTag("library-tab-${page.category.id}"),
                        )
                    }
                }
            }
            // P14: Android's "Search globally" for the library search's query (its first grid item while searching)
            if (state.query.isNotEmpty()) {
                TextButton(
                    onClick = { navigator.push(GlobalSearchScreen(state.query)) },
                    modifier = Modifier.padding(horizontal = 8.dp).testTag("library-global-search"),
                ) { Text(stringResource(MR.strings.action_global_search_query, state.query)) }
            }
            val page = pages[index]
            if (page.items.isEmpty()) {
                // Android's LibraryPagerEmptyScreen; weighted like the grid so the selection bar below stays shown
                EmptyScreen(
                    stringRes = when {
                        state.query.isNotEmpty() -> MR.strings.no_results_found
                        state.hasActiveFilters -> MR.strings.error_no_match
                        else -> MR.strings.information_no_manga_category
                    },
                    modifier = Modifier.weight(1f),
                )
            } else {
                LibraryItems(
                    items = page.items,
                    displayMode = displayMode,
                    columns = columns,
                    selection = state.selection,
                    onClick = { id, modifiers ->
                        // Android: a click selects while selecting, a long press selects a range. Desktop also:
                        // Shift-click selects a range, Ctrl / Cmd-click toggles one entry.
                        when {
                            modifiers.shift -> screenModel.toggleRangeSelection(page.category, id)
                            modifiers.toggle || state.selectionMode -> screenModel.toggleSelection(page.category, id)
                            else -> onOpen(id)
                        }
                    },
                    onLongClick = { screenModel.toggleRangeSelection(page.category, it) },
                    modifier = Modifier.weight(1f),
                    // Android's "continue reading" button (P12): the next unread chapter in the reader
                    onContinueReading = { item: LibraryItem ->
                        scope.launch {
                            // A failure (database, reader) is logged and shown, never left to the composition's scope
                            try {
                                val chapter = screenModel.getNextUnreadChapter(item.libraryManga.manga)
                                if (chapter != null) {
                                    readerLauncher.open(chapter.mangaId, chapter.id)
                                } else {
                                    context.toast(MR.strings.no_next_chapter)
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Throwable) {
                                xLogE("Could not open the next unread chapter", e)
                                e.message?.let { context.toast(it) } ?: context.toast(MR.strings.unknown_error)
                            }
                        }
                        Unit
                    }.takeIf { showContinueReading },
                )
            }
            if (state.selectionMode) {
                LibrarySelectionBar(
                    selectedManga = state.selectedManga,
                    onChangeCategoryClicked = screenModel::openChangeCategoryDialog,
                    onMarkAsReadClicked = { screenModel.markReadSelection(true) },
                    onMarkAsUnreadClicked = { screenModel.markReadSelection(false) },
                    onDownloadClicked = screenModel::performDownloadAction,
                    onUpdateClicked = {
                        // An explicit selection: Android's targeted update (no smart-update restrictions). While an
                        // update runs, keep the selection and say so (Android's library snackbar message).
                        if (runner.start(LibraryUpdateRunner.Target.Entries(state.selection))) {
                            screenModel.clearSelection()
                        } else {
                            context.toast(MR.strings.update_already_running)
                        }
                    },
                    onDeleteClicked = screenModel::openDeleteMangaDialog,
                    // P19: Android's — merged entries left out, nothing left → "No valid entry selected"
                    onMigrateClicked = {
                        val selection = state.selectedManga.filterNot { it.source == MERGED_SOURCE_ID }.map { it.id }
                        screenModel.clearSelection()
                        if (selection.isEmpty()) {
                            context.toast(SYMR.strings.no_valid_entry)
                        } else {
                            navigator.push(MigrationConfigScreen(selection))
                        }
                    },
                )
            }
        }
    }
}

/**
 * Page title: the category name (Android's `visualName`: "Default" for the system category); a group that is not a
 * category always shows its name (Android shows "Default" for a group with id 0, e.g. the local source).
 */
@Composable
private fun LibraryPage.title(groupType: Int): String =
    if (groupType == LibraryGroup.BY_DEFAULT && category.isSystemCategory) {
        stringResource(MR.strings.label_default)
    } else {
        category.name
    }
