package app.komikku.desktop

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import app.komikku.desktop.di.DesktopInjekt
import app.komikku.desktop.instance.DesktopSingleInstance
import app.komikku.desktop.log.DesktopLogFile
import app.komikku.desktop.notification.DesktopNotifications
import app.komikku.desktop.notification.DesktopTray
import app.komikku.desktop.notification.DesktopTrayPreferences
import app.komikku.desktop.platform.DesktopAppEvents
import app.komikku.desktop.scheduler.DesktopScheduler
import app.komikku.desktop.scheduler.stopDesktopWork
import app.komikku.desktop.security.DesktopAppLock
import app.komikku.desktop.selftest.DesktopSelfTest
import app.komikku.desktop.ui.DesktopTheme
import app.komikku.desktop.ui.DesktopToastHost
import app.komikku.desktop.ui.HomeShell
import app.komikku.desktop.ui.reader.ReaderHost
import app.komikku.desktop.ui.reader.ReaderLauncher
import app.komikku.desktop.ui.security.AppLockGate
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import tachiyomi.core.common.desktop.DesktopDirs
import tachiyomi.core.common.util.system.DesktopLogcatLogger
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.awt.Dimension
import javax.swing.SwingUtilities
import kotlin.system.exitProcess

/**
 * Komikku desktop entry point: boots the shared stack through Injekt ([DesktopInjekt]: preferences,
 * network, database, repositories, interactors, local source), starts the scheduler ([DesktopScheduler]) and opens
 * the main window. `--self-test[=<report file>]` runs the packaged runtime's self-test ([DesktopSelfTest], M2) instead
 * and exits; `--self-test-extensions=<folder>` adds the extension APKs in that folder to it, `--self-test-notify` one
 * notification through the system notifier. One instance runs per data folder ([DesktopSingleInstance]).
 */
fun main(args: Array<String>) {
    if (DesktopSelfTest.isRequested(args)) {
        exitProcess(DesktopSelfTest.main(args))
    }
    DesktopLogcatLogger.install()
    // P12: the log file Settings → Advanced opens and the crash handler writing to it, installed before anything else
    // can fail (P12 review): the data folder is resolved without Injekt, so a failure inside `DesktopInjekt.init()`
    // reaches the uncaught-exception handler and ends up in the file
    val dataDir = DesktopDirs.dataDir
    // One running instance per data folder: another start on it asks this one to show its window and exits. Checked
    // before the log file, which belongs to the running instance, and before `DesktopInjekt.init()` opens the database
    val singleInstance = DesktopSingleInstance.ensure(dataDir)
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
    // P17: Android's notifications (new chapters, update errors, downloader, backup / restore, app update) through the
    // system tray icon while there is one (KomikkuWindow installs it)
    Injekt.get<DesktopNotifications>().start(startScope)

    // Not exiting from `application`: its default `System.exit` would skip the bounded shutdown below
    application(exitProcessOnExit = false) {
        // Cmd+Q / Dock → Quit close the app like the window's close button (AWT would exit the JVM at once)
        DisposableEffect(Unit) {
            val installed = DesktopAppEvents.installQuitHandler(::exitApplication)
            onDispose { if (installed) DesktopAppEvents.removeQuitHandler() }
        }
        KomikkuWindow(app = app, onCloseRequest = ::exitApplication, singleInstance = singleInstance)
    }
    // The window is gone: a new start must wait for the lock, not be told that this exiting process showed its window
    singleInstance.stopListening()
    // The work runs on daemon threads: stop it and let it clean up before the JVM exits (bounded).
    runBlocking { stopDesktopWork(scheduler, Injekt.get(), Injekt.get()) }
    singleInstance.close()
    DesktopAppEvents.finishQuit()
    exitProcess(0)
}

/** The main window: persisted size / position / maximized state, keyboard shortcuts, the app UI. */
@Composable
fun ApplicationScope.KomikkuWindow(
    app: Application,
    onCloseRequest: () -> Unit,
    singleInstance: DesktopSingleInstance? = null,
) {
    val windowPreferences = remember { Injekt.get<DesktopWindowPreferences>() }
    val windowState = rememberPersistedWindowState(windowPreferences)
    val shortcuts = remember { DesktopShortcuts() }
    val appLock = remember { Injekt.get<DesktopAppLock>() }
    // P16: Android's app going to the background and back is the window being minimised and restored
    LaunchedEffect(windowState, appLock) {
        snapshotFlow { windowState.isMinimized }
            .distinctUntilChanged()
            .collect { minimized -> if (minimized) appLock.onWindowHidden() else appLock.onWindowShown() }
    }
    // macOS Cmd+H hides the app without minimising the window: the same for the lock
    DisposableEffect(windowState, appLock) {
        val listener = DesktopAppEvents.addHideListener(
            onHidden = appLock::onWindowHidden,
            onShown = { if (!windowState.isMinimized) appLock.onWindowShown() },
        )
        onDispose { listener?.close() }
    }
    val tray = remember { Injekt.get<DesktopTray>() }
    val trayIcon by remember { Injekt.get<DesktopTrayPreferences>().trayIcon() }.collectAsState()
    Window(
        onCloseRequest = onCloseRequest,
        state = windowState,
        title = MR.strings.app_name.localized(),
        // No shortcuts behind the lock screen
        onPreviewKeyEvent = { event -> !appLock.locked.value && shortcuts.onPreviewKeyEvent(event) },
    ) {
        remember(window) {
            window.minimumSize = Dimension(DesktopWindowPreferences.MIN_WIDTH, DesktopWindowPreferences.MIN_HEIGHT)
        }
        // P17: the optional tray icon (no-op without a system tray); it brings this window back and can quit
        DisposableEffect(trayIcon, window) {
            if (trayIcon) {
                tray.install(
                    openText = KMR.strings.desktop_tray_open.localized(),
                    exitText = KMR.strings.desktop_tray_exit.localized(),
                    onOpen = {
                        windowState.isMinimized = false
                        window.isVisible = true
                        window.toFront()
                        window.requestFocus()
                    },
                    onExit = onCloseRequest,
                )
            }
            onDispose { tray.remove() }
        }
        // Another start on this data folder asked for the window (on the instance's thread)
        DisposableEffect(singleInstance, window) {
            singleInstance?.onShowRequest = {
                SwingUtilities.invokeLater {
                    windowState.isMinimized = false
                    window.isVisible = true
                    window.toFront()
                    window.requestFocus()
                    DesktopSingleInstance.requestForeground()
                }
            }
            onDispose { singleInstance?.onShowRequest = null }
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
            // P16: the app lock covers everything (Android's UnlockActivity over the app): the toasts too (review fix,
            // they were drawn over the lock screen), and the dialogs / menus the app has open
            AppLockGate(lock = remember { Injekt.get<DesktopAppLock>() }) {
                DesktopToastHost {
                    ReaderHost(launcher = remember { Injekt.get<ReaderLauncher>() }, onToggleFullscreen = onToggleFullscreen) {
                        HomeShell(shortcuts = shortcuts)
                        AppUpdateDialogHost()
                    }
                }
            }
        }
    }
}
