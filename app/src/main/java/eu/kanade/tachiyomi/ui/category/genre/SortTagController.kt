package eu.kanade.tachiyomi.ui.category.genre

import androidx.compose.runtime.Composable
import eu.kanade.presentation.category.SortTagScreen
import eu.kanade.tachiyomi.ui.base.controller.FullComposeController

/**
 * Controller to manage the categories for the users' library.
 */
class SortTagController : FullComposeController<SortTagPresenter>() {

    override fun createPresenter() = SortTagPresenter()

    @Composable
    override fun ComposeContent() {
        SortTagScreen(
            presenter = presenter,
            navigateUp = router::popCurrentController,
        )
    }
}
