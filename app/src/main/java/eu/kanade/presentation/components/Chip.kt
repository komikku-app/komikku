package eu.kanade.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ChipBorder
import androidx.compose.material3.ChipColors
import androidx.compose.material3.ChipElevation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@ExperimentalMaterial3Api
@Composable
fun SuggestionChip(
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: @Composable (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    elevation: ChipElevation? = SuggestionChipDefaults.suggestionChipElevation(),
    shape: Shape = MaterialTheme.shapes.small,
    border: ChipBorder? = SuggestionChipDefaults.suggestionChipBorder(),
    colors: ChipColors = SuggestionChipDefaults.suggestionChipColors(),
) = Chip(
    modifier = modifier,
    label = label,
    labelTextStyle = MaterialTheme.typography.labelLarge,
    labelColor = colors.labelColor(enabled).value,
    leadingIcon = icon,
    avatar = null,
    trailingIcon = null,
    leadingIconColor = colors.leadingIconContentColor(enabled).value,
    trailingIconColor = colors.trailingIconContentColor(enabled).value,
    containerColor = colors.containerColor(enabled).value,
    tonalElevation = elevation?.tonalElevation(enabled, interactionSource)?.value ?: 0.dp,
    shadowElevation = elevation?.shadowElevation(enabled, interactionSource)?.value ?: 0.dp,
    minHeight = SuggestionChipDefaults.Height,
    paddingValues = SuggestionChipPadding,
    shape = shape,
    border = border?.borderStroke(enabled)?.value,
)

@ExperimentalMaterial3Api
@Composable
fun SuggestionChip(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: @Composable (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    elevation: ChipElevation? = SuggestionChipDefaults.suggestionChipElevation(),
    shape: Shape = MaterialTheme.shapes.small,
    border: ChipBorder? = SuggestionChipDefaults.suggestionChipBorder(),
    colors: ChipColors = SuggestionChipDefaults.suggestionChipColors(),
) = Chip(
    modifier = modifier,
    onClick = onClick,
    onLongClick = onLongClick,
    enabled = enabled,
    label = label,
    labelTextStyle = MaterialTheme.typography.labelLarge,
    labelColor = colors.labelColor(enabled).value,
    leadingIcon = icon,
    avatar = null,
    trailingIcon = null,
    leadingIconColor = colors.leadingIconContentColor(enabled).value,
    trailingIconColor = colors.trailingIconContentColor(enabled).value,
    containerColor = colors.containerColor(enabled).value,
    tonalElevation = elevation?.tonalElevation(enabled, interactionSource)?.value ?: 0.dp,
    shadowElevation = elevation?.shadowElevation(enabled, interactionSource)?.value ?: 0.dp,
    minHeight = SuggestionChipDefaults.Height,
    paddingValues = SuggestionChipPadding,
    shape = shape,
    border = border?.borderStroke(enabled)?.value,
    interactionSource = interactionSource,
)

@Suppress("SameParameterValue")
@ExperimentalMaterial3Api
@Composable
private fun Chip(
    modifier: Modifier,
    label: @Composable () -> Unit,
    labelTextStyle: TextStyle,
    labelColor: Color,
    leadingIcon: @Composable (() -> Unit)?,
    avatar: @Composable (() -> Unit)?,
    trailingIcon: @Composable (() -> Unit)?,
    leadingIconColor: Color,
    trailingIconColor: Color,
    containerColor: Color,
    tonalElevation: Dp,
    shadowElevation: Dp,
    minHeight: Dp,
    paddingValues: PaddingValues,
    shape: Shape,
    border: BorderStroke?,
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = containerColor,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        border = border,
    ) {
        ChipContent(
            label = label,
            labelTextStyle = labelTextStyle,
            labelColor = labelColor,
            leadingIcon = leadingIcon,
            avatar = avatar,
            trailingIcon = trailingIcon,
            leadingIconColor = leadingIconColor,
            trailingIconColor = trailingIconColor,
            minHeight = minHeight,
            paddingValues = paddingValues,
        )
    }
}

@Suppress("SameParameterValue")
@ExperimentalMaterial3Api
@Composable
private fun Chip(
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    enabled: Boolean,
    label: @Composable () -> Unit,
    labelTextStyle: TextStyle,
    labelColor: Color,
    leadingIcon: @Composable (() -> Unit)?,
    avatar: @Composable (() -> Unit)?,
    trailingIcon: @Composable (() -> Unit)?,
    leadingIconColor: Color,
    trailingIconColor: Color,
    containerColor: Color,
    tonalElevation: Dp,
    shadowElevation: Dp,
    minHeight: Dp,
    paddingValues: PaddingValues,
    shape: Shape,
    border: BorderStroke?,
    interactionSource: MutableInteractionSource,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        onLongClick = onLongClick,
        enabled = enabled,
        shape = shape,
        color = containerColor,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        border = border,
        interactionSource = interactionSource,
    ) {
        ChipContent(
            label = label,
            labelTextStyle = labelTextStyle,
            labelColor = labelColor,
            leadingIcon = leadingIcon,
            avatar = avatar,
            trailingIcon = trailingIcon,
            leadingIconColor = leadingIconColor,
            trailingIconColor = trailingIconColor,
            minHeight = minHeight,
            paddingValues = paddingValues,
        )
    }
}

@Composable
private fun ChipContent(
    label: @Composable () -> Unit,
    labelTextStyle: TextStyle,
    labelColor: Color,
    leadingIcon: @Composable (() -> Unit)?,
    avatar: @Composable (() -> Unit)?,
    trailingIcon: @Composable (() -> Unit)?,
    leadingIconColor: Color,
    trailingIconColor: Color,
    minHeight: Dp,
    paddingValues: PaddingValues,
) {
    CompositionLocalProvider(
        LocalContentColor provides labelColor,
        LocalTextStyle provides labelTextStyle,
    ) {
        Row(
            Modifier.defaultMinSize(minHeight = minHeight).padding(paddingValues),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (avatar != null) {
                avatar()
            } else if (leadingIcon != null) {
                CompositionLocalProvider(
                    LocalContentColor provides leadingIconColor,
                    content = leadingIcon,
                )
            }
            Spacer(Modifier.width(HorizontalElementsPadding))
            label()
            Spacer(Modifier.width(HorizontalElementsPadding))
            if (trailingIcon != null) {
                CompositionLocalProvider(
                    LocalContentColor provides trailingIconColor,
                    content = trailingIcon,
                )
            }
        }
    }
}

/**
 * The padding between the elements in the chip.
 */
private val HorizontalElementsPadding = 8.dp

/**
 * Returns the [PaddingValues] for the suggestion chip.
 */
private val SuggestionChipPadding = PaddingValues(horizontal = HorizontalElementsPadding)
