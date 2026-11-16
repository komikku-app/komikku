package app.komikku.desktop.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Shows the compat `android.widget.Toast`s (`context.toast(…)` from shared code and extensions) as
 * Material 3 snackbars over [content], while composed: replaces `Toast.presenter` and restores the
 * previous one on dispose. `Toast.show()` may be called from any thread.
 */
@Composable
fun DesktopToastHost(content: @Composable () -> Unit) {
    val hostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    DisposableEffect(hostState) {
        val previous = Toast.presenter
        Toast.presenter = { text, duration ->
            scope.launch {
                hostState.showSnackbar(
                    message = text.toString(),
                    duration = if (duration == Toast.LENGTH_LONG) SnackbarDuration.Long else SnackbarDuration.Short,
                )
            }
        }
        onDispose { Toast.presenter = previous }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        content()
        SnackbarHost(
            hostState = hostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        )
    }
}
