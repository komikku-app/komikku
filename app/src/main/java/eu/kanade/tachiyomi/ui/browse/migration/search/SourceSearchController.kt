package eu.kanade.tachiyomi.ui.browse.migration.search

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceController
import eu.kanade.tachiyomi.ui.browse.source.browse.SourceItem

class SourceSearchController(
    bundle: Bundle
) : BrowseSourceController(bundle) {

    constructor(manga: Manga, source: CatalogueSource, searchQuery: String? = null) : this(
        bundleOf(
            SOURCE_ID_KEY to source.id,
            MANGA_KEY to manga,
            SEARCH_QUERY_KEY to searchQuery
        )
    )
    private var oldManga: Manga = args.getSerializable(MANGA_KEY) as Manga
    private var newManga: Manga? = null

    override fun onItemClick(view: View, position: Int): Boolean {
        val item = adapter?.getItem(position) as? SourceItem ?: return false
        newManga = item.manga
        val searchController = router.backstack.findLast { it.controller().javaClass == SearchController::class.java }?.controller() as SearchController?
        val dialog =
            SearchController.MigrationDialog(oldManga, newManga!!, this)
        dialog.targetController = searchController
        dialog.showDialog(router)
        return true
    }
    private companion object {
        const val MANGA_KEY = "oldManga"
    }
}
