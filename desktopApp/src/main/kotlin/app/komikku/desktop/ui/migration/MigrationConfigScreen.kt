package app.komikku.desktop.ui.migration

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Deselect
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.komikku.desktop.ui.browse.BrowseScaffold
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.util.Screen
import mihon.feature.migration.config.MigrationConfigScreenModel
import mihon.feature.migration.config.MigrationConfigScreenSheet
import mihon.feature.migration.config.MigrationSource
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.icons.FlagEmoji
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * P19: Android's `MigrationConfigScreen` (Mihon / Komikku) on desktop — which sources the migration searches, in which
 * order, on the borrowed [MigrationConfigScreenModel] (same `migrationSources` preference: preselected from it, else the
 * pinned sources, else the enabled ones; every change saved at once): the selected sources first, then the available
 * ones, a click toggles, "select pinned / none / enabled / all", the source search ("," for several, an id works too).
 * "Continue" opens the borrowed options sheet (`MigrationConfigScreenSheet`: what to migrate, extra search query, hide
 * unmatched / without updates, smart search for one entry, deep search, prioritise by chapters) and then the migration
 * list replaces this screen, like Android.
 *
 * Desktop deviation: the selected sources are ordered with "move up / down" buttons instead of Android's drag handle.
 */
class MigrationConfigScreen(internal val mangaIds: Collection<Long>) : Screen() {

    constructor(mangaId: Long) : this(listOf(mangaId))

    @Composable
    override fun Content() {
        // Review fix (P19): the model reads the sources once, so it is created only once they are loaded
        val sourceManager = remember { Injekt.get<SourceManager>() }
        val sourcesLoaded by sourceManager.isInitialized.collectAsState()
        if (!sourcesLoaded) {
            LoadingScreen()
            return
        }
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel { MigrationConfigScreenModel() }
        val state by model.state.collectAsState()
        var searchQuery by remember { mutableStateOf("") }
        var sheetOpen by remember { mutableStateOf(false) }
        var menuOpen by remember { mutableStateOf(false) }

        BrowseScaffold(
            title = stringResource(SYMR.strings.select_sources),
            onBack = navigator::pop,
            actions = {
                IconButton(
                    onClick = { model.toggleSelection(MigrationConfigScreenModel.SelectionConfig.Pinned) },
                    modifier = Modifier.testTag("migration-config-pinned"),
                ) {
                    Icon(Icons.Outlined.PushPin, contentDescription = stringResource(MR.strings.migrationConfigScreen_selectPinnedLabel))
                }
                IconButton(
                    onClick = { model.toggleSelection(MigrationConfigScreenModel.SelectionConfig.None) },
                    modifier = Modifier.testTag("migration-config-none"),
                ) {
                    Icon(Icons.Outlined.Deselect, contentDescription = stringResource(MR.strings.migrationConfigScreen_selectNoneLabel))
                }
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.testTag("migration-config-more")) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(MR.strings.migrationConfigScreen_selectEnabledLabel)) },
                            onClick = {
                                menuOpen = false
                                model.toggleSelection(MigrationConfigScreenModel.SelectionConfig.Enabled)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(MR.strings.migrationConfigScreen_selectAllLabel)) },
                            onClick = {
                                menuOpen = false
                                model.toggleSelection(MigrationConfigScreenModel.SelectionConfig.All)
                            },
                        )
                    }
                }
            },
        ) {
            if (state.isLoading) {
                LoadingScreen()
                return@BrowseScaffold
            }
            val (selected, available) = state.sources
                .filter { it.matches(searchQuery) }
                .partition { it.isSelected }
            val showLanguage = remember(state.sources) { state.sources.distinctBy { it.source.lang }.size > 1 }
            Column(modifier = Modifier.fillMaxSize().testTag("migration-config")) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(KMR.strings.action_search_for_source)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("migration-config-search"),
                )
                Box(modifier = Modifier.weight(1f)) {
                    // Review fix (P19): room under the last row for the Continue button, like Android's scaffold padding
                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = FAB_SPACE)) {
                        if (selected.isNotEmpty()) {
                            item(key = "selected-header") { Header(stringResource(MR.strings.migrationConfigScreen_selectedHeader)) }
                            items(selected, key = { it.id }) { source ->
                                // Positions in the whole list (the selected sources come first in it)
                                val index = state.sources.indexOfFirst { it.id == source.id }
                                val previous = state.sources.getOrNull(index - 1)?.takeIf { it.isSelected }
                                val next = state.sources.getOrNull(index + 1)?.takeIf { it.isSelected }
                                SourceRow(
                                    source = source,
                                    showLanguage = showLanguage,
                                    onClick = { model.toggleSelection(source.id) },
                                    onMoveUp = { model.orderSource(index, index - 1) }.takeIf { previous != null && searchQuery.isBlank() },
                                    onMoveDown = { model.orderSource(index, index + 1) }.takeIf { next != null && searchQuery.isBlank() },
                                )
                            }
                        }
                        if (available.isNotEmpty()) {
                            item(key = "available-header") { Header(stringResource(MR.strings.migrationConfigScreen_availableHeader)) }
                            items(available, key = { "available-${it.id}" }) { source ->
                                SourceRow(
                                    source = source,
                                    showLanguage = showLanguage,
                                    onClick = { model.toggleSelection(source.id) },
                                    onMoveUp = null,
                                    onMoveDown = null,
                                )
                            }
                        }
                    }
                    if (selected.isNotEmpty() || state.sources.any { it.isSelected }) {
                        ExtendedFloatingActionButton(
                            text = { Text(stringResource(MR.strings.migrationConfigScreen_continueButtonText)) },
                            icon = { Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null) },
                            onClick = {
                                model.saveSources()
                                sheetOpen = true
                            },
                            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).testTag("migration-config-continue"),
                        )
                    }
                }
            }
        }

        if (sheetOpen) {
            MigrationConfigScreenSheet(
                preferences = model.sourcePreferences,
                onDismissRequest = { sheetOpen = false },
                onStartMigration = { extraSearchQuery ->
                    sheetOpen = false
                    navigator.replace(
                        MigrationListScreen(
                            mangaIds = mangaIds,
                            extraSearchQuery = extraSearchQuery,
                            isSmartSearchSingleEntry = model.sourcePreferences.migrationSmartSearchSingleEntry().get(),
                        ),
                    )
                },
                isSingleEntry = mangaIds.size == 1,
            )
        }
    }
}

