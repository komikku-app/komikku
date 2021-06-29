package eu.kanade.tachiyomi.ui.browse.migration.advanced.process

import android.view.MenuItem
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.History
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.MangaCategory
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.browse.migration.MigrationFlags
import eu.kanade.tachiyomi.util.lang.launchUI
import eu.kanade.tachiyomi.util.lang.withIOContext
import kotlinx.coroutines.cancel
import uy.kohesive.injekt.injectLazy

class MigrationProcessAdapter(
    val controller: MigrationListController
) : FlexibleAdapter<MigrationProcessItem>(null, controller, true) {
    private val db: DatabaseHelper by injectLazy()
    private val preferences: PreferencesHelper by injectLazy()

    var items: List<MigrationProcessItem> = emptyList()

    val menuItemListener: MigrationProcessInterface = controller

    val hideNotFound = preferences.hideNotFoundMigration().get()

    override fun updateDataSet(items: List<MigrationProcessItem>?) {
        this.items = items.orEmpty()
        super.updateDataSet(items)
    }

    interface MigrationProcessInterface {
        fun onMenuItemClick(position: Int, item: MenuItem)
        fun enableButtons()
        fun removeManga(item: MigrationProcessItem)
        fun noMigration()
        fun updateCount()
    }

    fun sourceFinished() {
        menuItemListener.updateCount()
        if (itemCount == 0) menuItemListener.noMigration()
        if (allMangasDone()) menuItemListener.enableButtons()
    }

    fun allMangasDone() = items.all { it.manga.migrationStatus != MigrationStatus.RUNNING } &&
        items.any { it.manga.migrationStatus == MigrationStatus.MANGA_FOUND }

    fun mangasSkipped() = items.count { it.manga.migrationStatus == MigrationStatus.MANGA_NOT_FOUND }

    suspend fun performMigrations(copy: Boolean) {
        withIOContext {
            db.inTransaction {
                currentItems.forEach { migratingManga ->
                    val manga = migratingManga.manga
                    if (manga.searchResult.initialized) {
                        val toMangaObj =
                            db.getManga(manga.searchResult.get() ?: return@forEach).executeAsBlocking()
                                ?: return@forEach
                        migrateMangaInternal(
                            manga.manga() ?: return@forEach,
                            toMangaObj,
                            !copy
                        )
                    }
                }
            }
        }
    }

    fun migrateManga(position: Int, copy: Boolean) {
        launchUI {
            val manga = getItem(position)?.manga ?: return@launchUI
            db.inTransaction {
                val toMangaObj =
                    db.getManga(manga.searchResult.get() ?: return@launchUI).executeAsBlocking()
                        ?: return@launchUI
                migrateMangaInternal(
                    manga.manga() ?: return@launchUI,
                    toMangaObj,
                    !copy
                )
            }
            removeManga(position)
        }
    }

    fun removeManga(position: Int) {
        val item = getItem(position) ?: return
        menuItemListener.removeManga(item)
        item.manga.migrationJob.cancel()
        removeItem(position)
        items = currentItems
        sourceFinished()
    }

    private fun migrateMangaInternal(
        prevManga: Manga,
        manga: Manga,
        replace: Boolean
    ) {
        if (controller.config == null) return
        val flags = preferences.migrateFlags().get()
        // Update chapters read
        if (MigrationFlags.hasChapters(flags)) {
            val prevMangaChapters = db.getChapters(prevManga).executeAsBlocking()
            val maxChapterRead =
                prevMangaChapters.filter { it.read }.maxOfOrNull { it.chapter_number }
            val dbChapters = db.getChapters(manga).executeAsBlocking()
            val prevHistoryList = db.getHistoryByMangaId(prevManga.id!!).executeAsBlocking()
            val historyList = mutableListOf<History>()
            for (chapter in dbChapters) {
                if (chapter.isRecognizedNumber) {
                    val prevChapter =
                        prevMangaChapters.find { it.isRecognizedNumber && it.chapter_number == chapter.chapter_number }
                    if (prevChapter != null) {
                        chapter.bookmark = prevChapter.bookmark
                        chapter.read = prevChapter.read
                        chapter.date_fetch = prevChapter.date_fetch
                        prevHistoryList.find { it.chapter_id == prevChapter.id }?.let { prevHistory ->
                            val history = History.create(chapter).apply { last_read = prevHistory.last_read }
                            historyList.add(history)
                        }
                    } else if (maxChapterRead != null && chapter.chapter_number <= maxChapterRead) {
                        chapter.read = true
                    }
                }
            }
            db.insertChapters(dbChapters).executeAsBlocking()
            db.updateHistoryLastRead(historyList).executeAsBlocking()
        }
        // Update categories
        if (MigrationFlags.hasCategories(flags)) {
            val categories = db.getCategoriesForManga(prevManga).executeAsBlocking()
            val mangaCategories = categories.map { MangaCategory.create(manga, it) }
            db.setMangaCategories(mangaCategories, listOf(manga))
        }
        // Update track
        if (MigrationFlags.hasTracks(flags)) {
            val tracks = db.getTracks(prevManga).executeAsBlocking()
            for (track in tracks) {
                track.id = null
                track.manga_id = manga.id!!
            }
            db.insertTracks(tracks).executeAsBlocking()
        }
        // Update extras
        if (MigrationFlags.hasExtra(flags)) {
            manga.chapter_flags = prevManga.chapter_flags
            manga.viewer_flags = prevManga.viewer_flags
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
        manga.favorite = true

        db.updateMangaMigrate(manga).executeAsBlocking()
    }
}
