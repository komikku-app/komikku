package exh.md.similar

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.core.os.bundleOf
import eu.kanade.domain.manga.model.Manga
import eu.kanade.presentation.browse.BrowseRecommendationsScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.ui.base.controller.pushController
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceController
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourcePresenter
import eu.kanade.tachiyomi.ui.manga.MangaController

/**
 * Controller that shows the latest manga from the catalogue. Inherit [BrowseSourceController].
 */
class MangaDexSimilarController(bundle: Bundle) : BrowseSourceController(bundle) {

    constructor(manga: Manga, source: CatalogueSource) : this(
        bundleOf(
            MANGA_ID to manga.id,
            MANGA_TITLE to manga.title,
            SOURCE_ID_KEY to source.id,
        ),
    )

    private val mangaTitle = args.getString(MANGA_TITLE, "")

    override fun createPresenter(): BrowseSourcePresenter {
        return MangaDexSimilarPresenter(args.getLong(MANGA_ID), args.getLong(SOURCE_ID_KEY))
    }

    @Composable
    override fun ComposeContent() {
        BrowseRecommendationsScreen(
            presenter = presenter,
            navigateUp = { router.popCurrentController() },
            title = stringResource(R.string.similar, mangaTitle),
            onMangaClick = {
                router.pushController(MangaController(it.id, true))
            },
        )
    }

    override fun initFilterSheet() {
        // No-op: we don't allow filtering in similar
    }

    companion object {
        const val MANGA_ID = "manga_id"
        const val MANGA_TITLE = "manga_title"
    }
}
