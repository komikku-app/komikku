package eu.kanade.tachiyomi.ui.library

import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastMap
import eu.kanade.core.util.fastFilterNot
import eu.kanade.domain.chapter.interactor.SetReadStatus
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.all.MergedSource
import eu.kanade.tachiyomi.util.removeCovers
import exh.source.MERGED_SOURCE_ID
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import mihon.core.common.utils.mutate
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.chapter.interactor.GetBookmarkedChaptersByMangaId
import tachiyomi.domain.history.interactor.GetNextChapters
import tachiyomi.domain.manga.interactor.GetMergedMangaById
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/*
 * KMK: desktop-only copy. Follows `master`'s app/src/main/java/eu/kanade/tachiyomi/ui/library/LibraryScreenModel.kt,
 * last synced to `master` 15610e67e4 (docs/kmp-desktop/UPSTREAM_AUDIT.md lists the desktop copies to re-sync at each
 * merge of `master`).
 *
 * The library's selection and batch-action logic, taken out of `LibraryScreenModel` for the desktop library
 * (app/komikku/desktop/ui/tabs/LibraryScreenModel.kt), which calls these with its own state and coroutine scope.
 * Android runs `master`'s `LibraryScreenModel` unchanged.
 *
 * Markers: this file is Komikku-owned and has no `// SY` / `// KMK` markers. Its bodies come from
 * `LibraryScreenModel` (`toggleSelection`, `toggleRangeSelection`, `selectAll`, `invertSelection`, `getCommonCategories`,
 * `getMixCategories`, `openChangeCategoryDialog`, `downloadNextChapters`, `downloadBookmarkedChapters`, `markReadSelection`,
 * `removeMangas`, `setMangaCategories`); of those, these parts come from TachiyomiSY (they were `// SY` blocks there):
 * the merged-entry (`MERGED_SOURCE_ID`) branches of the two download functions and `ogTitle` in their "already downloaded"
 * checks. Upstream Mihon / SY changes to these functions must be applied here.
 */

/** Formerly the body of `LibraryScreenModel.toggleSelection`. */
fun Set</* Manga */ Long>.toggleLibrarySelection(mangaId: Long): Set<Long> = mutate { set ->
    if (!set.remove(mangaId)) set.add(mangaId)
}

/**
 * `LibraryScreenModel.LastSelection`: the entry last clicked into the selection and the category it was clicked in, the
 * anchor of the next range selection. Set by a click that selects ([lastLibrarySelectionAfterToggle]) and by a range
 * click (to the clicked entry); cleared by clearing, selecting all and inverting the selection.
 */
data class LibraryLastSelection(val categoryId: Long, val mangaId: Long)

/** The anchor after a click on [mangaId] in [categoryId] made [newSelection]: none when the click deselected it. */
fun lastLibrarySelectionAfterToggle(categoryId: Long, mangaId: Long, newSelection: Set<Long>): LibraryLastSelection? =
    LibraryLastSelection(categoryId, mangaId).takeIf { mangaId in newSelection }

/**
 * The body of `LibraryScreenModel.toggleRangeSelection`: selects all manga between and including [mangaId] and the
 * anchor [lastSelection] when the anchor was clicked in the same category ([categoryId]) and is still selected;
 * otherwise only [mangaId]. [categoryItems] are the ids of that category's items in display order. The new anchor is
 * `LibraryLastSelection(categoryId, mangaId)`.
 */
fun Set</* Manga */ Long>.toggleLibraryRangeSelection(
    categoryId: Long,
    mangaId: Long,
    categoryItems: List<Long>,
    lastSelection: LibraryLastSelection?,
): Set<Long> = mutate { list ->
    val lastMangaIndex = lastSelection
        ?.takeIf { it.categoryId == categoryId && it.mangaId in list }
        ?.let { categoryItems.indexOf(it.mangaId) }
        ?: -1
    val curMangaIndex = categoryItems.indexOf(mangaId)
    if (lastMangaIndex == -1 || curMangaIndex == -1) {
        list.add(mangaId)
        return@mutate
    }

    val selectionRange = when {
        lastMangaIndex < curMangaIndex -> lastMangaIndex..curMangaIndex
        else -> curMangaIndex..lastMangaIndex
    }
    list.addAll(categoryItems.subList(selectionRange.first, selectionRange.last + 1))
}

