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
 * KMK: desktop-only copy. Follows `master`'s app/src/main/java/eu/kanade/tachiyomi/ui/browse/source/browse/
 * SourceFilterDialog.kt (`FilterItem` and the tri-state conversions below it), last synced to `master` 15610e67e4
 * (docs/kmp-desktop/UPSTREAM_AUDIT.md lists the desktop copies to re-sync at each merge of `master`).
 *
 * The rows of a source's filter list, for the desktop catalogue's filter panel. The body is `master`'s, markers
 * included, so a re-sync is a plain diff; only the name and visibility differ (`private fun FilterItem`).
 */

/** One filter of a source's [eu.kanade.tachiyomi.source.model.FilterList]; [onUpdate] after its state changed. */
@Composable
fun SourceFilterItem(filter: Filter<*>, onUpdate: () -> Unit/* SY --> */, startExpanded: Boolean /* SY <-- */) {
    when (filter) {
        // SY -->
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
        // SY <--
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
                // SY -->
                startExpanded = startExpanded,
                // SY <--
            ) {
                Column {
                    filter.state
                        .filterIsInstance<Filter<*>>()
                        .map { SourceFilterItem(filter = it, onUpdate = onUpdate /* SY --> */, startExpanded /* SY <-- */) }
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
        else -> TriState.DISABLED
    }
}

private fun TriState.toTriStateInt(): Int {
    return when (this) {
        TriState.DISABLED -> Filter.TriState.STATE_IGNORE
        TriState.ENABLED_IS -> Filter.TriState.STATE_INCLUDE
        TriState.ENABLED_NOT -> Filter.TriState.STATE_EXCLUDE
    }
}
