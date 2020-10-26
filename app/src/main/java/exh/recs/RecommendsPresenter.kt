package exh.recs

import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourcePresenter
import eu.kanade.tachiyomi.ui.browse.source.browse.Pager
import uy.kohesive.injekt.injectLazy

/**
 * Presenter of [RecommendsController]. Inherit BrowseCataloguePresenter.
 */
class RecommendsPresenter(val mangaId: Long, sourceId: Long) : BrowseSourcePresenter(sourceId) {

    var manga: Manga? = null

    val db: DatabaseHelper by injectLazy()

    override fun createPager(query: String, filters: FilterList): Pager {
        this.manga = db.getManga(mangaId).executeAsBlocking()
        return RecommendsPager(manga!!)
    }
}
