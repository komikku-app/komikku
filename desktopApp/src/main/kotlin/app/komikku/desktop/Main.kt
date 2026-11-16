package app.komikku.desktop

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import app.komikku.desktop.di.DesktopInjekt
import app.komikku.desktop.ui.DesktopTheme
import app.komikku.desktop.ui.DesktopToastHost
import app.komikku.desktop.ui.HomeShell
import app.komikku.desktop.window.DesktopShortcuts
import app.komikku.desktop.window.DesktopWindowPreferences
import app.komikku.desktop.window.rememberPersistedWindowState
import app.komikku.shared.coil.DesktopImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import eu.kanade.tachiyomi.network.NetworkHelper
import tachiyomi.core.common.util.system.DesktopLogcatLogger
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.awt.Dimension

/**
 * Komikku desktop entry point: boots the shared stack through Injekt ([DesktopInjekt]: preferences,
 * network, database, repositories, interactors, local source) and opens the main window.
 */
fun main() {
    DesktopLogcatLogger.install()
    val app = DesktopInjekt.init()

    application {
        KomikkuWindow(app = app, onCloseRequest = ::exitApplication)
    }
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
        KomikkuApp(context = app, shortcuts = shortcuts)
    }
}

/**
 * Window content: compat [LocalContext] (for `stringResource` & co.), the desktop Coil image loader,
 * the app theme, the snackbar host behind compat toasts and the navigation shell.
 */
@Composable
fun KomikkuApp(
    context: Context,
    shortcuts: DesktopShortcuts? = null,
) {
    setSingletonImageLoaderFactory { platformContext ->
        DesktopImageLoader.create(
            context = platformContext,
            networkHelper = Injekt.get<NetworkHelper>(),
            sourceManager = Injekt.get<SourceManager>(),
            cacheDir = context.cacheDir,
        )
    }
    CompositionLocalProvider(LocalContext provides context) {
        DesktopTheme {
            DesktopToastHost {
                HomeShell(shortcuts = shortcuts)
            }
        }
    }
}
