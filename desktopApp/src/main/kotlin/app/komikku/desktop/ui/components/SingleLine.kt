package app.komikku.desktop.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * M2: one line of text, cut with an ellipsis. For the labels of bars, tabs and search fields, which wrapped onto more
 * lines in a narrow window and made their bar taller.
 */
@Composable
fun SingleLineText(text: String, modifier: Modifier = Modifier, style: TextStyle? = null) {
    if (style == null) {
        Text(text, modifier = modifier, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    } else {
        Text(text, modifier = modifier, style = style, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * M2: a tab row that keeps one line per title. While every tab fits at an equal width ([tabsFitEqually]) it is the
 * fixed [PrimaryTabRow] (tabs spread over the width, as before); in a narrower window it is a [PrimaryScrollableTabRow]
 * that scrolls sideways, like Android's `ScrollableTabRow`, instead of wrapping the titles.
 */
@Composable
fun AdaptiveTabRow(
    titles: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    tabTestTag: (Int) -> String? = { null },
) {
    BoxWithConstraints(modifier = modifier) {
        val measurer = rememberTextMeasurer()
        val style = MaterialTheme.typography.titleSmall
        val density = LocalDensity.current
        val widest = remember(titles, style, density) {
            with(density) {
                titles.maxOfOrNull { measurer.measure(it, style, maxLines = 1, softWrap = false).size.width }?.toDp() ?: 0.dp
            }
        }
        val tabs = @Composable {
            titles.forEachIndexed { index, title ->
                Tab(
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                    text = { SingleLineText(title) },
                    modifier = tabTestTag(index)?.let { Modifier.testTag(it) } ?: Modifier,
                )
            }
        }
        if (tabsFitEqually(titles.size, widest, maxWidth)) {
            PrimaryTabRow(selectedTabIndex = selectedIndex, modifier = Modifier.testTag(TAB_ROW_FIXED_TAG)) { tabs() }
        } else {
            PrimaryScrollableTabRow(
                selectedTabIndex = selectedIndex,
                edgePadding = 0.dp,
                modifier = Modifier.testTag(TAB_ROW_SCROLLABLE_TAG),
            ) { tabs() }
        }
    }
}

/** Whether [count] tabs whose widest title is [widestTitle] fit side by side at equal widths into [available]. */
internal fun tabsFitEqually(count: Int, widestTitle: Dp, available: Dp): Boolean =
    count == 0 || available == Dp.Infinity || (widestTitle + TAB_HORIZONTAL_PADDING * 2) * count <= available

/** Material 3's horizontal padding inside a tab, on each side of its title. */
internal val TAB_HORIZONTAL_PADDING = 16.dp

const val TAB_ROW_FIXED_TAG = "adaptive-tab-row-fixed"
const val TAB_ROW_SCROLLABLE_TAG = "adaptive-tab-row-scrollable"
