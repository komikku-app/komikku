package eu.kanade.presentation.browse.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ViewModule
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.tachiyomi.R
import tachiyomi.domain.library.model.LibraryDisplayMode

@Composable
fun BrowseSourceSimpleToolbar(
    navigateUp: () -> Unit,
    title: String,
    displayMode: LibraryDisplayMode?,
    onDisplayModeChange: (LibraryDisplayMode) -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    AppBar(
        navigateUp = navigateUp,
        title = title,
        actions = {
            var selectingDisplayMode by remember { mutableStateOf(false) }
            AppBarActions(
                // SY -->
                actions = listOfNotNull(
                    AppBar.Action(
                        title = stringResource(id = R.string.action_display_mode),
                        icon = Icons.Outlined.ViewModule,
                        onClick = { selectingDisplayMode = true },
                    ),
                ),
            )
            DropdownMenu(
                expanded = selectingDisplayMode,
                onDismissRequest = { selectingDisplayMode = false },
            ) {
                DropdownMenuItem(
                    text = { Text(text = stringResource(id = R.string.action_display_comfortable_grid)) },
                    onClick = { onDisplayModeChange(LibraryDisplayMode.ComfortableGrid) },
                    trailingIcon = {
                        if (displayMode == LibraryDisplayMode.ComfortableGrid) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = "",
                            )
                        }
                    },
                )
                DropdownMenuItem(
                    text = { Text(text = stringResource(id = R.string.action_display_grid)) },
                    onClick = { onDisplayModeChange(LibraryDisplayMode.CompactGrid) },
                    trailingIcon = {
                        if (displayMode == LibraryDisplayMode.CompactGrid) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = "",
                            )
                        }
                    },
                )
                DropdownMenuItem(
                    text = { Text(text = stringResource(id = R.string.action_display_list)) },
                    onClick = { onDisplayModeChange(LibraryDisplayMode.List) },
                    trailingIcon = {
                        if (displayMode == LibraryDisplayMode.List) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = "",
                            )
                        }
                    },
                )
            }
        },
        scrollBehavior = scrollBehavior,
    )
}
