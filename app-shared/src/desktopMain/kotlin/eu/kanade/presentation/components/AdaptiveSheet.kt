package eu.kanade.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import tachiyomi.presentation.core.components.AdaptiveSheet as AdaptiveSheetImpl

/**
 * KMK: desktop counterpart of the app's `eu.kanade.presentation.components.AdaptiveSheet`, which the borrowed
 * `MigrationConfigScreenSheet` (P19) opens. Same dialog around presentation-core's sheet; a desktop window always gets
 * Android's tablet form (centred, no swipe to dismiss).
 */
@Composable
fun AdaptiveSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    enableSwipeDismiss: Boolean = true,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        AdaptiveSheetImpl(
            isTabletUi = true,
            enableSwipeDismiss = enableSwipeDismiss,
            onDismissRequest = onDismissRequest,
            modifier = modifier,
        ) {
            content()
        }
    }
}
