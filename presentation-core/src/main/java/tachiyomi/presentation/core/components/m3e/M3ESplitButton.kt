@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package tachiyomi.presentation.core.components.m3e

import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * A split button: a primary action beside a related action.
 *
 * The leading half holds the primary action. The trailing half holds the action that belongs with
 * it, such as a variant of the same task. Use a split button only when the two actions belong
 * together; an unrelated action belongs in a toolbar or a menu.
 */
@Composable
fun ExpressiveSplitButton(
    leadingButton: @Composable () -> Unit,
    trailingButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) = SplitButtonLayout(
    leadingButton = leadingButton,
    trailingButton = trailingButton,
    modifier = modifier,
)

/**
 * The icon action of the trailing half of an [ExpressiveSplitButton].
 *
 * The colours follow the filled style, so the pair reads as one control.
 */
@Composable
fun SplitButtonSecondaryAction(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ),
) = SplitButtonDefaults.TrailingButton(
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    colors = colors,
) {
    Icon(
        imageVector = icon,
        contentDescription = title,
        modifier = Modifier.size(SplitButtonDefaults.TrailingIconSize),
    )
}
