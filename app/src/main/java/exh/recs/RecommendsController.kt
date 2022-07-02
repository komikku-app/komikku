package exh.recs

import android.os.Bundle
import android.view.Menu
import android.view.View
import androidx.core.os.bundleOf
import eu.kanade.domain.manga.model.Manga
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.ui.base.controller.pushController
import eu.kanade.tachiyomi.ui.browse.source.SourcesController
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceController
import eu.kanade.tachiyomi.ui.browse.source.browse.SourceItem

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

    override fun getTitle(): String? {
        return (presenter as? RecommendsPresenter)?.manga?.title
    }

    override fun createPresenter(): RecommendsPresenter {
        return RecommendsPresenter(args.getLong(MANGA_ID), args.getLong(SOURCE_ID_KEY))
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        menu.findItem(R.id.action_search).isVisible = false
        menu.findItem(R.id.action_open_in_web_view).isVisible = false
        menu.findItem(R.id.action_settings).isVisible = false
    }

    override fun initFilterSheet() {
        // No-op: we don't allow filtering in recs
    }

    override fun onItemClick(view: View, position: Int): Boolean {
        val item = adapter?.getItem(position) as? SourceItem ?: return false
        openSmartSearch(item.manga.ogTitle)
        return true
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

    override fun onItemLongClick(position: Int) {
        return
    }

    companion object {
        const val MANGA_ID = "manga_id"
    }
}
