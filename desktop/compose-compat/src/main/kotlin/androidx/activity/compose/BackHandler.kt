package androidx.activity.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi

/**
 * compose-compat: `androidx.activity.compose.BackHandler` for desktop, delegating to Compose
 * Multiplatform's back handler (triggered by the Escape key in desktop windows).
 *
 * CMP 1.12 deprecates that handler in favour of the navigation-event API; it is kept because it
 * has exactly the `(enabled, onBack)` shape the Android API has. Revisit with the next CMP bump.
 */
@Suppress("DEPRECATION")
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    androidx.compose.ui.backhandler.BackHandler(enabled = enabled, onBack = onBack)
}