/** The Continue button's height (56) and its margins (16 each side). */
private val FAB_SPACE = 88.dp

/** Android's source search of the screen: any of the ","-separated parts in the name, or a source id. */
private fun MigrationSource.matches(query: String): Boolean {
    if (query.isBlank()) return true
    return query.split(",").any {
        val input = it.trim()
        input.isNotEmpty() && (source.name.contains(input, ignoreCase = true) || source.id == input.toLongOrNull())
    }
}

@Composable
private fun Header(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
}

@Composable
private fun SourceRow(
    source: MigrationSource,
    showLanguage: Boolean,
    onClick: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .testTag("migration-config-source-${source.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(checked = source.isSelected, onCheckedChange = null)
        Text(
            text = source.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        if (showLanguage) {
            Text(
                text = FlagEmoji.getEmojiLangFlag(source.shortLanguage) + " (${source.upperShortLanguage})",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (source.isSelected) {
            IconButton(
                onClick = { onMoveUp?.invoke() },
                enabled = onMoveUp != null,
                modifier = Modifier.testTag("migration-config-up-${source.id}"),
            ) {
                Icon(Icons.Outlined.ArrowUpward, contentDescription = stringResource(KMR.strings.action_move_up))
            }
            IconButton(
                onClick = { onMoveDown?.invoke() },
                enabled = onMoveDown != null,
                modifier = Modifier.testTag("migration-config-down-${source.id}"),
            ) {
                Icon(Icons.Outlined.ArrowDownward, contentDescription = stringResource(KMR.strings.action_move_down))
            }
        }
    }
}
