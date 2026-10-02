package tachiyomi.presentation.core.components.m3e

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import tachiyomi.presentation.core.components.material.Surface as ClickableSurface

private val ChipPadding = PaddingValues(horizontal = 8.dp)
private val ChipMinHeight = 32.dp

/**
 * A suggestion chip that also answers a long press.
 *
 * Material 3 gives [androidx.compose.material3.SuggestionChip] no long-press parameter, and the
 * saved-search list needs one for its delete action. The chip is therefore drawn on the shared
 * clickable surface, with the shape, the height and the type of a Material 3 chip.
 */
@Composable
fun ExpressiveSuggestionChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    border: BorderStroke? = null,
    containerColor: Color = Color.Transparent,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    contentPadding: PaddingValues = ChipPadding,
) = ClickableSurface(
    onClick = onClick,
    modifier = modifier,
    onLongClick = onLongClick,
    enabled = enabled,
    shape = MaterialTheme.shapes.small,
    color = containerColor,
    contentColor = labelColor,
    border = border,
) {
    ChipContent(
        label = label,
        contentPadding = contentPadding,
    )
}

/**
 * A tag that is not a link.
 *
 * The tag keeps the shape, the height and the type of a chip, but it holds no click action, so it
 * is a plain surface. Use a chip when the tag does something.
 */
@Composable
fun StaticTagChip(
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    border: BorderStroke? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    contentPadding: PaddingValues = ChipPadding,
) = Surface(
    modifier = modifier,
    shape = MaterialTheme.shapes.small,
    color = containerColor,
    contentColor = labelColor,
    border = border,
) {
    ChipContent(
        label = label,
        contentPadding = contentPadding,
    )
}

@Composable
private fun ChipContent(
    label: @Composable () -> Unit,
    contentPadding: PaddingValues,
) {
    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current) {
        ProvideTextStyle(value = MaterialTheme.typography.labelLarge) {
            Row(
                modifier = Modifier
                    .defaultMinSize(minHeight = ChipMinHeight)
                    .padding(contentPadding),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                label()
            }
        }
    }
}
