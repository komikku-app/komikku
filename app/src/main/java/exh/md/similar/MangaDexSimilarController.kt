package exh.md.similar

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.core.os.bundleOf
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.domain.manga.model.Manga
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.ui.base.controller.BasicFullComposeController
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceController

/**
 * Controller that shows the latest manga from the catalogue. Inherit [BrowseSourceController].
 */
class MangaDexSimilarController(bundle: Bundle) : BasicFullComposeController(bundle) {

    constructor(manga: Manga, source: CatalogueSource) : this(
        bundleOf(
            MANGA_ID to manga.id,
            SOURCE_ID_KEY to source.id,
        ),
    )

    val mangaId = args.getLong(MANGA_ID)
    val sourceId = args.getLong(SOURCE_ID_KEY)

    @Composable
    override fun ComposeContent() {
        Navigator(screen = MangaDexSimilarScreen(mangaId, sourceId))
    }
}

private const val MANGA_ID = "manga_id"
private const val SOURCE_ID_KEY = "source_id"
