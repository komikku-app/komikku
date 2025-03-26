package eu.kanade.presentation.updates

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.Panorama
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastAny
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.manga.components.ChapterDownloadAction
import eu.kanade.presentation.manga.components.MangaBottomActionMenu
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.ui.updates.UpdatesItem
import eu.kanade.tachiyomi.ui.updates.UpdatesScreenModel
import eu.kanade.tachiyomi.ui.updates.groupByDateAndManga
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import java.time.LocalDate
import kotlin.time.Duration.Companion.seconds

@Composable
fun UpdateScreen(
    state: UpdatesScreenModel.State,
    snackbarHostState: SnackbarHostState,
    lastUpdated: Long,
    // SY -->
    preserveReadingPosition: Boolean,
    // SY <--
    onClickCover: (UpdatesItem) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,
    onCalendarClicked: () -> Unit,
    onUpdateLibrary: () -> Boolean,
    onDownloadChapter: (List<UpdatesItem>, ChapterDownloadAction) -> Unit,
    onMultiBookmarkClicked: (List<UpdatesItem>, bookmark: Boolean) -> Unit,
    onMultiMarkAsReadClicked: (List<UpdatesItem>, read: Boolean) -> Unit,
    onMultiDeleteClicked: (List<UpdatesItem>) -> Unit,
    onUpdateSelected: (UpdatesItem, Boolean, Boolean, Boolean) -> Unit,
    onOpenChapter: (UpdatesItem) -> Unit,
    // KMK -->
    collapseToggle: (key: String) -> Unit,
    // KMK <--
) {
    // KMK -->
    val usePanoramaCover = remember { mutableStateOf(false) }
    // KMK <--
    BackHandler(enabled = state.selectionMode, onBack = { onSelectAll(false) })

    Scaffold(
        topBar = { scrollBehavior ->
            UpdatesAppBar(
                onCalendarClicked = { onCalendarClicked() },
                onUpdateLibrary = { onUpdateLibrary() },
                actionModeCounter = state.selected.size,
                onSelectAll = { onSelectAll(true) },
                onInvertSelection = { onInvertSelection() },
                onCancelActionMode = { onSelectAll(false) },
                scrollBehavior = scrollBehavior,
                // KMK -->
                usePanoramaCover = usePanoramaCover.value,
                usePanoramaCoverClick = { usePanoramaCover.value = !usePanoramaCover.value },
                // KMK
            )
        },
        bottomBar = {
            UpdatesBottomBar(
                selected = state.selected,
                onDownloadChapter = onDownloadChapter,
                onMultiBookmarkClicked = onMultiBookmarkClicked,
                onMultiMarkAsReadClicked = onMultiMarkAsReadClicked,
                onMultiDeleteClicked = onMultiDeleteClicked,
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { contentPadding ->
        when {
            state.isLoading -> LoadingScreen(Modifier.padding(contentPadding))
            state.items.isEmpty() -> EmptyScreen(
                stringRes = MR.strings.information_no_recent,
                modifier = Modifier.padding(contentPadding),
            )
            else -> {
                val scope = rememberCoroutineScope()
                var isRefreshing by remember { mutableStateOf(false) }

                PullRefresh(
                    refreshing = isRefreshing,
                    onRefresh = {
                        val started = onUpdateLibrary()
                        if (!started) return@PullRefresh
                        scope.launch {
                            // Fake refresh status but hide it after a second as it's a long running task
                            isRefreshing = true
                            delay(1.seconds)
                            isRefreshing = false
                        }
                    },
                    enabled = !state.selectionMode,
                    indicatorPadding = contentPadding,
                ) {
                    FastScrollLazyColumn(
                        contentPadding = contentPadding,
                    ) {
                        updatesLastUpdatedItem(lastUpdated)

                        updatesUiItems(
                            uiModels = state.getUiModel()
                                // KMK -->
                                .filter {
                                    when (it) {
                                        is UpdatesUiModel.Header, is UpdatesUiModel.Leader -> true
                                        is UpdatesUiModel.Item ->
                                            state.expandedState.contains(it.item.update.groupByDateAndManga())
                                    }
                                },
                            expandedState = state.expandedState,
                            collapseToggle = collapseToggle,
                            usePanoramaCover = usePanoramaCover.value,
                            // KMK <--
                            selectionMode = state.selectionMode,
                            // SY -->
                            preserveReadingPosition = preserveReadingPosition,
                            // SY <--
                            onUpdateSelected = onUpdateSelected,
                            onClickCover = onClickCover,
                            onClickUpdate = onOpenChapter,
                            onDownloadChapter = onDownloadChapter,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdatesAppBar(
    onCalendarClicked: () -> Unit,
    onUpdateLibrary: () -> Unit,
    // For action mode
    actionModeCounter: Int,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onCancelActionMode: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    // KMK -->
    usePanoramaCover: Boolean,
    usePanoramaCoverClick: () -> Unit,
    // KMK <--
    modifier: Modifier = Modifier,
) {
    AppBar(
        modifier = modifier,
        title = stringResource(MR.strings.label_recent_updates),
        actions = {
            AppBarActions(
                persistentListOf(
                    // KMK -->
                    AppBar.Action(
                        title = stringResource(KMR.strings.action_panorama_cover),
                        icon = Icons.Outlined.Panorama,
                        iconTint = MaterialTheme.colorScheme.primary.takeIf { usePanoramaCover },
                        onClick = usePanoramaCoverClick,
                    ),
                    // KMK <--
                    AppBar.Action(
                        title = stringResource(MR.strings.action_view_upcoming),
                        icon = Icons.Outlined.CalendarMonth,
                        onClick = onCalendarClicked,
                    ),
                    AppBar.Action(
                        title = stringResource(MR.strings.action_update_library),
                        icon = Icons.Outlined.Refresh,
                        onClick = onUpdateLibrary,
                    ),
                ),
            )
        },
        actionModeCounter = actionModeCounter,
        onCancelActionMode = onCancelActionMode,
        actionModeActions = {
            AppBarActions(
                persistentListOf(
                    AppBar.Action(
                        title = stringResource(MR.strings.action_select_all),
                        icon = Icons.Outlined.SelectAll,
                        onClick = onSelectAll,
                    ),
                    AppBar.Action(
                        title = stringResource(MR.strings.action_select_inverse),
                        icon = Icons.Outlined.FlipToBack,
                        onClick = onInvertSelection,
                    ),
                ),
            )
        },
        scrollBehavior = scrollBehavior,
    )
}

@Composable
private fun UpdatesBottomBar(
    selected: List<UpdatesItem>,
    onDownloadChapter: (List<UpdatesItem>, ChapterDownloadAction) -> Unit,
    onMultiBookmarkClicked: (List<UpdatesItem>, bookmark: Boolean) -> Unit,
    onMultiMarkAsReadClicked: (List<UpdatesItem>, read: Boolean) -> Unit,
    onMultiDeleteClicked: (List<UpdatesItem>) -> Unit,
) {
    MangaBottomActionMenu(
        visible = selected.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
        onBookmarkClicked = {
            onMultiBookmarkClicked.invoke(selected, true)
        }.takeIf { selected.fastAny { !it.update.bookmark } },
        onRemoveBookmarkClicked = {
            onMultiBookmarkClicked.invoke(selected, false)
        }.takeIf { selected.fastAll { it.update.bookmark } },
        onMarkAsReadClicked = {
            onMultiMarkAsReadClicked(selected, true)
        }.takeIf { selected.fastAny { !it.update.read } },
        onMarkAsUnreadClicked = {
            onMultiMarkAsReadClicked(selected, false)
        }.takeIf { selected.fastAny { it.update.read || it.update.lastPageRead > 0L } },
        onDownloadClicked = {
            onDownloadChapter(selected, ChapterDownloadAction.START)
        }.takeIf {
            selected.fastAny { it.downloadStateProvider() != Download.State.DOWNLOADED }
        },
        onDeleteClicked = {
            onMultiDeleteClicked(selected)
        }.takeIf { selected.fastAny { it.downloadStateProvider() == Download.State.DOWNLOADED } },
    )
}

sealed interface UpdatesUiModel {
    data class Header(val date: LocalDate, val mangaCount: Int) : UpdatesUiModel
    open class Item(open val item: UpdatesItem, open val isExpandable: Boolean = false) : UpdatesUiModel
    // KMK -->
    /** The first [Item] in a group of chapters from same manga */
    data class Leader(override val item: UpdatesItem, override val isExpandable: Boolean) : Item(item)
    // KMK <--
}
