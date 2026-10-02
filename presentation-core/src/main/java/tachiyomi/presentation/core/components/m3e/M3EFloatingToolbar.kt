@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package tachiyomi.presentation.core.components.m3e

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The floating toolbar of Material 3 Expressive.
 *
 * Use it for the actions that act on what the user selected. Give it the `bottomBar` slot of a
 * scaffold and drive `expanded` from the selection state.
 *
 * [content] holds the actions of the toolbar. [leadingContent] holds a count or a label, and
 * [trailingContent] holds the action that ends the mode, such as a close action.
 */
@Composable
fun ExpressiveFloatingToolbar(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    leadingContent: @Composable (RowScope.() -> Unit)? = null,
    trailingContent: @Composable (RowScope.() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) = HorizontalFloatingToolbar(
    expanded = expanded,
    // The toolbar is docked at the bottom, so it must clear the system navigation bar. The
    // scaffold does not do it for the bottom bar slot.
    modifier = modifier
        .padding(horizontal = FloatingToolbarDefaults.ScreenOffset)
        .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)),
    leadingContent = leadingContent,
    trailingContent = trailingContent,
    content = content,
)

/**
 * One action of an [ExpressiveFloatingToolbar].
 *
 * The icon button carries no label, so [title] becomes its content description and the accessible
 * name of the action.
 */
@Composable
fun FloatingToolbarAction(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) = IconButton(
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
) {
    Icon(
        imageVector = icon,
        contentDescription = title,
    )
}
