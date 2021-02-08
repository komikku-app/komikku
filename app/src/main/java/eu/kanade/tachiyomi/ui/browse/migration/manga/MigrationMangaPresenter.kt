package eu.kanade.tachiyomi.ui.browse.migration.manga

import android.os.Bundle
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.MangaCategory
import eu.kanade.tachiyomi.data.database.models.toMangaInfo
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.toSChapter
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.ui.browse.migration.MigrationFlags
import eu.kanade.tachiyomi.util.chapter.syncChaptersWithSource
import eu.kanade.tachiyomi.util.lang.runAsObservable
import exh.debug.DebugFunctions.sourceManager
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MigrationMangaPresenter(
    private val sourceId: Long,
    private val db: DatabaseHelper = Injekt.get()
) : BasePresenter<MigrationMangaController>() {

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        db.getFavoriteMangas()
            .asRxObservable()
            .observeOn(AndroidSchedulers.mainThread())
            .map { libraryToMigrationItem(it) }
            .subscribeLatestCache(MigrationMangaController::setManga)
    }

    private fun libraryToMigrationItem(library: List<Manga>): List<MigrationMangaItem> {
        return library.filter { it.source == sourceId }
            .sortedBy { it.originalTitle }
            .map { MigrationMangaItem(it) }
    }

    // SY -->
    fun migrateManga(prevManga: Manga, manga: Manga, replace: Boolean) {
        val source = sourceManager.get(manga.source) ?: return

        Observable.defer { runAsObservable({ source.getChapterList(manga.toMangaInfo()).map { it.toSChapter() } }) }.onErrorReturn { emptyList() }
            .doOnNext { migrateMangaInternal(source, it, prevManga, manga, replace) }
            .onErrorReturn { emptyList() }.subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe()
    }

    private fun migrateMangaInternal(
        source: Source,
        sourceChapters: List<SChapter>,
        prevManga: Manga,
        manga: Manga,
        replace: Boolean
    ) {
        val flags = Injekt.get<PreferencesHelper>().migrateFlags().get()
        val migrateChapters = MigrationFlags.hasChapters(flags)
        val migrateCategories = MigrationFlags.hasCategories(flags)
        val migrateTracks = MigrationFlags.hasTracks(flags)
        val migrateExtra = MigrationFlags.hasExtra(flags)

        db.inTransaction {
            // Update chapters read
            if (migrateChapters) {
                try {
                    syncChaptersWithSource(db, sourceChapters, manga, source)
                } catch (e: Exception) {
                    // Worst case, chapters won't be synced
                }

                val prevMangaChapters = db.getChapters(prevManga).executeAsBlocking()
                val maxChapterRead =
                    prevMangaChapters.filter { it.read }.maxByOrNull { it.chapter_number }?.chapter_number
                if (maxChapterRead != null) {
                    val dbChapters = db.getChapters(manga).executeAsBlocking()
                    for (chapter in dbChapters) {
                        if (chapter.isRecognizedNumber && chapter.chapter_number <= maxChapterRead) {
                            chapter.read = true
                        }
                    }
                    db.insertChapters(dbChapters).executeAsBlocking()
                }
            }
            // Update categories
            if (migrateCategories) {
                val categories = db.getCategoriesForManga(prevManga).executeAsBlocking()
                val mangaCategories = categories.map { MangaCategory.create(manga, it) }
                db.setMangaCategories(mangaCategories, listOf(manga))
            }
            // Update track
            if (migrateTracks) {
                val tracks = db.getTracks(prevManga).executeAsBlocking()
                for (track in tracks) {
                    track.id = null
                    track.manga_id = manga.id!!
                }
                db.insertTracks(tracks).executeAsBlocking()
            }

            if (migrateExtra) {
                manga.viewer = prevManga.viewer
                manga.chapter_flags = prevManga.chapter_flags
            }

            // Update favorite status
            if (replace) {
                prevManga.favorite = false
                manga.date_added = prevManga.date_added
                prevManga.date_added = 0
                db.updateMangaFavorite(prevManga).executeAsBlocking()
            } else {
                manga.date_added = System.currentTimeMillis()
            }
            // Set extra data

            manga.favorite = true
            db.updateMangaMigrate(manga).executeAsBlocking()
        }
    }
    // SY <--
}
