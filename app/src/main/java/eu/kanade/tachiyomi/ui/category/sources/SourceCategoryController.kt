package eu.kanade.tachiyomi.ui.category.sources

import androidx.compose.runtime.Composable
import eu.kanade.presentation.category.SourceCategoryScreen
import eu.kanade.tachiyomi.ui.base.controller.FullComposeController

/**
 * Controller to manage the categories for the users' library.
 */
class SourceCategoryController : FullComposeController<SourceCategoryPresenter>() {

    override fun createPresenter() = SourceCategoryPresenter()

    @Composable
    override fun ComposeContent() {
        SourceCategoryScreen(
            presenter = presenter,
            navigateUp = router::popCurrentController,
        )
    }
}
