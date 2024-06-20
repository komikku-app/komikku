package eu.kanade.presentation.category.components.biometric

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eu.kanade.tachiyomi.ui.category.biometric.TimeRangeItem
import kotlinx.collections.immutable.ImmutableList
import tachiyomi.presentation.core.components.material.padding

@Composable
fun BiometricTimesContent(
    timeRanges: ImmutableList<TimeRangeItem>,
    lazyListState: LazyListState,
    paddingValues: PaddingValues,
    onClickDelete: (TimeRangeItem) -> Unit,
) {
    LazyColumn(
        state = lazyListState,
        contentPadding = paddingValues,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        items(timeRanges, key = { it.formattedString }) { timeRange ->
            BiometricTimesListItem(
                modifier = Modifier.animateItem(),
                timeRange = timeRange,
                onDelete = { onClickDelete(timeRange) },
            )
        }
    }
}
