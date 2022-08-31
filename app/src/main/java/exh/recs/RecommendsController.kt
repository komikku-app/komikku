package exh.recs

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.core.os.bundleOf
import eu.kanade.domain.manga.model.Manga
import eu.kanade.presentation.browse.BrowseRecommendationsScreen
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.ui.base.controller.pushController
import eu.kanade.tachiyomi.ui.browse.source.SourcesController
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceController

/**
 * Controller that shows the latest manga from the catalogue. Inherit [BrowseSourceController].
 */
class RecommendsController(bundle: Bundle) : BrowseSourceController(bundle) {

    constructor(manga: Manga, source: CatalogueSource) : this(
        bundleOf(
            MANGA_ID to manga.id,
            SOURCE_ID_KEY to source.id,
        ),
    )

    override fun createPresenter(): RecommendsPresenter {
        return RecommendsPresenter(args.getLong(MANGA_ID), args.getLong(SOURCE_ID_KEY))
    }

    @Composable
    override fun ComposeContent() {
        BrowseRecommendationsScreen(
            presenter = presenter,
            navigateUp = { router.popCurrentController() },
            title = (presenter as RecommendsPresenter).manga!!.title,
            onMangaClick = { manga ->
                openSmartSearch(manga.ogTitle)
            },
        )
    }

    override fun initFilterSheet() {
        // No-op: we don't allow filtering in recs
    }

    private fun openSmartSearch(title: String) {
        val smartSearchConfig = SourcesController.SmartSearchConfig(title)
        router.pushController(
            SourcesController(
                bundleOf(
                    SourcesController.SMART_SEARCH_CONFIG to smartSearchConfig,
                ),
            ),
        )
    }

    companion object {
        const val MANGA_ID = "manga_id"
    }
}
