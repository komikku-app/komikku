package app.komikku.desktop

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import app.komikku.desktop.di.DesktopInjekt
import app.komikku.desktop.log.DesktopLogFile
import app.komikku.desktop.scheduler.DesktopScheduler
import app.komikku.desktop.scheduler.stopDesktopWork
import app.komikku.desktop.ui.DesktopTheme
import app.komikku.desktop.ui.DesktopToastHost
import app.komikku.desktop.ui.HomeShell
import app.komikku.desktop.ui.reader.ReaderHost
import app.komikku.desktop.ui.reader.ReaderLauncher
import app.komikku.desktop.updater.AppUpdateDialogHost
import app.komikku.desktop.updater.DesktopAppUpdateChecker
import app.komikku.desktop.window.DesktopShortcuts
import app.komikku.desktop.window.DesktopWindowPreferences
import app.komikku.desktop.window.rememberPersistedWindowState
import app.komikku.shared.coil.DesktopImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.network.NetworkHelper
import exh.source.ExhPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import tachiyomi.core.common.desktop.DesktopDirs
import tachiyomi.core.common.util.system.DesktopLogcatLogger
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.awt.Dimension

/**
 * Komikku desktop entry point: boots the shared stack through Injekt ([DesktopInjekt]: preferences,
 * network, database, repositories, interactors, local source), starts the scheduler ([DesktopScheduler]) and opens
 * the main window.
 */
fun main() {
    DesktopLogcatLogger.install()
    // P12: the log file Settings → Advanced opens and the crash handler writing to it, installed before anything else
    // can fail (P12 review): the data folder is resolved without Injekt, so a failure inside `DesktopInjekt.init()`
    // reaches the uncaught-exception handler and ends up in the file
    val dataDir = DesktopDirs.dataDir
    DesktopLogFile.install(DesktopLogFile.directoryFor(dataDir))
    val app = DesktopInjekt.init(dataDir)
    // SY's log level, once the preferences are there (applied at once when it changes)
    val startScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    Injekt.get<ExhPreferences>().logLevel(BuildConfig.DEBUG).changes()
        .onEach(DesktopLogFile::applyLevel)
        .launchIn(startScope)
    // Android `MainActivity`: "Clear chapter cache on app launch"
    if (Injekt.get<LibraryPreferences>().autoClearChapterCache().get()) startScope.launch { Injekt.get<ChapterCache>().clear() }
    // Android's start-up app update check (P6; only with the updater build flag). It must run before
    // `scheduler.start()`: the scheduler's catch-up then waits for it and does not ask GitHub a second time.
    Injekt.get<DesktopAppUpdateChecker>().checkOnStart(CoroutineScope(SupervisorJob() + Dispatchers.IO))
    // Scheduled library updates, automatic backups and app update checks while the app runs (P3); the first check
    // catches up on what was missed while it was closed.
    val scheduler = Injekt.get<DesktopScheduler>().also { it.start() }

    application {
        KomikkuWindow(app = app, onCloseRequest = ::exitApplication)
    }
    // The work runs on daemon threads: stop it and let it clean up before the JVM exits (bounded).
    runBlocking { stopDesktopWork(scheduler, Injekt.get(), Injekt.get()) }
}

/** The main window: persisted size / position / maximized state, keyboard shortcuts, the app UI. */
@Composable
fun ApplicationScope.KomikkuWindow(
    app: Application,
    onCloseRequest: () -> Unit,
) {
    val windowPreferences = remember { Injekt.get<DesktopWindowPreferences>() }
    val windowState = rememberPersistedWindowState(windowPreferences)
    val shortcuts = remember { DesktopShortcuts() }
    Window(
        onCloseRequest = onCloseRequest,
        state = windowState,
        title = MR.strings.app_name.localized(),
        onPreviewKeyEvent = shortcuts::onPreviewKeyEvent,
    ) {
        remember(window) {
            window.minimumSize = Dimension(DesktopWindowPreferences.MIN_WIDTH, DesktopWindowPreferences.MIN_HEIGHT)
        }
        KomikkuApp(
            context = app,
            shortcuts = shortcuts,
            onToggleFullscreen = {
                windowState.placement = if (windowState.placement == WindowPlacement.Fullscreen) {
                    WindowPlacement.Floating
                } else {
                    WindowPlacement.Fullscreen
                }
            },
        )
    }
}

/**
 * Window content: compat [LocalContext] (for `stringResource` & co.), the desktop Coil image loader,
 * the app theme, the snackbar host behind compat toasts, the navigation shell and the reader above it.
 */
@Composable
fun KomikkuApp(
    context: Context,
    shortcuts: DesktopShortcuts? = null,
    onToggleFullscreen: (() -> Unit)? = null,
) {
    setSingletonImageLoaderFactory { platformContext ->
        DesktopImageLoader.create(
            context = platformContext,
            networkHelper = Injekt.get<NetworkHelper>(),
            sourceManager = Injekt.get<SourceManager>(),
            cacheDir = context.cacheDir,
            coverCache = Injekt.get<CoverCache>(),
        )
    }
    CompositionLocalProvider(LocalContext provides context) {
        DesktopTheme {
            DesktopToastHost {
                ReaderHost(launcher = remember { Injekt.get<ReaderLauncher>() }, onToggleFullscreen = onToggleFullscreen) {
                    HomeShell(shortcuts = shortcuts)
                    AppUpdateDialogHost()
                }
            }
        }
    }
}
