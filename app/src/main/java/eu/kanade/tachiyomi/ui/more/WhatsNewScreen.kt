package eu.kanade.tachiyomi.ui.more

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.more.WhatsNewScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.util.system.openInBrowser

class WhatsNewScreen(
    private val currentVersion: String,
    private val versionName: String,
    private val changelogInfo: String,
    private val releaseLink: String,
) : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val changelogInfoNoChecksum = remember {
            changelogInfo
        }

        WhatsNewScreen(
            currentVersion = currentVersion,
            versionName = versionName,
            changelogInfo = changelogInfoNoChecksum,
            onOpenInBrowser = { context.openInBrowser(releaseLink) },
            onAcceptUpdate = { navigator.pop() },
        )
    }
}