/** Formerly the body of `LibraryScreenModel.selectAll`. */
fun Set</* Manga */ Long>.selectAllLibraryItems(categoryItems: List<Long>): Set<Long> = mutate { list ->
    categoryItems.let(list::addAll)
}

/** Formerly the body of `LibraryScreenModel.invertSelection`. */
fun Set</* Manga */ Long>.invertLibrarySelection(categoryItems: List<Long>): Set<Long> = mutate { list ->
    val (toRemove, toAdd) = categoryItems.partition { it in list }
    list.removeAll(toRemove.toSet())
    list.addAll(toAdd)
}

/**
 * The batch actions on the library selection (formerly bodies of `LibraryScreenModel` functions of the same names).
 * Callers run them in their own (non-cancellable) scope.
 */
class LibraryBatchActions(
    private val getCategories: GetCategories = Injekt.get(),
    private val getNextChapters: GetNextChapters = Injekt.get(),
    private val getBookmarkedChaptersByMangaId: GetBookmarkedChaptersByMangaId = Injekt.get(),
    private val setReadStatus: SetReadStatus = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val setMangaCategories: SetMangaCategories = Injekt.get(),
    private val coverCache: CoverCache = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val getMergedMangaById: GetMergedMangaById = Injekt.get(),
) {

    suspend fun getCommonCategories(mangas: List<Manga>): Collection<Category> {
        if (mangas.isEmpty()) return emptyList()
        return mangas
            .map { getCategories.await(it.id).toSet() }
            .reduce { set1, set2 -> set1.intersect(set2) }
    }

    /**
     * Returns the mix (non-common) categories for the given list of manga.
     *
     * @param mangas the list of manga.
     */
    suspend fun getMixCategories(mangas: List<Manga>): Collection<Category> {
        if (mangas.isEmpty()) return emptyList()
        val mangaCategories = mangas.map { getCategories.await(it.id).toSet() }
        val common = mangaCategories.reduce { set1, set2 -> set1.intersect(set2) }
        return mangaCategories.flatten().distinct().subtract(common)
    }

    /**
     * The initial selection of the change-category dialog (formerly part of
     * `LibraryScreenModel.openChangeCategoryDialog`): common categories checked, mixed ones indeterminate.
     *
     * @param categories the categories to offer, without the default category.
     */
    suspend fun getChangeCategorySelection(
        mangaList: List<Manga>,
        categories: List<Category>,
    ): ImmutableList<CheckboxState<Category>> {
        // Get indexes of the common categories to preselect.
        val common = getCommonCategories(mangaList)
        // Get indexes of the mix categories to preselect.
        val mix = getMixCategories(mangaList)
        return categories
            .fastMap {
                when (it) {
                    in common -> CheckboxState.State.Checked(it)
                    in mix -> CheckboxState.TriState.Exclude(it)
                    else -> CheckboxState.State.None(it)
                }
            }
            .toImmutableList()
    }

    /** Queues [amount] (all when `null`) unread chapters of each of [mangas] that are not downloaded or queued yet. */
    suspend fun downloadNextChapters(mangas: List<Manga>, amount: Int?) {
        mangas.forEach { manga ->
            if (manga.source == MERGED_SOURCE_ID) {
                val mergedMangas = getMergedMangaById.await(manga.id)
                    .associateBy { it.id }
                getNextChapters.await(manga.id)
                    .let { if (amount != null) it.take(amount) else it }
                    .groupBy { it.mangaId }
                    .forEach ab@{ (mangaId, chapters) ->
                        val mergedManga = mergedMangas[mangaId] ?: return@ab
                        val downloadChapters = chapters.fastFilterNot { chapter ->
                            downloadManager.queueState.value.fastAny { chapter.id == it.chapter.id } ||
                                downloadManager.isChapterDownloaded(
                                    chapter.name,
                                    chapter.scanlator,
                                    chapter.url,
                                    mergedManga.ogTitle,
                                    mergedManga.source,
                                )
                        }

                        downloadManager.downloadChapters(mergedManga, downloadChapters)
                    }

                return@forEach
            }

            val chapters = getNextChapters.await(manga.id)
                .fastFilterNot { chapter ->
                    downloadManager.getQueuedDownloadOrNull(chapter.id) != null ||
                        downloadManager.isChapterDownloaded(
                            chapter.name,
                            chapter.scanlator,
                            chapter.url,
                            manga.ogTitle,
                            manga.source,
                        )
                }
                .let { if (amount != null) it.take(amount) else it }

            downloadManager.downloadChapters(manga, chapters)
        }
    }

    /** Queues the bookmarked chapters of each of [mangas] that are not downloaded or queued yet. */
    suspend fun downloadBookmarkedChapters(mangas: List<Manga>) {
        mangas.forEach { manga ->
            if (manga.source == MERGED_SOURCE_ID) {
                val mergedMangas = getMergedMangaById.await(manga.id)
                    .associateBy { it.id }
                getBookmarkedChaptersByMangaId.await(manga.id)
                    .groupBy { it.mangaId }
                    .forEach ab@{ (mangaId, chapters) ->
                        val mergedManga = mergedMangas[mangaId] ?: return@ab
                        val downloadChapters = chapters.fastFilterNot { chapter ->
                            downloadManager.queueState.value.fastAny { chapter.id == it.chapter.id } ||
                                downloadManager.isChapterDownloaded(
                                    chapter.name,
                                    chapter.scanlator,
                                    chapter.url,
                                    mergedManga.ogTitle,
                                    mergedManga.source,
                                )
                        }

                        downloadManager.downloadChapters(mergedManga, downloadChapters)
                    }

                return@forEach
            }

            val chapters = getBookmarkedChaptersByMangaId.await(manga.id)
                .fastFilterNot { chapter ->
                    downloadManager.getQueuedDownloadOrNull(chapter.id) != null ||
                        downloadManager.isChapterDownloaded(
                            chapter.name,
                            chapter.scanlator,
                            chapter.url,
                            manga.ogTitle,
                            manga.source,
                        )
                }
            downloadManager.downloadChapters(manga, chapters)
        }
    }

    /** Marks all chapters of [mangas] read or unread. */
    suspend fun markRead(mangas: List<Manga>, read: Boolean) {
        mangas.forEach { manga ->
            setReadStatus.await(
                manga = manga,
                read = read,
            )
        }
    }

    /**
     * Remove the given manga.
     *
     * @param mangas the list of manga to delete.
     * @param deleteFromLibrary whether to delete manga from library.
     * @param deleteChapters whether to delete downloaded chapters.
     */
    suspend fun removeMangas(mangas: List<Manga>, deleteFromLibrary: Boolean, deleteChapters: Boolean) {
        if (deleteFromLibrary) {
            val toDelete = mangas
                .distinctBy { it.id }
                .map {
                    it.removeCovers(coverCache)
                    MangaUpdate(
                        favorite = false,
                        id = it.id,
                    )
                }
            updateManga.awaitAll(toDelete)
        }

        if (deleteChapters) {
            mangas.forEach { manga ->
                val source = sourceManager.get(manga.source) as? HttpSource
                if (source != null) {
                    if (source is MergedSource) {
                        val mergedMangas = getMergedMangaById.await(manga.id)
                        val sources = mergedMangas.distinctBy {
                            it.source
                        }.map { sourceManager.getOrStub(it.source) }
                        mergedMangas.forEach merge@{ mergedManga ->
                            val mergedSource =
                                sources.firstOrNull { mergedManga.source == it.id } as? HttpSource ?: return@merge
                            downloadManager.deleteManga(mergedManga, mergedSource)
                        }
                    } else {
                        downloadManager.deleteManga(manga, source)
                    }
                }
            }
        }
    }

    /**
     * Bulk update categories of manga using old and new common categories.
     *
     * @param mangaList the list of manga to move.
     * @param addCategories the categories to add for all mangas.
     * @param removeCategories the categories to remove in all mangas.
     */
    suspend fun setMangaCategories(mangaList: List<Manga>, addCategories: List<Long>, removeCategories: List<Long>) {
        mangaList.forEach { manga ->
            val categoryIds = getCategories.await(manga.id)
                .map { it.id }
                .subtract(removeCategories.toSet())
                .plus(addCategories)
                .toList()

            setMangaCategories.await(manga.id, categoryIds)
        }
    }
}
