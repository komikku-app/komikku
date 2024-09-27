package eu.kanade.presentation.browse.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.presentation.components.RadioMenuItem
import eu.kanade.tachiyomi.ui.browse.bulkSelectionButton
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun BrowseSourceSimpleToolbar(
    navigateUp: () -> Unit,
    title: String,
    displayMode: LibraryDisplayMode,
    onDisplayModeChange: (LibraryDisplayMode) -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    // KMK -->
    toggleSelectionMode: () -> Unit,
    // KMK <--
) {
    AppBar(
        navigateUp = navigateUp,
        title = title,
        actions = {
            var selectingDisplayMode by remember { mutableStateOf(false) }
            AppBarActions(
                // SY -->
                actions = persistentListOf(
                    AppBar.Action(
                        title = stringResource(MR.strings.action_display_mode),
                        // KMK -->
                        icon = if (displayMode == LibraryDisplayMode.List) {
                            Icons.AutoMirrored.Filled.ViewList
                        } else {
                            Icons.Filled.ViewModule
                        },
                        // KMK <--
                        onClick = { selectingDisplayMode = true },
                    ),
                    // KMK -->
                    bulkSelectionButton(toggleSelectionMode),
                    // KMK <--
                ),
            )
            DropdownMenu(
                expanded = selectingDisplayMode,
                onDismissRequest = { selectingDisplayMode = false },
            ) {
                // KMK -->
                RadioMenuItem(
                    text = { Text(text = stringResource(MR.strings.action_display_comfortable_grid)) },
                    isChecked = displayMode == LibraryDisplayMode.ComfortableGrid,
                ) {
                    selectingDisplayMode = false
                    onDisplayModeChange(LibraryDisplayMode.ComfortableGrid)
                }
                val usePanoramaCover by lazy { Injekt.get<UiPreferences>().usePanoramaCover() }
                if (!usePanoramaCover.collectAsState().value) {
                    RadioMenuItem(
                        text = { Text(text = stringResource(KMR.strings.action_display_comfortable_grid_panorama)) },
                        isChecked = displayMode == LibraryDisplayMode.ComfortableGridPanorama,
                    ) {
                        selectingDisplayMode = false
                        onDisplayModeChange(LibraryDisplayMode.ComfortableGridPanorama)
                    }
                }
                RadioMenuItem(
                    text = { Text(text = stringResource(MR.strings.action_display_grid)) },
                    isChecked = displayMode == LibraryDisplayMode.CompactGrid,
                ) {
                    selectingDisplayMode = false
                    onDisplayModeChange(LibraryDisplayMode.CompactGrid)
                }
                RadioMenuItem(
                    text = { Text(text = stringResource(MR.strings.action_display_list)) },
                    isChecked = displayMode == LibraryDisplayMode.List,
                ) {
                    selectingDisplayMode = false
                    onDisplayModeChange(LibraryDisplayMode.List)
                }
                // KMK <--
            }
        },
        scrollBehavior = scrollBehavior,
    )
}
