package app.komikku.desktop.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.komikku.desktop.ui.browse.BrowseScaffold
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.util.system.toast
import exh.log.xLogE
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.toLong
import tachiyomi.data.Database
import tachiyomi.domain.source.interactor.GetSourcesWithNonLibraryManga
import tachiyomi.domain.source.model.SourceWithCount
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * KMK: desktop "Clear database" (P12, Settings → Advanced → Data), Android's `ClearDatabaseScreen` with the same model
 * rules and strings: the sources with entries outside the library and their counts, selection (all / inverse), a
 * confirmation with "Keep entries with read chapters", then the same queries (`deleteNonLibraryManga`,
 * `removeResettedHistory`). No source icons on the desktop list.
 */
class ClearDatabaseScreen : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel { ClearDatabaseScreenModel() }
        val state by model.state.collectAsState()
        val scope = rememberCoroutineScope()
        val ready = state as? ClearDatabaseScreenModel.State.Ready

        BrowseScaffold(
            title = stringResource(MR.strings.pref_clear_database),
            onBack = navigator::pop,
            actions = {
                if (ready != null && ready.items.isNotEmpty()) {
                    IconButton(onClick = model::selectAll, modifier = Modifier.testTag("clear-database-select-all")) {
                        Icon(Icons.Outlined.SelectAll, contentDescription = stringResource(MR.strings.action_select_all))
                    }
                    IconButton(onClick = model::invertSelection) {
                        Icon(Icons.Outlined.FlipToBack, contentDescription = stringResource(MR.strings.action_select_inverse))
                    }
                }
            },
        ) {
            when {
                ready == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                ready.items.isEmpty() -> Box(Modifier.fillMaxSize().testTag("clear-database-empty"), contentAlignment = Alignment.Center) {
                    Text(stringResource(MR.strings.database_clean))
                }
                else -> Column(Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.weight(1f).testTag("clear-database")) {
                        items(ready.items, key = { it.id }) { item ->
                            SourceRow(item, selected = item.id in ready.selection) { model.toggleSelection(item.id) }
                        }
                    }
                    Button(
                        onClick = model::showConfirmation,
                        enabled = ready.selection.isNotEmpty() && !ready.isClearing,
                        modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("clear-database-delete"),
                    ) {
                        Text(stringResource(MR.strings.action_delete))
                    }
                }
            }
        }

        if (ready != null && ready.showConfirmation) {
            var keepReadManga by remember { mutableStateOf(true) }
            val completed = stringResource(MR.strings.clear_database_completed)
            AlertDialog(
                onDismissRequest = { if (!ready.isClearing) model.hideConfirmation() },
                title = { Text(stringResource(MR.strings.are_you_sure)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(MR.strings.clear_database_text))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(MR.strings.clear_db_exclude_read), modifier = Modifier.weight(1f))
                            Switch(
                                enabled = !ready.isClearing,
                                checked = keepReadManga,
                                onCheckedChange = { keepReadManga = it },
                                modifier = Modifier.testTag("clear-database-keep-read"),
                            )
                        }
                        if (!keepReadManga) {
                            Text(
                                stringResource(MR.strings.clear_database_history_warning),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                try {
                                    if (!model.removeMangaBySourceId(keepReadManga)) return@launch
                                    model.clearSelection()
                                    model.hideConfirmation()
                                    context.toast(completed)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Throwable) {
                                    xLogE("Could not clear the database", e)
                                    model.hideConfirmation()
                                    e.message?.let { context.toast(it) } ?: context.toast(MR.strings.unknown_error)
                                }
                            }
                        },
                        enabled = !ready.isClearing,
                        modifier = Modifier.testTag("clear-database-confirm"),
                    ) {
                        if (ready.isClearing) {
                            CircularProgressIndicator(Modifier.size(20.dp).testTag("clear-database-progress"), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(MR.strings.action_ok))
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = model::hideConfirmation, enabled = !ready.isClearing) {
                        Text(stringResource(MR.strings.action_cancel))
                    }
                },
            )
        }
    }

    @Composable
    private fun SourceRow(item: SourceWithCount, selected: Boolean, onClick: () -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("clear-database-source-${item.id}"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(item.source.visualName, style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(MR.strings.clear_database_source_item_count, item.count), style = MaterialTheme.typography.bodySmall)
            }
            Checkbox(checked = selected, onCheckedChange = { onClick() })
        }
    }
}

/** Android's `ClearDatabaseScreenModel` (private there): the same subscription, selection and queries. */
class ClearDatabaseScreenModel(
    private val getSourcesWithNonLibraryManga: GetSourcesWithNonLibraryManga = Injekt.get(),
    private val database: Database = Injekt.get(),
) : StateScreenModel<ClearDatabaseScreenModel.State>(State.Loading) {

    init {
        screenModelScope.launchIO {
            getSourcesWithNonLibraryManga.subscribe()
                .collectLatest { list ->
                    mutableState.update { old ->
                        val items = list.sortedBy { it.name }
                        when (old) {
                            State.Loading -> State.Ready(items)
                            // Selected sources that have nothing left to clear are no longer selected
                            is State.Ready -> old.copy(items = items, selection = old.selection.filter { id -> items.any { it.id == id } })
                        }
                    }
                }
        }
    }

    /**
     * Deletes the selected sources' entries outside the library on the IO dispatcher, not cancellable once started
     * (Android: `withNonCancellableContext`), with [State.Ready.isClearing] set meanwhile. Returns `false` when nothing
     * was started (not ready, or already clearing).
     */
    suspend fun removeMangaBySourceId(keepReadManga: Boolean): Boolean {
        var selection: List<Long>? = null
        mutableState.update { state ->
            if (state !is State.Ready || state.isClearing) return@update state
            selection = state.selection
            state.copy(isClearing = true)
        }
        val sources = selection ?: return false
        try {
            withContext(NonCancellable + Dispatchers.IO) {
                database.mangasQueries.deleteNonLibraryManga(sources, keepReadManga.toLong())
                database.historyQueries.removeResettedHistory()
            }
        } finally {
            mutableState.update { state -> if (state is State.Ready) state.copy(isClearing = false) else state }
        }
        return true
    }

    fun toggleSelection(sourceId: Long) = mutableState.update { state ->
        if (state !is State.Ready) return@update state
        state.copy(selection = if (sourceId in state.selection) state.selection - sourceId else state.selection + sourceId)
    }

    fun clearSelection() = mutableState.update { state ->
        if (state !is State.Ready) return@update state
        state.copy(selection = emptyList())
    }

    fun selectAll() = mutableState.update { state ->
        if (state !is State.Ready) return@update state
        state.copy(selection = state.items.map { it.id })
    }

    fun invertSelection() = mutableState.update { state ->
        if (state !is State.Ready) return@update state
        state.copy(selection = state.items.map { it.id }.filterNot { it in state.selection })
    }

    fun showConfirmation() = mutableState.update { state ->
        if (state !is State.Ready) return@update state
        state.copy(showConfirmation = true)
    }

    fun hideConfirmation() = mutableState.update { state ->
        if (state !is State.Ready) return@update state
        state.copy(showConfirmation = false)
    }

    sealed interface State {
        data object Loading : State

        data class Ready(
            val items: List<SourceWithCount>,
            val selection: List<Long> = emptyList(),
            val showConfirmation: Boolean = false,
            /** The delete runs (P12 review): the buttons are disabled and a progress indicator shown. */
            val isClearing: Boolean = false,
        ) : State
    }
}
