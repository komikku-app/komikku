package eu.kanade.presentation.browse.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.automirrored.outlined.Help
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.presentation.components.RadioMenuItem
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.Source
import exh.source.anyIs
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.source.local.LocalSource

@Composable
fun BrowseSourceToolbar(
    searchQuery: String?,
    onSearchQueryChange: (String?) -> Unit,
    source: Source?,
    displayMode: LibraryDisplayMode?,
    onDisplayModeChange: (LibraryDisplayMode) -> Unit,
    navigateUp: () -> Unit,
    onWebViewClick: () -> Unit,
    onHelpClick: () -> Unit,
    // KMK -->
    onSettingsClick: (() -> Unit)?,
    // KMK <--
    onBlockruleClick: () -> Unit,
    onSearch: (String) -> Unit,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    // KMK -->
    toggleSelectionMode: () -> Unit,
    isRunning: Boolean,
    // KMK <--
) {
    // Avoid capturing unstable source in actions lambda
    val title = source?.name
    val isLocalSource = source is LocalSource
    val isConfigurableSource = source?.anyIs<ConfigurableSource>() == true

    var selectingDisplayMode by remember { mutableStateOf(false) }

    SearchToolbar(
        navigateUp = navigateUp,
        titleContent = { AppBarTitle(title) },
        searchQuery = searchQuery,
        onChangeSearchQuery = onSearchQueryChange,
        onSearch = onSearch,
        onClickCloseSearch = navigateUp,
        actions = {
            AppBarActions(
                actions = persistentListOf<AppBar.AppBarAction>().builder()
                    .apply {
                        if (displayMode != null) {
                            add(
                                AppBar.Action(
                                    title = stringResource(MR.strings.action_display_mode),
                                    icon = if (displayMode == LibraryDisplayMode.List) {
                                        Icons.AutoMirrored.Filled.ViewList
                                    } else {
                                        Icons.Filled.ViewModule
                                    },
                                    onClick = { selectingDisplayMode = true },
                                ),
                            )
                        }
                        // KMK -->
                        add(bulkSelectionButton(isRunning, toggleSelectionMode))
                        // KMK <--
                        if (isLocalSource) {
                            if (isConfigurableSource && displayMode != null) {
                                add(
                                    AppBar.OverflowAction(
                                        title = stringResource(MR.strings.label_help),
                                        onClick = onHelpClick,
                                    ),
                                )
                            } else {
                                add(
                                    AppBar.Action(
                                        title = stringResource(MR.strings.label_help),
                                        icon = Icons.AutoMirrored.Outlined.Help,
                                        onClick = onHelpClick,
                                    ),
                                )
                            }
                        } else {
                            if (isConfigurableSource && displayMode != null) {
                                add(
                                    AppBar.OverflowAction(
                                        title = stringResource(MR.strings.action_web_view),
                                        onClick = onWebViewClick,
                                    ),
                                )
                            } else {
                                add(
                                    AppBar.Action(
                                        title = stringResource(MR.strings.action_web_view),
                                        icon = Icons.Outlined.Public,
                                        onClick = onWebViewClick,
                                    ),
                                )
                            }
                        }
                        // SY <--
                        // KMK -->
                        onSettingsClick?.let {
                            // KMK <--
                            add(
                                AppBar.OverflowAction(
                                    title = stringResource(MR.strings.action_settings),
                                    onClick = onSettingsClick,
                                ),
                            )
                        }
                        add(
                            AppBar.OverflowAction(
                                title = stringResource(KMR.strings.block_rule_manage),
                                onClick = onBlockruleClick,
                            ),
                        )
                    }
                    .build(),
            )

            DropdownMenu(
                expanded = selectingDisplayMode,
                onDismissRequest = { selectingDisplayMode = false },
            ) {
                RadioMenuItem(
                    text = { Text(text = stringResource(MR.strings.action_display_comfortable_grid)) },
                    isChecked = displayMode == LibraryDisplayMode.ComfortableGrid,
                ) {
                    selectingDisplayMode = false
                    onDisplayModeChange(LibraryDisplayMode.ComfortableGrid)
                }
                // KMK -->
                RadioMenuItem(
                    text = { Text(text = stringResource(KMR.strings.action_display_comfortable_grid_panorama)) },
                    isChecked = displayMode == LibraryDisplayMode.ComfortableGridPanorama,
                ) {
                    selectingDisplayMode = false
                    onDisplayModeChange(LibraryDisplayMode.ComfortableGridPanorama)
                }
                // KMK <--
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
            }
        },
        scrollBehavior = scrollBehavior,
    )
}
