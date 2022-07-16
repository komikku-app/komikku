package eu.kanade.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastMaxBy

@Composable
fun AroundLayout(
    modifier: Modifier = Modifier,
    startLayout: @Composable () -> Unit,
    endLayout: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    SubcomposeLayout(modifier) { constraints ->
        val layoutWidth = constraints.maxWidth

        val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)

        val startLayoutPlaceables = subcompose(AroundLayoutContent.Start, startLayout).fastMap {
            it.measure(looseConstraints)
        }

        val startLayoutWidth = startLayoutPlaceables.fastMaxBy { it.width }?.width ?: 0

        val endLayoutPlaceables = subcompose(AroundLayoutContent.End, endLayout).fastMap {
            it.measure(looseConstraints)
        }

        val endLayoutWidth = endLayoutPlaceables.fastMaxBy { it.width }?.width ?: 0

        val bodyContentWidth = layoutWidth - startLayoutWidth

        val bodyContentPlaceables = subcompose(AroundLayoutContent.MainContent) {
            Box(Modifier.padding(end = endLayoutWidth.toDp())) {
                content()
            }
        }.fastMap { it.measure(looseConstraints.copy(maxWidth = bodyContentWidth)) }

        val height = (startLayoutPlaceables + endLayoutPlaceables + bodyContentPlaceables).maxOfOrNull { it.height } ?: 0

        layout(constraints.maxWidth, height) {
            // Placing to control drawing order to match default elevation of each placeable

            bodyContentPlaceables.fastForEach {
                it.place(startLayoutWidth, 0)
            }
            startLayoutPlaceables.fastForEach {
                it.place(0, 0)
            }
            // The bottom bar is always at the bottom of the layout
            endLayoutPlaceables.fastForEach {
                it.place(layoutWidth - endLayoutWidth, 0)
            }
        }
    }
}

private enum class AroundLayoutContent { Start, MainContent, End }
