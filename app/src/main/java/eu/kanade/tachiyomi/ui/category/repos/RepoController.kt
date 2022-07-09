package eu.kanade.tachiyomi.ui.category.repos

import androidx.compose.runtime.Composable
import eu.kanade.presentation.category.SourceRepoScreen
import eu.kanade.tachiyomi.ui.base.controller.FullComposeController

/**
 * Controller to manage the categories for the users' library.
 */
class RepoController : FullComposeController<RepoPresenter>() {

    override fun createPresenter() = RepoPresenter()

    @Composable
    override fun ComposeContent() {
        SourceRepoScreen(
            presenter = presenter,
            navigateUp = router::popCurrentController,
        )
    }
}
