package eu.kanade.tachiyomi.ui.category.repos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.presentation.util.LocalRouter
import eu.kanade.tachiyomi.ui.base.controller.BasicFullComposeController

/**
 * Controller to manage the categories for the users' library.
 */
class RepoController : BasicFullComposeController() {

    @Composable
    override fun ComposeContent() {
        CompositionLocalProvider(LocalRouter provides router) {
            Navigator(screen = RepoScreen())
        }
    }
}
