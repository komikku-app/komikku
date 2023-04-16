package eu.kanade.presentation.browse.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import eu.kanade.tachiyomi.R

@Composable
fun MigrationProgressDialog(
    progress: Float,
    exitMigration: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {
            TextButton(onClick = exitMigration) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
        text = {
            if (!progress.isNaN()) {
                val progressAnimated by animateFloatAsState(
                    targetValue = progress,
                    animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
                )
                LinearProgressIndicator(progressAnimated)
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    )
}
