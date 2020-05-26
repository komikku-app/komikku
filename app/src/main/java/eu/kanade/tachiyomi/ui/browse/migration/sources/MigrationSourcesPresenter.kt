package eu.kanade.tachiyomi.ui.browse.migration.sources

import android.os.Bundle
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.ui.browse.migration.manga.MangaItem
import exh.MERGED_SOURCE_ID
import rx.android.schedulers.AndroidSchedulers
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MigrationSourcesPresenter(
    private val sourceManager: SourceManager = Injekt.get(),
    private val db: DatabaseHelper = Injekt.get(),
    private val preferences: PreferencesHelper = Injekt.get()
) : BasePresenter<MigrationSourcesController>() {

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        db.getFavoriteMangas()
            .asRxObservable()
            .observeOn(AndroidSchedulers.mainThread())
            .map { findSourcesWithManga(it) }
            .subscribeLatestCache(MigrationSourcesController::setSources)
    }

    private fun findSourcesWithManga(library: List<Manga>): List<SourceItem> {
        val header =
            SelectionHeader()
        return library.map { it.source }.toSet()
            .mapNotNull { if (it != LocalSource.ID && it != MERGED_SOURCE_ID) sourceManager.getOrStub(it) else null }
            .sortedBy { it.name }
            .map {
                SourceItem(
                    it,
                    header
                )
            }
    }

    private fun libraryToMigrationItem(library: List<Manga>, sourceId: Long): List<MangaItem> {
        return library.filter { it.source == sourceId }.map(::MangaItem)
    }
}
