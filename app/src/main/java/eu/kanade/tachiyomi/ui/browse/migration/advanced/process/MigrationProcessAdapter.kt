package eu.kanade.tachiyomi.ui.browse.migration.advanced.process

import android.view.MenuItem
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.kanade.domain.category.interactor.GetCategories
import eu.kanade.domain.category.interactor.SetMangaCategories
import eu.kanade.domain.chapter.interactor.GetChapterByMangaId
import eu.kanade.domain.chapter.interactor.UpdateChapter
import eu.kanade.domain.chapter.model.Chapter
import eu.kanade.domain.chapter.model.ChapterUpdate
import eu.kanade.domain.history.interactor.GetHistoryByMangaId
import eu.kanade.domain.history.interactor.UpsertHistory
import eu.kanade.domain.history.model.HistoryUpdate
import eu.kanade.domain.manga.interactor.GetManga
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.Manga
import eu.kanade.domain.manga.model.MangaUpdate
import eu.kanade.domain.manga.model.hasCustomCover
import eu.kanade.domain.manga.model.toDbManga
import eu.kanade.domain.track.interactor.DeleteTrack
import eu.kanade.domain.track.interactor.GetTracks
import eu.kanade.domain.track.interactor.InsertTrack
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.browse.migration.MigrationFlags
import eu.kanade.tachiyomi.util.lang.launchUI
import eu.kanade.tachiyomi.util.lang.withIOContext
import kotlinx.coroutines.cancel
import uy.kohesive.injekt.injectLazy

class MigrationProcessAdapter(
    val controller: MigrationListController,
) : FlexibleAdapter<MigrationProcessItem>(null, controller, true) {
    private val preferences: PreferencesHelper by injectLazy()
    private val coverCache: CoverCache by injectLazy()
    private val getManga: GetManga by injectLazy()
    private val updateManga: UpdateManga by injectLazy()
    private val updateChapter: UpdateChapter by injectLazy()
    private val getChapterByMangaId: GetChapterByMangaId by injectLazy()
    private val getHistoryByMangaId: GetHistoryByMangaId by injectLazy()
    private val upsertHistory: UpsertHistory by injectLazy()
    private val getCategories: GetCategories by injectLazy()
    private val setMangaCategories: SetMangaCategories by injectLazy()
    private val getTracks: GetTracks by injectLazy()
    private val insertTrack: InsertTrack by injectLazy()
    private val deleteTrack: DeleteTrack by injectLazy()

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
            currentItems.forEach { migratingManga ->
                val manga = migratingManga.manga
                if (manga.searchResult.initialized) {
                    val toMangaObj = getManga.await(manga.searchResult.get() ?: return@forEach)
                        ?: return@forEach
                    migrateMangaInternal(
                        manga.manga() ?: return@forEach,
                        toMangaObj,
                        !copy,
                    )
                }
            }
        }
    }

    fun migrateManga(position: Int, copy: Boolean) {
        launchUI {
            val manga = getItem(position)?.manga ?: return@launchUI

            val toMangaObj = getManga.await(manga.searchResult.get() ?: return@launchUI)
                ?: return@launchUI
            migrateMangaInternal(
                manga.manga() ?: return@launchUI,
                toMangaObj,
                !copy,
            )

            removeManga(position)
        }
    }

    fun removeManga(position: Int) {
        val item = getItem(position) ?: return
        if (items.size == 1) {
            item.manga.migrationStatus = MigrationStatus.MANGA_NOT_FOUND
            item.manga.migrationJob.cancel()
            item.manga.searchResult.set(null)
            sourceFinished()
            notifyItemChanged(position)
            return
        }
        menuItemListener.removeManga(item)
        item.manga.migrationJob.cancel()
        removeItem(position)
        items = currentItems
        sourceFinished()
    }

    private suspend fun migrateMangaInternal(
        prevManga: Manga,
        manga: Manga,
        replace: Boolean,
    ) {
        controller.config ?: return
        val flags = preferences.migrateFlags().get()
        // Update chapters read
        if (MigrationFlags.hasChapters(flags)) {
            val prevMangaChapters = getChapterByMangaId.await(prevManga.id)
            val maxChapterRead =
                prevMangaChapters.filter(Chapter::read).maxOfOrNull(Chapter::chapterNumber)
            val dbChapters = getChapterByMangaId.await(manga.id)
            val prevHistoryList = getHistoryByMangaId.await(prevManga.id)

            val chapterUpdates = mutableListOf<ChapterUpdate>()
            val historyUpdates = mutableListOf<HistoryUpdate>()

            dbChapters.forEach { chapter ->
                if (chapter.isRecognizedNumber) {
                    val prevChapter = prevMangaChapters.find { it.isRecognizedNumber && it.chapterNumber == chapter.chapterNumber }
                    if (prevChapter != null) {
                        chapterUpdates += ChapterUpdate(
                            id = chapter.id,
                            bookmark = prevChapter.bookmark,
                            read = prevChapter.read,
                            dateFetch = prevChapter.dateFetch,
                        )
                        prevHistoryList.find { it.chapterId == prevChapter.id }?.let { prevHistory ->
                            historyUpdates += HistoryUpdate(
                                chapter.id,
                                prevHistory.readAt ?: return@let,
                                prevHistory.readDuration,
                            )
                        }
                    } else if (maxChapterRead != null && chapter.chapterNumber <= maxChapterRead) {
                        chapterUpdates += ChapterUpdate(
                            id = chapter.id,
                            read = true,
                        )
                    }
                }
            }

            updateChapter.awaitAll(chapterUpdates)
            historyUpdates.forEach {
                upsertHistory.await(it)
            }
        }
        // Update categories
        if (MigrationFlags.hasCategories(flags)) {
            val categories = getCategories.await(prevManga.id)
            setMangaCategories.await(manga.id, categories.map { it.id })
        }
        // Update track
        if (MigrationFlags.hasTracks(flags)) {
            val tracks = getTracks.await(prevManga.id)
            if (tracks.isNotEmpty()) {
                getTracks.await(manga.id).forEach {
                    deleteTrack.await(manga.id, it.syncId)
                }
                insertTrack.awaitAll(tracks.map { it.copy(mangaId = manga.id) })
            }
        }
        // Update custom cover
        if (MigrationFlags.hasCustomCover(flags) && prevManga.hasCustomCover(coverCache)) {
            coverCache.setCustomCoverToCache(manga.toDbManga(), coverCache.getCustomCoverFile(prevManga.id).inputStream())
        }

        var mangaUpdate = MangaUpdate(manga.id, favorite = true, dateAdded = System.currentTimeMillis())
        var prevMangaUpdate: MangaUpdate? = null
        // Update extras
        if (MigrationFlags.hasExtra(flags)) {
            mangaUpdate = mangaUpdate.copy(
                chapterFlags = prevManga.chapterFlags,
                viewerFlags = prevManga.viewerFlags,
            )
        }
        // Update favorite status
        if (replace) {
            prevMangaUpdate = MangaUpdate(
                id = prevManga.id,
                favorite = false,
                dateAdded = 0,
            )
            mangaUpdate = mangaUpdate.copy(
                dateAdded = prevManga.dateAdded,
            )
        }

        updateManga.awaitAll(listOfNotNull(mangaUpdate, prevMangaUpdate))
    }
}
