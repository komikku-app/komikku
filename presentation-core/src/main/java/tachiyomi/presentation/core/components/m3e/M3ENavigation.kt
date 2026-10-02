@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3ComponentOverrideApi::class)

package tachiyomi.presentation.core.components.m3e

import androidx.compose.material3.ExperimentalMaterial3ComponentOverrideApi
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailState
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The compact navigation bar of Material 3 Expressive.
 *
 * Use it below the medium width class. Above that class use [ExpressiveNavigationRail].
 */
@Composable
fun ExpressiveNavigationBar(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) = ShortNavigationBar(
    modifier = modifier,
    content = content,
)

/** One destination of [ExpressiveNavigationBar]. */
@Composable
fun ExpressiveNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: @Composable (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) = ShortNavigationBarItem(
    selected = selected,
    onClick = onClick,
    icon = icon,
    label = label,
    modifier = modifier,
    enabled = enabled,
)

/**
 * The navigation rail of Material 3 Expressive, for the medium width class and wider.
 *
 * The rail collapses to icons and expands to icons with labels. Drive it through [state].
 */
@Composable
fun ExpressiveNavigationRail(
    state: WideNavigationRailState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) = WideNavigationRail(
    modifier = modifier,
    state = state,
    content = content,
)

/** One destination of [ExpressiveNavigationRail]. */
@Composable
fun ExpressiveNavigationRailItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: @Composable (() -> Unit)?,
    railExpanded: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) = WideNavigationRailItem(
    selected = selected,
    onClick = onClick,
    icon = icon,
    label = label,
    railExpanded = railExpanded,
    modifier = modifier,
    enabled = enabled,
)

/**
 * Remembers the state of a rail.
 *
 * The state lives in saved instance state, not in a preference, and starts expanded. A caller
 * toggles it with `state.toggle()`.
 */
@Composable
fun rememberExpressiveNavigationRailState(
    initialValue: WideNavigationRailValue = WideNavigationRailValue.Expanded,
): WideNavigationRailState = rememberWideNavigationRailState(initialValue = initialValue)
