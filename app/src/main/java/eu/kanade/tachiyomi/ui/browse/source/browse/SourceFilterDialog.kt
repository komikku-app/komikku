package eu.kanade.tachiyomi.ui.browse.source.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.collections.immutable.ImmutableList
import tachiyomi.domain.source.model.EXHSavedSearch
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.Scroller.STICKY_HEADER_KEY_PREFIX
import tachiyomi.presentation.core.components.material.Button
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun SourceFilterDialog(
    onDismissRequest: () -> Unit,
    filters: FilterList,
    onReset: () -> Unit,
    onFilter: () -> Unit,
    onUpdate: (FilterList) -> Unit,
    // SY -->
    startExpanded: Boolean,
    savedSearches: ImmutableList<EXHSavedSearch>,
    onSave: () -> Unit,
    onSavedSearch: (EXHSavedSearch) -> Unit,
    onSavedSearchPress: (EXHSavedSearch) -> Unit,
    // KMK -->
    onSavedSearchPressDesc: String,
    shouldShowSavingButton: Boolean = true,
    // KMK <--
    openMangaDexRandom: (() -> Unit)?,
    openMangaDexFollows: (() -> Unit)?,
    // SY <--
) {
    val updateFilters = { onUpdate(filters) }

    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        LazyColumn {
            stickyHeader(
                key = "$STICKY_HEADER_KEY_PREFIX-title",
            ) {
                Row(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.background)
                        .padding(8.dp),
                ) {
                    TextButton(onClick = onReset) {
                        Text(
                            text = stringResource(MR.strings.action_reset),
                            style = LocalTextStyle.current.copy(
                                color = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // KMK -->
                    if (shouldShowSavingButton) {
                        // KMK <--
                        // SY -->
                        IconButton(onClick = onSave) {
                            Icon(
                                Icons.Default.Save,
                                contentDescription = stringResource(MR.strings.action_save),
                                tint = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                        // SY <--
                    }
                    Button(onClick = {
                        onFilter()
                        onDismissRequest()
                    }) {
                        Text(stringResource(MR.strings.action_filter))
                    }
                }
                HorizontalDivider()
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
                    // KMK -->
                    onSavedSearchPressDesc = onSavedSearchPressDesc,
                    // KMK <--
                )
            }

            items(filters) {
                // KMK -->
                SourceFilterItem(it, updateFilters, startExpanded)
                // KMK <--
            }
        }
    }
}

// KMK --> FilterItem and its tri-state helpers moved to SourceFilterItems.kt (SourceFilterItem), shared with the desktop
// KMK <--
