package eu.kanade.presentation.library.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.DialogProperties
import exh.favorites.FavoritesSyncStatus
import kotlinx.coroutines.delay
import tachiyomi.core.i18n.stringResource
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import kotlin.time.Duration.Companion.seconds

data class SyncFavoritesProgressProperties(
    val title: String,
    val text: String,
    val canDismiss: Boolean,
    val positiveButtonText: String? = null,
    val positiveButton: (() -> Unit)? = null,
    val negativeButtonText: String? = null,
    val negativeButton: (() -> Unit)? = null,
)

@Composable
fun SyncFavoritesProgressDialog(
    status: FavoritesSyncStatus,
    setStatusIdle: () -> Unit,
    openManga: (Manga) -> Unit,
) {
    val context = LocalContext.current
    val properties by produceState<SyncFavoritesProgressProperties?>(initialValue = null, status) {
        when (status) {
            is FavoritesSyncStatus.BadLibraryState.MangaInMultipleCategories -> value = SyncFavoritesProgressProperties(
                title = context.stringResource(SYMR.strings.favorites_sync_error),
                text = context.stringResource(SYMR.strings.favorites_sync_bad_library_state, status.message),
                canDismiss = false,
                positiveButtonText = context.stringResource(SYMR.strings.show_gallery),
                positiveButton = {
                    openManga(status.manga)
                    setStatusIdle()
                },
                negativeButtonText = context.stringResource(MR.strings.action_ok),
                negativeButton = setStatusIdle,
            )
            is FavoritesSyncStatus.CompleteWithErrors -> value = SyncFavoritesProgressProperties(
                title = context.stringResource(SYMR.strings.favorites_sync_done_errors),
                text = context.stringResource(SYMR.strings.favorites_sync_done_errors_message, status.message),
                canDismiss = false,
                positiveButtonText = context.stringResource(MR.strings.action_ok),
                positiveButton = setStatusIdle,
            )
            is FavoritesSyncStatus.Error -> value = SyncFavoritesProgressProperties(
                title = context.stringResource(SYMR.strings.favorites_sync_error),
                text = context.stringResource(SYMR.strings.favorites_sync_error_string, status.message),
                canDismiss = false,
                positiveButtonText = context.stringResource(MR.strings.action_ok),
                positiveButton = setStatusIdle,
            )
            is FavoritesSyncStatus.Idle -> value = null
            is FavoritesSyncStatus.Initializing, is FavoritesSyncStatus.Processing -> {
                value = SyncFavoritesProgressProperties(
                    title = context.stringResource(SYMR.strings.favorites_syncing),
                    text = status.message,
                    canDismiss = false,
                )
                if (status is FavoritesSyncStatus.Processing && status.title != null) {
                    delay(5.seconds)
                    value = SyncFavoritesProgressProperties(
                        title = context.stringResource(SYMR.strings.favorites_syncing),
                        text = status.delayedMessage ?: status.message,
                        canDismiss = false,
                    )
                }
            }
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
                }
            },
            properties = DialogProperties(
                dismissOnClickOutside = dialog.canDismiss,
                dismissOnBackPress = dialog.canDismiss,
            ),
        )
    }
}
