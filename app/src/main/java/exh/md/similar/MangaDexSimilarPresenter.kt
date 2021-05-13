package exh.md.similar

import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.online.all.MangaDex
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourcePresenter
import eu.kanade.tachiyomi.ui.browse.source.browse.Pager
import exh.source.getMainSource
import uy.kohesive.injekt.injectLazy

/**
 * Presenter of [MangaDexSimilarController]. Inherit BrowseCataloguePresenter.
 */
class MangaDexSimilarPresenter(val mangaId: Long, sourceId: Long) : BrowseSourcePresenter(sourceId) {

    var manga: Manga? = null

    val db: DatabaseHelper by injectLazy()

    override fun createPager(query: String, filters: FilterList): Pager {
        val sourceAsMangaDex = source.getMainSource() as MangaDex
        this.manga = db.getManga(mangaId).executeAsBlocking()
        return MangaDexSimilarPager(manga!!, sourceAsMangaDex)
    }
}
