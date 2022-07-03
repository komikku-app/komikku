package eu.kanade.tachiyomi.ui.browse.migration.search

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import eu.kanade.domain.manga.model.Manga
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.browse.migration.advanced.process.MigrationListController
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceController
import eu.kanade.tachiyomi.ui.browse.source.browse.SourceItem
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SourceSearchController(
    bundle: Bundle,
) : BrowseSourceController(bundle) {

    constructor(targetController: MigrationListController, manga: Manga, source: CatalogueSource, searchQuery: String? = null) : this(
        bundleOf(
            SOURCE_ID_KEY to source.id,
            MANGA_KEY to manga,
            SEARCH_QUERY_KEY to searchQuery,
        ),
    ) {
        this.targetController = targetController
    }

    override fun onItemClick(view: View, position: Int): Boolean {
        val manga = (adapter?.getItem(position) as? SourceItem)?.manga ?: return false
        val migrationListController = targetController as? MigrationListController ?: return false
        val sourceManager = Injekt.get<SourceManager>()
        val source = sourceManager.get(manga.source) ?: return false
        migrationListController.useMangaForMigration(manga, source)
        router.popCurrentController()
        router.popCurrentController()
        return true
    }

    override fun onItemLongClick(position: Int) {
        view?.let { super.onItemClick(it, position) }
    }
}

private const val MANGA_KEY = "oldManga"
