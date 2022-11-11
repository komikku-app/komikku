package eu.kanade.tachiyomi.ui.more

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.coroutineScope
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.core.prefs.asState
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.more.MoreScreen
import eu.kanade.presentation.util.LocalRouter
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadService
import eu.kanade.tachiyomi.ui.base.controller.pushController
import eu.kanade.tachiyomi.ui.category.CategoryController
import eu.kanade.tachiyomi.ui.download.DownloadController
import eu.kanade.tachiyomi.ui.history.HistoryController
import eu.kanade.tachiyomi.ui.setting.SettingsMainController
import eu.kanade.tachiyomi.ui.updates.UpdatesController
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.system.isInstalledFromFDroid
import exh.ui.batchadd.BatchAddController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object MoreScreen : Screen {
    @Composable
    override fun Content() {
        val router = LocalRouter.currentOrThrow
        val context = LocalContext.current
        val screenModel = rememberScreenModel { MoreScreenModel() }
        val downloadQueueState by screenModel.downloadQueueState.collectAsState()
        MoreScreen(
            downloadQueueStateProvider = { downloadQueueState },
            downloadedOnly = screenModel.downloadedOnly,
            onDownloadedOnlyChange = { screenModel.downloadedOnly = it },
            incognitoMode = screenModel.incognitoMode,
            onIncognitoModeChange = { screenModel.incognitoMode = it },
            isFDroid = context.isInstalledFromFDroid(),
            // SY -->
            showNavUpdates = screenModel.showNavUpdates,
            showNavHistory = screenModel.showNavHistory,
            // SY <--
            onClickDownloadQueue = { router.pushController(DownloadController()) },
            onClickCategories = { router.pushController(CategoryController()) },
            onClickBackupAndRestore = { router.pushController(SettingsMainController.toBackupScreen()) },
            onClickSettings = { router.pushController(SettingsMainController()) },
            onClickAbout = { router.pushController(SettingsMainController.toAboutScreen()) },
            // SY -->
            onClickBatchAdd = { router.pushController(BatchAddController()) },
            onClickUpdates = { router.pushController(UpdatesController()) },
            onClickHistory = { router.pushController(HistoryController()) },
            // SY <--
        )
    }
}

private class MoreScreenModel(
    private val downloadManager: DownloadManager = Injekt.get(),
    preferences: BasePreferences = Injekt.get(),
    // SY -->
    uiPreferences: UiPreferences = Injekt.get(),
    // SY <--
) : ScreenModel {

    var downloadedOnly by preferences.downloadedOnly().asState(coroutineScope)
    var incognitoMode by preferences.incognitoMode().asState(coroutineScope)

    // SY -->
    val showNavUpdates by uiPreferences.showNavUpdates().asState(coroutineScope)
    val showNavHistory by uiPreferences.showNavHistory().asState(coroutineScope)
    // SY <--

    private var _state: MutableStateFlow<DownloadQueueState> = MutableStateFlow(DownloadQueueState.Stopped)
    val downloadQueueState: StateFlow<DownloadQueueState> = _state.asStateFlow()

    init {
        // Handle running/paused status change and queue progress updating
        coroutineScope.launchIO {
            combine(
                DownloadService.isRunning,
                downloadManager.queue.updatedFlow(),
            ) { isRunning, downloadQueue -> Pair(isRunning, downloadQueue.size) }
                .collectLatest { (isDownloading, downloadQueueSize) ->
                    val pendingDownloadExists = downloadQueueSize != 0
                    _state.value = when {
                        !pendingDownloadExists -> DownloadQueueState.Stopped
                        !isDownloading && !pendingDownloadExists -> DownloadQueueState.Paused(0)
                        !isDownloading && pendingDownloadExists -> DownloadQueueState.Paused(downloadQueueSize)
                        else -> DownloadQueueState.Downloading(downloadQueueSize)
                    }
                }
        }
    }
}

sealed class DownloadQueueState {
    object Stopped : DownloadQueueState()
    data class Paused(val pending: Int) : DownloadQueueState()
    data class Downloading(val pending: Int) : DownloadQueueState()
}
