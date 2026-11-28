package app.komikku.desktop.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import app.komikku.shared.navigation.AppNavigatorHost
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.TabNavigator

/**
 * The app's navigators, handed to the in-app UI smoke mode (`--ui-smoke`, `app.komikku.desktop.smoke.UiSmoke`) so it
 * drives the same navigation the UI performs: the home shell's tabs and the stacks of the tabs that host one.
 *
 * Inert in a normal run (ATLAS_INSTRUCTIONS.md ANSWER 011, condition 1): [LocalNavigatorRegistry]'s default forwards
 * to the registry the smoke mode [install]s and does nothing while none is installed, so a normal start keeps and
 * exposes no navigator.
 */
internal interface NavigatorRegistry {

    /** The home shell's tab navigator (rail order: `homeTabs`). */
    fun registerTabs(tabNavigator: TabNavigator)

    /** The stack navigator whose root screen is [root]. */
    fun registerStack(root: Screen, navigator: Navigator)

    companion object {

        /** The registry the smoke mode installed, or `null` (always in a normal run). */
        @Volatile
        var installed: NavigatorRegistry? = null
            private set

        /** Installs [registry] for the rest of the process; only the smoke mode does, before the window exists. */
        fun install(registry: NavigatorRegistry) {
            check(installed == null) { "A navigator registry is already installed" }
            installed = registry
        }

        /** Tests only: removes the installed registry again. */
        fun uninstall() {
            installed = null
        }
    }
}

/** Forwards to [NavigatorRegistry.installed]; nothing happens and nothing is kept while none is installed. */
private object InstalledNavigatorRegistry : NavigatorRegistry {
    override fun registerTabs(tabNavigator: TabNavigator) {
        NavigatorRegistry.installed?.registerTabs(tabNavigator)
    }

    override fun registerStack(root: Screen, navigator: Navigator) {
        NavigatorRegistry.installed?.registerStack(root, navigator)
    }
}

internal val LocalNavigatorRegistry = staticCompositionLocalOf<NavigatorRegistry> { InstalledNavigatorRegistry }

/** [AppNavigatorHost] over [screen], its navigator registered in [LocalNavigatorRegistry]. */
@Composable
internal fun RegisteredNavigatorHost(screen: Screen) {
    val registry = LocalNavigatorRegistry.current
    AppNavigatorHost(screen = screen, onNavigatorReady = { registry.registerStack(screen, it) })
}
