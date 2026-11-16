package app.komikku.shared.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.NavigatorDisposeBehavior
import eu.kanade.presentation.util.DefaultNavigatorScreenTransition

/**
 * Voyager navigator host shared by the desktop app (and usable by Android): a root [Navigator] over
 * [screen] with the app's default screen transition (`DefaultNavigatorScreenTransition`, borrowed from
 * `app`). Back navigation pops the stack through the transition's `BackHandler` (Escape on desktop).
 */
@Composable
fun AppNavigatorHost(
    screen: Screen,
    modifier: Modifier = Modifier,
    onNavigatorReady: (Navigator) -> Unit = {},
) {
    Navigator(
        screen = screen,
        disposeBehavior = NavigatorDisposeBehavior(disposeNestedNavigators = false, disposeSteps = true),
    ) { navigator ->
        onNavigatorReady(navigator)
        DefaultNavigatorScreenTransition(navigator = navigator, modifier = modifier)
    }
}
