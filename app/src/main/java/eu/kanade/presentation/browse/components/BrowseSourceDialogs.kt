package eu.kanade.presentation.browse.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.DialogProperties
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.components.TabbedDialogPaddings
import eu.kanade.presentation.more.settings.LocalPreferenceMinHeight
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.ImmutableList
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun MangaActionsDialog(
    manga: Manga,
    onDismissRequest: () -> Unit,
    onClickFavorite: () -> Unit,
    onClickBlacklist: () -> Unit,
) {
    val minHeight = LocalPreferenceMinHeight.current

    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .padding(
                    vertical = TabbedDialogPaddings.Vertical,
                    horizontal = TabbedDialogPaddings.Horizontal,
                )
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            Text(
                text = manga.title,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = MaterialTheme.padding.small),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            TextPreferenceWidget(
                title = stringResource(if (manga.favorite) MR.strings.action_remove else MR.strings.add_to_library),
                icon = if (manga.favorite) Icons.Outlined.Delete else Icons.Outlined.FavoriteBorder,
                onPreferenceClick = {
                    onDismissRequest()
                    onClickFavorite()
                },
            )
            TextPreferenceWidget(
                title = stringResource(KMR.strings.action_add_to_blacklist),
                icon = Icons.Outlined.Block,
                onPreferenceClick = {
                    onDismissRequest()
                    onClickBlacklist()
                },
            )

            TextButton(
                onClick = onDismissRequest,
                modifier = Modifier
                    .fillMaxWidth()
                    .sizeIn(minHeight = minHeight),
            ) {
                Text(
                    text = stringResource(MR.strings.action_cancel),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
fun RemoveMangaDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    mangaToRemove: Manga,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismissRequest()
                    onConfirm()
                },
            ) {
                Text(text = stringResource(MR.strings.action_remove))
            }
        },
        title = {
            Text(text = stringResource(MR.strings.are_you_sure))
        },
        text = {
            Text(text = stringResource(MR.strings.remove_manga, mangaToRemove.title))
        },
    )
}

@Composable
fun SavedSearchDeleteDialog(
    onDismissRequest: () -> Unit,
    name: String,
    deleteSavedSearch: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    deleteSavedSearch()
                    onDismissRequest()
                },
            ) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        title = {
            Text(text = stringResource(SYMR.strings.save_search_delete))
        },
        text = {
            Text(text = stringResource(SYMR.strings.save_search_delete_message, name))
        },
    )
}

@Composable
fun SavedSearchCreateDialog(
    onDismissRequest: () -> Unit,
    currentSavedSearches: ImmutableList<String>,
    saveSearch: (String) -> Unit,
) {
    var textFieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(SYMR.strings.save_search)) },
        text = {
            OutlinedTextField(
                value = textFieldValue,
                onValueChange = { textFieldValue = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(text = stringResource(SYMR.strings.save_search_hint))
                },
            )
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = true,
        ),
        confirmButton = {
            TextButton(
                onClick = {
                    val searchName = textFieldValue.text.trim()
                    if (searchName.isNotBlank() && searchName !in currentSavedSearches) {
                        saveSearch(searchName)
                        onDismissRequest()
                    } else {
                        context.toast(SYMR.strings.save_search_invalid_name)
                    }
                },
            ) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
    )
}
