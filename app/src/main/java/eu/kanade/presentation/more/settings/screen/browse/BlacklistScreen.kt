package eu.kanade.presentation.more.settings.screen.browse

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.times
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.source.model.BlacklistedSeriesEntry
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.category.components.CategoryFloatingActionButton
import eu.kanade.presentation.components.AnimatedFloatingSearchBox
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.SOURCE_SEARCH_BOX_HEIGHT
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.util.lang.toBlacklistNormalizedTitle
import eu.kanade.tachiyomi.util.system.toast
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.components.material.topSmallPaddingValues
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.util.plus
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class BlacklistScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val sourcePreferences = remember { Injekt.get<SourcePreferences>() }
        val entries by sourcePreferences.blacklistedSeries().changes().collectAsState(sourcePreferences.blacklistedSeries().get())
        val lazyListState = rememberLazyListState()

        var searchQuery by rememberSaveable { mutableStateOf<String?>(null) }
        val filteredEntries = remember(entries, searchQuery) {
            val query = searchQuery
            if (query.isNullOrBlank()) {
                entries
            } else {
                entries.filter { it.originalTitle.contains(query, ignoreCase = true) }
            }
        }

        BackHandler(enabled = !searchQuery.isNullOrBlank()) {
            searchQuery = ""
        }

        val addDialogOpen = rememberSaveable { mutableStateOf(false) }
        val deleteEntryNormalizedTitle = rememberSaveable { mutableStateOf<String?>(null) }
        val deleteEntry = deleteEntryNormalizedTitle.value?.let { normalizedTitle ->
            entries.firstOrNull { it.normalizedTitle == normalizedTitle }
        }

        fun closeAddDialog() {
            addDialogOpen.value = false
        }

        fun promptDeleteEntry(entry: BlacklistedSeriesEntry) {
            deleteEntryNormalizedTitle.value = entry.normalizedTitle
        }

        fun clearDeleteEntry() {
            deleteEntryNormalizedTitle.value = null
        }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    navigateUp = navigator::pop,
                    title = stringResource(KMR.strings.pref_blacklist_series),
                    scrollBehavior = scrollBehavior,
                )
            },
            floatingActionButton = {
                CategoryFloatingActionButton(
                    lazyListState = lazyListState,
                    onCreate = { addDialogOpen.value = true },
                )
            },
        ) { paddingValues ->
            if (entries.isEmpty()) {
                EmptyScreen(
                    message = stringResource(KMR.strings.blacklist_empty),
                    modifier = Modifier.padding(paddingValues),
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    val density = LocalDensity.current
                    var searchBoxHeight by remember { mutableStateOf(SOURCE_SEARCH_BOX_HEIGHT) }

                    if (filteredEntries.isEmpty()) {
                        EmptyScreen(
                            message = stringResource(MR.strings.no_results_found),
                            modifier = Modifier.padding(paddingValues + PaddingValues(top = searchBoxHeight)),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            state = lazyListState,
                            contentPadding = paddingValues + topSmallPaddingValues +
                                PaddingValues(
                                    top = searchBoxHeight,
                                    start = MaterialTheme.padding.medium,
                                    end = MaterialTheme.padding.medium,
                                    bottom = MaterialTheme.padding.medium,
                                ),
                            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                        ) {
                            items(filteredEntries, key = { it.normalizedTitle }) { entry ->
                                BlacklistItem(
                                    entry = entry,
                                    onDelete = { promptDeleteEntry(entry) },
                                )
                            }
                        }
                    }

                    AnimatedFloatingSearchBox(
                        listState = lazyListState,
                        searchQuery = searchQuery,
                        onChangeSearchQuery = { searchQuery = it },
                        placeholderText = stringResource(MR.strings.action_search_hint),
                        modifier = Modifier
                            .padding(top = paddingValues.calculateTopPadding())
                            .background(MaterialTheme.colorScheme.background)
                            .padding(
                                horizontal = MaterialTheme.padding.medium,
                                vertical = MaterialTheme.padding.small,
                            )
                            .align(Alignment.TopCenter),
                        onGloballyPositioned = { layoutCoordinates ->
                            searchBoxHeight = with(density) { layoutCoordinates.size.height.toDp() + (2 * MaterialTheme.padding.small) }
                        },
                    )
                }
            }
        }

        if (addDialogOpen.value) {
            AddBlacklistEntryDialog(
                onDismissRequest = ::closeAddDialog,
                onAdd = { title ->
                    when {
                        title.isBlank() -> {
                            context.toast(KMR.strings.blacklist_invalid_title)
                        }
                        sourcePreferences.addBlacklistedSeries(title) -> {
                            closeAddDialog()
                        }
                        else -> {
                            context.toast(KMR.strings.blacklist_title_exists)
                        }
                    }
                },
            )
        }

        deleteEntry?.let { entry ->
            DeleteBlacklistEntryDialog(
                entry = entry,
                onDismissRequest = ::clearDeleteEntry,
                onDelete = {
                    sourcePreferences.removeBlacklistedSeries(entry.normalizedTitle)
                    clearDeleteEntry()
                },
            )
        }
    }
}

@Composable
private fun BlacklistItem(
    entry: BlacklistedSeriesEntry,
    onDelete: () -> Unit,
) {
    ElevatedCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.padding.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = Icons.AutoMirrored.Outlined.Label, contentDescription = null)
            Text(
                text = entry.originalTitle,
                modifier = Modifier
                    .padding(start = MaterialTheme.padding.medium)
                    .weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = onDelete) {
                Icon(imageVector = Icons.Outlined.Delete, contentDescription = null)
            }
        }
    }
}

@Composable
private fun AddBlacklistEntryDialog(
    onDismissRequest: () -> Unit,
    onAdd: (String) -> Unit,
) {
    var textFieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    val candidateTitle = textFieldValue.text.trim()
    val canAdd = candidateTitle.isNotEmpty() && candidateTitle.toBlacklistNormalizedTitle().isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(KMR.strings.blacklist_add_title)) },
        text = {
            OutlinedTextField(
                value = textFieldValue,
                onValueChange = { textFieldValue = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(text = stringResource(KMR.strings.blacklist_add_hint))
                },
            )
        },
        confirmButton = {
            TextButton(
                enabled = canAdd,
                onClick = {
                    onAdd(candidateTitle)
                },
            ) {
                Text(text = stringResource(MR.strings.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
    )
}

@Composable
private fun DeleteBlacklistEntryDialog(
    entry: BlacklistedSeriesEntry,
    onDismissRequest: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(MR.strings.are_you_sure)) },
        text = {
            Text(text = stringResource(KMR.strings.blacklist_delete_confirmation, entry.originalTitle))
        },
        confirmButton = {
            TextButton(onClick = onDelete) {
                Text(text = stringResource(MR.strings.action_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
    )
}
