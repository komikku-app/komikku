package app.komikku.desktop.smoke

import app.komikku.desktop.ui.homeTabs
import app.komikku.desktop.ui.navigation.NavigatorRegistry
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.TabNavigator
import java.util.concurrent.ConcurrentHashMap

/**
 * The navigator registry the smoke mode installs: the home shell's tabs and the tab stacks, driven only with what the
 * UI itself does (select a rail destination, push one of the app's screens). Read and changed on the UI thread.
 */
internal class SmokeNavigators : NavigatorRegistry {

    @Volatile
    private var tabs: TabNavigator? = null

    private val stacks = ConcurrentHashMap<String, Navigator>()

    override fun registerTabs(tabNavigator: TabNavigator) {
        tabs = tabNavigator
    }

    override fun registerStack(root: Screen, navigator: Navigator) {
        stacks[root.key] = navigator
    }

    val hasTabs: Boolean get() = tabs != null

    /** The rail destination at [index] (rail order), like a click on it or Ctrl/Cmd+1…5. */
    fun selectTab(index: Int) {
        val tabs = checkNotNull(tabs) { "The home shell has not registered its tabs" }
        tabs.current = homeTabs[index]
    }

    fun currentTab(): Int? = tabs?.let { navigator -> homeTabs.indexOfFirst { it.key == navigator.current.key } }

    fun push(root: Screen, screen: Screen) {
        val stack = checkNotNull(stacks[root.key]) { "No navigator for ${root.key}; is its tab shown?" }
        stack.push(screen)
    }

    /** The screen on top of [root]'s stack. */
    fun top(root: Screen): Screen? = stacks[root.key]?.lastItemOrNull
}
