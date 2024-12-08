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
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import kotlin.time.Duration.Companion.seconds

data class SyncFavoritesProgressProperties(
    val title: String,
    val text: String,
    val positiveButtonText: String? = null,
    val positiveButton: (() -> Unit)? = null,
    val negativeButtonText: String? = null,
    val negativeButton: (() -> Unit)? = null,
)

@Composable
fun SyncFavoritesProgressDialog(
    status: FavoritesSyncStatus,
    setStatusIdle: () -> Unit,
    openManga: (Long) -> Unit,
) {
    val context = LocalContext.current
    val properties by produceState<SyncFavoritesProgressProperties?>(initialValue = null, status) {
        when (status) {
            is FavoritesSyncStatus.BadLibraryState.MangaInMultipleCategories -> value = SyncFavoritesProgressProperties(
                title = context.stringResource(SYMR.strings.favorites_sync_error),
                text = context.stringResource(
                    SYMR.strings.favorites_sync_bad_library_state,
                    context.stringResource(
                        SYMR.strings.favorites_sync_gallery_in_multiple_categories, status.mangaTitle,
                        status.categories.joinToString(),
                    ),
                ),
                positiveButtonText = context.stringResource(SYMR.strings.show_gallery),
                positiveButton = {
                    openManga(status.mangaId)
                    setStatusIdle()
                },
                negativeButtonText = context.stringResource(MR.strings.action_ok),
                negativeButton = setStatusIdle,
            )
            is FavoritesSyncStatus.CompleteWithErrors -> value = SyncFavoritesProgressProperties(
                title = context.stringResource(SYMR.strings.favorites_sync_done_errors),
                text = context.stringResource(
                    SYMR.strings.favorites_sync_done_errors_message,
                    status.messages.joinToString(separator = "\n") {
                        when (it) {
                            is FavoritesSyncStatus.SyncError.GallerySyncError.GalleryAddFail ->
                                context.stringResource(SYMR.strings.favorites_sync_failed_to_add_to_local) +
                                    context.stringResource(
                                        SYMR.strings.favorites_sync_failed_to_add_to_local_error, it.title, it.reason,
                                    )
                            is FavoritesSyncStatus.SyncError.GallerySyncError.InvalidGalleryFail ->
                                context.stringResource(SYMR.strings.favorites_sync_failed_to_add_to_local) +
                                    context.stringResource(
                                        SYMR.strings.favorites_sync_failed_to_add_to_local_unknown_type, it.title, it.url,
                                    )
                            is FavoritesSyncStatus.SyncError.GallerySyncError.UnableToAddGalleryToRemote ->
                                context.stringResource(SYMR.strings.favorites_sync_unable_to_add_to_remote, it.title, it.gid)
                            FavoritesSyncStatus.SyncError.GallerySyncError.UnableToDeleteFromRemote ->
                                context.stringResource(SYMR.strings.favorites_sync_unable_to_delete)
                        }
                    },
                ),
                positiveButtonText = context.stringResource(MR.strings.action_ok),
                positiveButton = setStatusIdle,
            )
            is FavoritesSyncStatus.Idle -> value = null
            is FavoritesSyncStatus.Initializing -> {
                value = SyncFavoritesProgressProperties(
                    title = context.stringResource(SYMR.strings.favorites_syncing),
                    text = context.stringResource(SYMR.strings.favorites_sync_initializing),
                )
            }

            is FavoritesSyncStatus.SyncError -> value = SyncFavoritesProgressProperties(
                title = context.stringResource(SYMR.strings.favorites_sync_error),
                text = context.stringResource(
                    SYMR.strings.favorites_sync_error_string,
                    when (status) {
                        FavoritesSyncStatus.SyncError.NotLoggedInSyncError -> context.stringResource(SYMR.strings.please_login)
                        FavoritesSyncStatus.SyncError.FailedToFetchFavorites ->
                            context.stringResource(SYMR.strings.favorites_sync_failed_to_featch)
                        is FavoritesSyncStatus.SyncError.UnknownSyncError ->
                            context.stringResource(SYMR.strings.favorites_sync_unknown_error, status.message)
                        is FavoritesSyncStatus.SyncError.GallerySyncError.GalleryAddFail ->
                            context.stringResource(SYMR.strings.favorites_sync_failed_to_add_to_local) +
                                context.stringResource(
                                    SYMR.strings.favorites_sync_failed_to_add_to_local_error, status.title, status.reason,
                                )
                        is FavoritesSyncStatus.SyncError.GallerySyncError.InvalidGalleryFail ->
                            context.stringResource(SYMR.strings.favorites_sync_failed_to_add_to_local) +
                                context.stringResource(
                                    SYMR.strings.favorites_sync_failed_to_add_to_local_unknown_type, status.title, status.url,
                                )
                        is FavoritesSyncStatus.SyncError.GallerySyncError.UnableToAddGalleryToRemote ->
                            context.stringResource(SYMR.strings.favorites_sync_unable_to_add_to_remote, status.title, status.gid)
                        FavoritesSyncStatus.SyncError.GallerySyncError.UnableToDeleteFromRemote ->
                            context.stringResource(SYMR.strings.favorites_sync_unable_to_delete)
                    },
                ),
                positiveButtonText = context.stringResource(MR.strings.action_ok),
                positiveButton = setStatusIdle,
            )
            is FavoritesSyncStatus.Processing -> {
                val properties = SyncFavoritesProgressProperties(
                    title = context.stringResource(SYMR.strings.favorites_syncing),
                    text = when (status) {
                        FavoritesSyncStatus.Processing.VerifyingLibrary ->
                            context.stringResource(SYMR.strings.favorites_sync_verifying_library)
                        FavoritesSyncStatus.Processing.DownloadingFavorites ->
                            context.stringResource(SYMR.strings.favorites_sync_downloading)
                        FavoritesSyncStatus.Processing.CalculatingRemoteChanges ->
                            context.stringResource(SYMR.strings.favorites_sync_calculating_remote_changes)
                        FavoritesSyncStatus.Processing.CalculatingLocalChanges ->
                            context.stringResource(SYMR.strings.favorites_sync_calculating_local_changes)
                        FavoritesSyncStatus.Processing.SyncingCategoryNames ->
                            context.stringResource(SYMR.strings.favorites_sync_syncing_category_names)
                        is FavoritesSyncStatus.Processing.RemovingRemoteGalleries ->
                            context.stringResource(SYMR.strings.favorites_sync_removing_galleries, status.galleryCount)
                        is FavoritesSyncStatus.Processing.AddingGalleryToRemote ->
                            if (status.isThrottling) {
                                context.stringResource(
                                    SYMR.strings.favorites_sync_processing_throttle,
                                    context.stringResource(SYMR.strings.favorites_sync_adding_to_remote, status.index, status.total),
                                )
                            } else {
                                context.stringResource(SYMR.strings.favorites_sync_adding_to_remote, status.index, status.total)
                            }
                        is FavoritesSyncStatus.Processing.RemovingGalleryFromLocal ->
                            context.stringResource(SYMR.strings.favorites_sync_remove_from_local, status.index, status.total)
                        is FavoritesSyncStatus.Processing.AddingGalleryToLocal ->
                            if (status.isThrottling) {
                                context.stringResource(
                                    SYMR.strings.favorites_sync_processing_throttle,
                                    context.stringResource(SYMR.strings.favorites_sync_add_to_local, status.index, status.total),
                                )
                            } else {
                                context.stringResource(SYMR.strings.favorites_sync_add_to_local, status.index, status.total)
                            }

                        FavoritesSyncStatus.Processing.CleaningUp ->
                            context.stringResource(SYMR.strings.favorites_sync_cleaning_up)
                    },
                )
                value = properties
                if (
                    status is FavoritesSyncStatus.Processing.AddingGalleryToRemote ||
                    status is FavoritesSyncStatus.Processing.AddingGalleryToLocal
                ) {
                    delay(5.seconds)
                    value = properties.copy(
                        text = when (status) {
                            is FavoritesSyncStatus.Processing.AddingGalleryToRemote ->
                                properties.text + "\n\n" + status.title
                            is FavoritesSyncStatus.Processing.AddingGalleryToLocal ->
                                properties.text + "\n\n" + status.title
                            else -> properties.text
                        },
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
                dismissOnClickOutside = false,
                dismissOnBackPress = false,
            ),
        )
    }
}
