package exh.md.similar

import android.os.Bundle
import android.view.Menu
import androidx.core.os.bundleOf
import eu.kanade.domain.manga.model.Manga
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceController
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourcePresenter

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

    private val mangaTitle = args.getString(MANGA_TITLE)

    override fun getTitle(): String? {
        return view?.context?.getString(R.string.similar, mangaTitle)
    }

    override fun createPresenter(): BrowseSourcePresenter {
        return MangaDexSimilarPresenter(args.getLong(MANGA_ID), args.getLong(SOURCE_ID_KEY))
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        menu.findItem(R.id.action_search).isVisible = false
        menu.findItem(R.id.action_open_in_web_view).isVisible = false
        menu.findItem(R.id.action_settings).isVisible = false
    }

    override fun initFilterSheet() {
        // No-op: we don't allow filtering in similar
    }

    override fun onItemLongClick(position: Int) {
        return
    }

    override fun onAddPageError(error: Throwable) {
        super.onAddPageError(error)
        binding.emptyView.show(activity!!.getString(R.string.similar_no_results))
    }

    companion object {
        const val MANGA_ID = "manga_id"
        const val MANGA_TITLE = "manga_title"
    }
}
