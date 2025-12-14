package mihon.feature.migration.list.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun MigrationMangaDialog(
    onDismissRequest: () -> Unit,
    copy: Boolean,
    totalCount: Int,
    skippedCount: Int,
    copyManga: () -> Unit,
    onMigrate: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        text = {
            Text(
                text = pluralStringResource(
                    if (copy) SYMR.plurals.copy_entry else SYMR.plurals.migrate_entry,
                    count = totalCount,
                    totalCount,
                    (if (skippedCount > 0) " " + stringResource(SYMR.strings.skipping_, skippedCount) else ""),
                ),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (copy) {
                        copyManga()
                    } else {
                        onMigrate()
                    }
                },
            ) {
                Text(text = stringResource(if (copy) MR.strings.copy else MR.strings.migrate))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
    )
}
