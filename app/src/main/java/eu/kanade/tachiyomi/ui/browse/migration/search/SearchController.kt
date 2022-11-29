package eu.kanade.tachiyomi.ui.browse.migration.search

import android.os.Bundle
import androidx.core.os.bundleOf
import eu.kanade.domain.manga.interactor.GetManga
import eu.kanade.domain.manga.model.Manga
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.base.controller.pushController
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchController
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchPresenter
import kotlinx.coroutines.runBlocking
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SearchController(
    private var manga: Manga? = null,
    private var sources: List<CatalogueSource>? = null,
) : GlobalSearchController(
    manga?.ogTitle,
    bundle = bundleOf(
        OLD_MANGA to manga?.id,
        SOURCES to sources?.map { it.id }?.toLongArray(),
    ),
) {
    constructor(mangaId: Long, sources: LongArray) :
        this(
            runBlocking {
                Injekt.get<GetManga>()
                    .await(mangaId)
            },
            sources.map { Injekt.get<SourceManager>().getOrStub(it) }.filterIsInstance<CatalogueSource>(),
        )

    @Suppress("unused")
    constructor(bundle: Bundle) : this(
        bundle.getLong(OLD_MANGA),
        bundle.getLongArray(SOURCES) ?: LongArray(0),
    )

    var useMangaForMigration: ((Manga, Source) -> Unit)? = null

    /**
     * Called when controller is initialized.
     */
    init {
        setHasOptionsMenu(true)
    }

    override fun createPresenter(): GlobalSearchPresenter {
        return SearchPresenter(
            initialQuery,
            manga!!,
            sources,
        )
    }

    override fun onMangaClick(manga: Manga) {
        val sourceManager = Injekt.get<SourceManager>()
        val source = sourceManager.get(manga.source) ?: return
        useMangaForMigration?.let { it(manga, source) }
        router.popCurrentController()
    }

    override fun onMangaLongClick(manga: Manga) {
        // Call parent's default click listener
        super.onMangaClick(manga)
    }

    override fun onTitleClick(source: CatalogueSource) {
        presenter.sourcePreferences.lastUsedSource().set(source.id)

        router.pushController(SourceSearchController(manga!!, source, presenter.query).also { it.useMangaForMigration = useMangaForMigration })
    }

    companion object {
        const val OLD_MANGA = "old_manga"
        const val SOURCES = "sources"
    }
}
