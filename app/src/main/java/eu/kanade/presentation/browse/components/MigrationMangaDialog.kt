package eu.kanade.presentation.browse.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import eu.kanade.tachiyomi.R

@Composable
fun MigrationMangaDialog(
    onDismissRequest: () -> Unit,
    copy: Boolean,
    mangaSet: Int,
    mangaSkipped: Int,
    copyManga: () -> Unit,
    migrateManga: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = {
                    if (copy) {
                        copyManga()
                    } else {
                        migrateManga()
                    }
                },
            ) {
                Text(text = stringResource(if (copy) R.string.copy else R.string.migrate))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(android.R.string.cancel))
            }
        },
        text = {
            Text(
                text = pluralStringResource(
                    if (copy) R.plurals.copy_entry else R.plurals.migrate_entry,
                    count = mangaSet,
                    mangaSet,
                    (if (mangaSkipped > 0) " " + stringResource(R.string.skipping_, mangaSkipped) else ""),
                ),
            )
        },
    )
}
