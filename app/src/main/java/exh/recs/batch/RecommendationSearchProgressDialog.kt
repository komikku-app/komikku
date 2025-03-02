package exh.recs.batch

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR

data class RecommendationSearchProgressProperties(
    val title: String,
    val text: String,
    val positiveButtonText: String? = null,
    val positiveButton: (() -> Unit)? = null,
    val negativeButtonText: String? = null,
    val negativeButton: (() -> Unit)? = null,
)

@Composable
fun RecommendationSearchProgressDialog(
    status: SearchStatus,
    setStatusIdle: () -> Unit,
    setStatusCancelling: () -> Unit,
) {
    val context = LocalContext.current
    val currentView = LocalView.current

    DisposableEffect(status) {
        if (status != SearchStatus.Idle) {
            currentView.keepScreenOn = true
        }
        onDispose {
            currentView.keepScreenOn = false
        }
    }

    val properties by produceState<RecommendationSearchProgressProperties?>(initialValue = null, status) {
        value = when (status) {
            is SearchStatus.Initializing -> {
                RecommendationSearchProgressProperties(
                    title = context.stringResource(SYMR.strings.rec_collecting),
                    text = context.stringResource(SYMR.strings.rec_initializing),
                    negativeButtonText = context.stringResource(MR.strings.action_cancel),
                    negativeButton = setStatusCancelling,
                )
            }
            is SearchStatus.Error -> {
                RecommendationSearchProgressProperties(
                    title = context.stringResource(SYMR.strings.rec_error_title),
                    text = context.stringResource(SYMR.strings.rec_error_string, status.message),
                    positiveButtonText = context.stringResource(MR.strings.action_ok),
                    positiveButton = setStatusIdle,
                )
            }
            is SearchStatus.Processing -> {
                RecommendationSearchProgressProperties(
                    title = context.stringResource(SYMR.strings.rec_collecting),
                    text = context.stringResource(SYMR.strings.rec_processing_state, status.current, status.total) + "\n\n" + status.manga.title,
                    negativeButtonText = context.stringResource(MR.strings.action_cancel),
                    negativeButton = setStatusCancelling,
                )
            }
            else -> null
        }
    }
    val dialog = properties
    if (dialog != null) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {
                if (dialog.positiveButton != null && dialog.positiveButtonText != null) {
                    TextButton(onClick = dialog.positiveButton) {
                        Text(text = dialog.positiveButtonText)
                    }
                }
            },
            dismissButton = {
                if (dialog.negativeButton != null && dialog.negativeButtonText != null) {
                    TextButton(onClick = dialog.negativeButton) {
                        Text(text = dialog.negativeButtonText)
                    }
                }
            },
            title = {
                Text(text = dialog.title)
            },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                ) {
                    Text(text = dialog.text)
                    if (status is SearchStatus.Processing) {
                        LinearProgressIndicator(
                            progress = { status.current.toFloat() / status.total },
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        )
                    }
                    // KMK -->
                    if (status is SearchStatus.Initializing) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(40.dp),
                                strokeWidth = 3.dp,
                            )
                        }
                    }
                }
            },
            properties = DialogProperties(
                dismissOnClickOutside = false,
                dismissOnBackPress = false,
            ),
        )
    }
}
