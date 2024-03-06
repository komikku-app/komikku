package eu.kanade.presentation.manga

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowColumn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun DuplicateMangaDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    onOpenManga: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = stringResource(MR.strings.are_you_sure))
        },
        text = {
            Text(text = stringResource(MR.strings.confirm_add_duplicate_manga))
        },
        confirmButton = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
            ) {
                TextButton(
                    onClick = {
                        onDismissRequest()
                        onOpenManga()
                    },
                ) {
                    Text(text = stringResource(MR.strings.action_show_manga))
                }

                Spacer(modifier = Modifier.weight(1f))

                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
                TextButton(
                    onClick = {
                        onDismissRequest()
                        onConfirm()
                    },
                ) {
                    Text(text = stringResource(MR.strings.action_add))
                }
            }
        },
    )
}

// KMK -->
@Composable
fun AllowDuplicateDialog(
    onDismissRequest: () -> Unit,
    onAllowAllDuplicate: () -> Unit,
    onSkipAllDuplicate: () -> Unit,
    onOpenManga: () -> Unit = {},
    onAllowDuplicate: () -> Unit = {},
    onSkipDuplicate: () -> Unit = {},
    duplicatedName: String = "",
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = duplicatedName)
        },
        text = {
            Text(text = stringResource(MR.strings.confirm_add_duplicate_manga))
        },
        confirmButton = {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                FlowColumn {
                    TextButton(
                        onClick = {
                            onDismissRequest()
                            onAllowDuplicate()
                        },
                        Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text(text = stringResource(MR.strings.action_allow_duplicate_manga))
                    }

                    TextButton(
                        onClick = {
                            onDismissRequest()
                            onAllowAllDuplicate()
                        },
                        Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text(text = stringResource(MR.strings.action_allow_all_duplicate_manga))
                    }
                }

                FlowColumn {
                    TextButton(
                        onClick = {
                            onDismissRequest()
                            onSkipDuplicate()
                        },
                        Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text(text = stringResource(MR.strings.action_skip_duplicate_manga))
                    }

                    TextButton(
                        onClick = {
                            onDismissRequest()
                            onSkipAllDuplicate()
                        },
                        Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text(text = stringResource(MR.strings.action_skip_all_duplicate_manga))
                    }
                }

                FlowColumn {
                    TextButton(
                        onClick = {
                            onDismissRequest()
                            onOpenManga()
                        },
                        Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text(text = stringResource(MR.strings.action_show_manga))
                    }

                    TextButton(
                        onClick = {
                            onDismissRequest()
                        },
                        Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text(text = stringResource(MR.strings.action_cancel))
                    }
                }

            }
        },
    )
}
// KMK <--
