package eu.kanade.tachiyomi.ui.browse.source.browse

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import eu.kanade.tachiyomi.source.model.Filter
import kotlinx.collections.immutable.toImmutableList
import tachiyomi.core.common.preference.TriState
import tachiyomi.presentation.core.components.CheckboxItem
import tachiyomi.presentation.core.components.CollapsibleBox
import tachiyomi.presentation.core.components.HeadingItem
import tachiyomi.presentation.core.components.SelectItem
import tachiyomi.presentation.core.components.SortItem
import tachiyomi.presentation.core.components.TextItem
import tachiyomi.presentation.core.components.TriStateItem

/*
 * KMK: the rows of a source's filter list, moved out of SourceFilterDialog.kt (Android's filter sheet) unchanged so
 * the desktop catalogue's filter panel shows the same rows (a `shared` borrowed source, see
 * app-shared/borrowed-app-sources.gradle.kts). One change: a select without options shows its name only, a state
 * outside its options shows the first one (SelectItem would throw on either).
 */

/** One filter of a source's [eu.kanade.tachiyomi.source.model.FilterList]; [onUpdate] after its state changed. */
@Composable
fun SourceFilterItem(filter: Filter<*>, onUpdate: () -> Unit, startExpanded: Boolean) {
    when (filter) {
        is Filter.AutoComplete -> {
            AutoCompleteItem(
                name = filter.name,
                state = filter.state.toImmutableList(),
                hint = filter.hint,
                values = filter.values.toImmutableList(),
                skipAutoFillTags = filter.skipAutoFillTags.toImmutableList(),
                validPrefixes = filter.validPrefixes.toImmutableList(),
            ) {
                filter.state = it
                onUpdate()
            }
        }
        is Filter.Header -> {
            HeadingItem(filter.name)
        }
        is Filter.Separator -> {
            HorizontalDivider()
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
            if (filter.values.isEmpty()) {
                HeadingItem(filter.name)
            } else {
                SelectItem(
                    label = filter.name,
                    options = filter.values,
                    selectedIndex = filter.state.takeIf { it in filter.values.indices } ?: 0,
                ) {
                    filter.state = it
                    onUpdate()
                }
            }
        }
        is Filter.Sort -> {
            CollapsibleBox(
                heading = filter.name,
                startExpanded = startExpanded,
            ) {
                Column {
                    filter.values.mapIndexed { index, item ->
                        val sortAscending = filter.state?.ascending
                            ?.takeIf { index == filter.state?.index }
                        SortItem(
                            label = item,
                            sortDescending = if (sortAscending != null) !sortAscending else null,
                            onClick = {
                                val ascending = if (index == filter.state?.index) {
                                    !filter.state!!.ascending
                                } else {
                                    filter.state?.ascending ?: true
                                }
                                filter.state = Filter.Sort.Selection(
                                    index = index,
                                    ascending = ascending,
                                )
                                onUpdate()
                            },
                        )
                    }
                }
            }
        }
        is Filter.Group<*> -> {
            CollapsibleBox(
                heading = filter.name,
                startExpanded = startExpanded,
            ) {
                Column {
                    filter.state
                        .filterIsInstance<Filter<*>>()
                        .map { SourceFilterItem(filter = it, onUpdate = onUpdate, startExpanded = startExpanded) }
                }
            }
        }
    }
}

private fun Int.toTriStateFilter(): TriState {
    return when (this) {
        Filter.TriState.STATE_IGNORE -> TriState.DISABLED
        Filter.TriState.STATE_INCLUDE -> TriState.ENABLED_IS
        Filter.TriState.STATE_EXCLUDE -> TriState.ENABLED_NOT
        else -> throw IllegalStateException("Unknown TriState state: $this")
    }
}

private fun TriState.toTriStateInt(): Int {
    return when (this) {
        TriState.DISABLED -> Filter.TriState.STATE_IGNORE
        TriState.ENABLED_IS -> Filter.TriState.STATE_INCLUDE
        TriState.ENABLED_NOT -> Filter.TriState.STATE_EXCLUDE
    }
}
