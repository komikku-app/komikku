package eu.kanade.presentation.more.settings.database.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.TextButton
import eu.kanade.tachiyomi.R

@Composable
fun ClearDatabaseDeleteDialog(
    onDismissRequest: () -> Unit,
    // SY -->
    onDelete: (Boolean) -> Unit,
    // SY <--
) {
    // SY -->
    var keepReadManga by remember { mutableStateOf(true) }
    // SY <--
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = /* SY --> */ { onDelete(keepReadManga) } /* SY <-- */) {
                Text(text = stringResource(id = android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(id = android.R.string.cancel))
            }
        },
        text = {
            // SY -->
            Column {
                // SY <--
                Text(text = stringResource(id = R.string.clear_database_confirmation))
                // SY -->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clickable(onClick = { keepReadManga = !keepReadManga }),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.clear_db_exclude_read))
                    Checkbox(
                        checked = keepReadManga,
                        onCheckedChange = null,
                    )
                }
            }
            // SY <--
        },
    )
}
