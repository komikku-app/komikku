package eu.kanade.presentation.browse.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.browse.migration.advanced.process.MigratingManga
import me.saket.cascade.CascadeDropdownMenu

@Composable
fun MigrationActionIcon(
    modifier: Modifier,
    result: MigratingManga.SearchResult,
    skipManga: () -> Unit,
    searchManually: () -> Unit,
    migrateNow: () -> Unit,
    copyNow: () -> Unit,
) {
    var moreExpanded by remember { mutableStateOf(false) }
    val closeMenu = { moreExpanded = false }

    Box(modifier) {
        if (result is MigratingManga.SearchResult.Searching) {
            IconButton(onClick = skipManga) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.action_stop),
                )
            }
        } else if (result is MigratingManga.SearchResult.Result || result is MigratingManga.SearchResult.NotFound) {
            IconButton(onClick = { moreExpanded = !moreExpanded }) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = stringResource(R.string.abc_action_menu_overflow_description),
                )
            }
            CascadeDropdownMenu(
                expanded = moreExpanded,
                onDismissRequest = closeMenu,
                offset = DpOffset(8.dp, (-56).dp),
            ) {
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_search_manually)) },
                    onClick = {
                        searchManually()
                        closeMenu()
                    },
                )
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_skip_entry)) },
                    onClick = {
                        skipManga()
                        closeMenu()
                    },
                )
                if (result is MigratingManga.SearchResult.Result) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_migrate_now)) },
                        onClick = {
                            migrateNow()
                            closeMenu()
                        },
                    )
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_copy_now)) },
                        onClick = {
                            copyNow()
                            closeMenu()
                        },
                    )
                }
            }
        }
    }
}
