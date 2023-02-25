package eu.kanade.tachiyomi.ui.browse.source.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.components.SelectItem
import eu.kanade.presentation.components.TriStateItem
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import tachiyomi.domain.manga.model.TriStateFilter
import tachiyomi.domain.source.model.EXHSavedSearch
import tachiyomi.presentation.core.components.CheckboxItem
import tachiyomi.presentation.core.components.CollapsibleBox
import tachiyomi.presentation.core.components.HeadingItem
import tachiyomi.presentation.core.components.LazyColumn
import tachiyomi.presentation.core.components.SortItem
import tachiyomi.presentation.core.components.TextItem
import tachiyomi.presentation.core.components.material.Button
import tachiyomi.presentation.core.components.material.Divider

@Composable
fun SourceFilterDialog(
    onDismissRequest: () -> Unit,
    filters: FilterList,
    onReset: () -> Unit,
    onFilter: () -> Unit,
    onUpdate: (FilterList) -> Unit,
    // SY -->
    startExpanded: Boolean,
    savedSearches: List<EXHSavedSearch>,
    onSave: () -> Unit,
    onSavedSearch: (EXHSavedSearch) -> Unit,
    onSavedSearchPress: (EXHSavedSearch) -> Unit,
    openMangaDexRandom: (() -> Unit)?,
    openMangaDexFollows: (() -> Unit)?,
    // SY <--
) {
    val updateFilters = { onUpdate(filters) }

    AdaptiveSheet(
        onDismissRequest = onDismissRequest,
    ) { contentPadding ->
        LazyColumn(
            contentPadding = contentPadding,
        ) {
            stickyHeader {
                Row(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.background)
                        .padding(8.dp),
                ) {
                    TextButton(onClick = onReset) {
                        Text(
                            text = stringResource(R.string.action_reset),
                            style = LocalTextStyle.current.copy(
                                color = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // SY -->
                    IconButton(onClick = onSave) {
                        Icon(
                            Icons.Default.Save,
                            contentDescription = stringResource(R.string.action_save),
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    // SY <--
                    Button(onClick = {
                        onFilter()
                        onDismissRequest()
                    },) {
                        Text(stringResource(R.string.action_filter))
                    }
                }
                Divider()
            }

            if (openMangaDexRandom != null && openMangaDexFollows != null) {
                item {
                    MangaDexFilterHeader(
                        openMangaDexRandom = openMangaDexRandom,
                        openMangaDexFollows = openMangaDexFollows,
                    )
                }
            }

            item {
                SavedSearchItem(
                    savedSearches = savedSearches,
                    onSavedSearch = onSavedSearch,
                    onSavedSearchPress = onSavedSearchPress,
                )
            }

            items(filters) {
                FilterItem(it, updateFilters /* SY --> */, startExpanded /* SY <-- */)
            }
        }
    }
}

@Composable
private fun FilterItem(filter: Filter<*>, onUpdate: () -> Unit/* SY --> */, startExpanded: Boolean /* SY <-- */) {
    when (filter) {
        // SY -->
        is Filter.AutoComplete -> {
            AutoCompleteItem(
                name = filter.name,
                state = filter.state,
                hint = filter.hint,
                values = filter.values,
                skipAutoFillTags = filter.skipAutoFillTags,
                validPrefixes = filter.validPrefixes,
            ) {
                filter.state = it
                onUpdate()
            }
        }
        // SY <--
        is Filter.Header -> {
            HeadingItem(filter.name)
        }
        is Filter.Separator -> {
            Divider()
        }
        is Filter.CheckBox -> {
            CheckboxItem(
                label = filter.name,
                checked = filter.state,
            ) {
                filter.state = !filter.state
                onUpdate()
            }
        }
        is Filter.TriState -> {
            TriStateItem(
                label = filter.name,
                state = filter.state.toTriStateFilter(),
            ) {
                filter.state = filter.state.toTriStateFilter().next().toTriStateInt()
                onUpdate()
            }
        }
        is Filter.Text -> {
            TextItem(
                label = filter.name,
                value = filter.state,
            ) {
                filter.state = it
                onUpdate()
            }
        }
        is Filter.Select<*> -> {
            SelectItem(
                label = filter.name,
                options = filter.values,
                selectedIndex = filter.state,
            ) {
                filter.state = it
                onUpdate()
            }
        }
        is Filter.Sort -> {
            CollapsibleBox(
                heading = filter.name,
                // SY -->
                startExpanded = startExpanded,
                // SY <--
            ) {
                Column {
                    filter.values.mapIndexed { index, item ->
                        SortItem(
                            label = item,
                            sortDescending = filter.state?.ascending?.not()
                                ?.takeIf { index == filter.state?.index },
                        ) {
                            val ascending = if (index == filter.state?.index) {
                                !filter.state!!.ascending
                            } else {
                                filter.state!!.ascending
                            }
                            filter.state = Filter.Sort.Selection(
                                index = index,
                                ascending = ascending,
                            )
                            onUpdate()
                        }
                    }
                }
            }
        }
        is Filter.Group<*> -> {
            CollapsibleBox(
                heading = filter.name,
                // SY -->
                startExpanded = startExpanded,
                // SY <--
            ) {
                Column {
                    filter.state
                        .filterIsInstance<Filter<*>>()
                        .map { FilterItem(filter = it, onUpdate = onUpdate /* SY --> */, startExpanded /* SY <-- */) }
                }
            }
        }
    }
}

private fun Int.toTriStateFilter(): TriStateFilter {
    return when (this) {
        Filter.TriState.STATE_IGNORE -> TriStateFilter.DISABLED
        Filter.TriState.STATE_INCLUDE -> TriStateFilter.ENABLED_IS
        Filter.TriState.STATE_EXCLUDE -> TriStateFilter.ENABLED_NOT
        else -> throw IllegalStateException("Unknown TriState state: $this")
    }
}

private fun TriStateFilter.toTriStateInt(): Int {
    return when (this) {
        TriStateFilter.DISABLED -> Filter.TriState.STATE_IGNORE
        TriStateFilter.ENABLED_IS -> Filter.TriState.STATE_INCLUDE
        TriStateFilter.ENABLED_NOT -> Filter.TriState.STATE_EXCLUDE
    }
}
