package app.komikku.desktop.ui.download

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardDoubleArrowDown
import androidx.compose.material.icons.outlined.KeyboardDoubleArrowUp
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.komikku.desktop.ui.browse.BrowseScaffold
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.model.Download
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Desktop download queue (Android `DownloadQueueScreen`): the borrowed `DownloadManager`'s queue with
 * per-chapter status and page progress; start / pause, cancel one, move to top / bottom, cancel all.
 */
class DownloadQueueScreenModel(
    private val downloadManager: DownloadManager = Injekt.get(),
) : StateScreenModel<DownloadQueueScreenModel.State>(State()) {

    @Immutable
    data class Item(
        val download: Download,
        val status: Download.State,
        val downloadedPages: Int,
        val totalPages: Int?,
        val progress: Int,
    )

    @Immutable
    data class State(val items: List<Item> = emptyList(), val isRunning: Boolean = false)

    init {
        screenModelScope.launch {
            val ticks = merge(downloadManager.statusFlow().map { }, downloadManager.progressFlow().map { })
                .sample(100)
                .onStart { emit(Unit) }
            combine(downloadManager.queueState, downloadManager.isDownloaderRunning, ticks) { queue, running, _ ->
                State(
                    items = queue.map { Item(it, it.status, it.downloadedImages, it.pages?.size, it.progress) },
                    isRunning = running,
                )
            }.collectLatest { new -> mutableState.update { new } }
        }
    }

    fun start() = downloadManager.startDownloads()

    fun pause() = downloadManager.pauseDownloads()

    fun clear() = downloadManager.clearQueue()

    fun cancel(download: Download) = downloadManager.cancelQueuedDownloads(listOf(download))

    fun moveToTop(download: Download) = reorder(download, top = true)

    fun moveToBottom(download: Download) = reorder(download, top = false)

    private fun reorder(download: Download, top: Boolean) {
        val queue = downloadManager.queueState.value.toMutableList()
        if (!queue.remove(download)) return
        if (top) queue.add(0, download) else queue.add(download)
        downloadManager.reorderQueue(queue)
    }
}

class DownloadQueueScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel { DownloadQueueScreenModel() }
        val state by model.state.collectAsState()
        BrowseScaffold(
            title = stringResource(MR.strings.label_download_queue),
            onBack = navigator::pop,
            actions = {
                if (state.items.isNotEmpty()) {
                    if (state.isRunning) {
                        IconButton(onClick = model::pause, modifier = Modifier.testTag("queue-pause")) {
                            Icon(Icons.Outlined.Pause, contentDescription = stringResource(MR.strings.action_pause))
                        }
                    } else {
                        IconButton(onClick = model::start, modifier = Modifier.testTag("queue-start")) {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = stringResource(MR.strings.action_resume))
                        }
                    }
                    IconButton(onClick = model::clear, modifier = Modifier.testTag("queue-clear")) {
                        Icon(Icons.Outlined.ClearAll, contentDescription = stringResource(MR.strings.action_cancel_all))
                    }
                }
            },
        ) {
            if (state.items.isEmpty()) {
                EmptyScreen(stringRes = MR.strings.information_no_downloads)
                return@BrowseScaffold
            }
            LazyColumn(Modifier.fillMaxSize().testTag("download-queue"), contentPadding = PaddingValues(vertical = 8.dp)) {
                itemsIndexed(state.items, key = { _, item -> item.download.chapter.id }) { index, item ->
                    DownloadRow(item, index, state.items.size, model)
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(item: DownloadQueueScreenModel.Item, index: Int, count: Int, model: DownloadQueueScreenModel) {
    val download = item.download
    ListItem(
        modifier = Modifier.testTag("download-${download.chapter.id}"),
        overlineContent = { Text(download.manga.title) },
        headlineContent = { Text(download.chapter.name) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val total = item.totalPages
                val status = when (item.status) {
                    Download.State.ERROR -> stringResource(MR.strings.chapter_error)
                    Download.State.DOWNLOADING -> total?.let {
                        stringResource(MR.strings.chapter_downloading_progress, item.downloadedPages, it)
                    }
                    else -> null
                }
                status?.let {
                    Text(
                        it,
                        color = if (item.status == Download.State.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (item.status == Download.State.DOWNLOADING) {
                    if (total == null) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(progress = { item.progress / 100f }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        trailingContent = {
            Box {
                Row {
                    IconButton(onClick = { model.moveToTop(download) }, enabled = index > 0) {
                        Icon(Icons.Outlined.KeyboardDoubleArrowUp, contentDescription = stringResource(MR.strings.action_move_to_top))
                    }
                    IconButton(onClick = { model.moveToBottom(download) }, enabled = index < count - 1) {
                        Icon(Icons.Outlined.KeyboardDoubleArrowDown, contentDescription = stringResource(MR.strings.action_move_to_bottom))
                    }
                    IconButton(onClick = { model.cancel(download) }, modifier = Modifier.testTag("download-cancel-${download.chapter.id}")) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(MR.strings.action_cancel))
                    }
                }
            }
        },
    )
}
