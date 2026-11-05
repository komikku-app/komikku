package eu.kanade.domain.chapter.interactor

import com.hippo.unifile.UniFile
import eu.kanade.domain.chapter.interactor.OrphanChapterComicInfoLink.ComicInfoWebChapterMatch
import eu.kanade.domain.chapter.interactor.OrphanDownloadRetarget.Decision
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.source.Source
import kotlinx.coroutines.CancellationException
import tachiyomi.core.common.storage.extension
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.download.model.ChapterDownload
import tachiyomi.domain.download.repository.ChapterDownloadRepository
import tachiyomi.domain.manga.interactor.GetAllManga
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.source.local.isLocal

/**
 * Removes false `orphaned://` chapter rows. A folder whose name re-parses to a real catalog
 * chapter (see [OrphanDownloadRetarget]) is renamed to that chapter's directory and linked in
 * `chapter_downloads`. Otherwise `ComicInfo.xml` `<Web>` is used, unchanged.
 *
 * Invoked from [eu.kanade.tachiyomi.ui.download.reindexDownloads] (full library) and after reindex
 * merge (parent manga only) following [RestoreOrphanedChapters].
 */
class CleanupOrphanedDuplicateChapters(
    private val getAllManga: GetAllManga,
    private val getManga: GetManga,
    private val getChaptersByMangaId: GetChaptersByMangaId,
    private val chapterRepository: ChapterRepository,
    private val downloadProvider: DownloadProvider,
    private val sourceManager: SourceManager,
    private val comicInfoReader: ChapterDownloadComicInfoReader,
    private val chapterDownloadRepository: ChapterDownloadRepository,
    private val downloadCache: DownloadCache,
) {
    /**
     * @param mangaIds When null, scans every manga in the library. When non-null, only those IDs
     *   are processed (after reindex merge, only the parent id is passed).
     */
    suspend fun await(mangaIds: Collection<Long>? = null): Int {
        return resolveMangaList(mangaIds).sumOf { cleanupForManga(it) }
    }

    private suspend fun resolveMangaList(mangaIds: Collection<Long>?): List<Manga> {
        if (mangaIds == null) {
            return getAllManga.await()
        }
        return mangaIds.distinct().mapNotNull { getManga.await(it) }
    }

    private suspend fun cleanupForManga(manga: Manga): Int {
        val source = sourceManager.getOrStub(manga.source)
        if (source.isLocal()) return 0

        val mangaDir = downloadProvider.findMangaDir(manga.ogTitle, source) ?: return 0
        val dbChapters = getChaptersByMangaId.await(manga.id)
        val orphans = dbChapters.filter { it.url.startsWith("orphaned://", ignoreCase = true) }
        if (orphans.isEmpty()) return 0

        var removed = 0
        for (orphan in orphans) {
            val entry = mangaDir.findFile(orphan.name)
                ?: mangaDir.findFile(orphan.name + ".cbz")
                ?: continue

            val decision = OrphanDownloadRetarget.choose(
                folderName = entry.name ?: orphan.name,
                mangaTitle = manga.title,
                chapters = dbChapters,
            )
            val retargeted = when (decision) {
                is Decision.Retarget -> retargetOrphan(source, manga, mangaDir, orphan, entry, decision)
                Decision.Leave -> false
            }
            if (retargeted) {
                removed += 1
                continue
            }
            // A failed retarget still gets the ComicInfo <Web> fallback (pre-retarget behavior).

            if (removeByComicInfoWeb(entry, orphan, dbChapters)) {
                removed += 1
            }
        }
        return removed
    }

    private suspend fun removeByComicInfoWeb(
        entry: UniFile,
        orphan: Chapter,
        dbChapters: List<Chapter>,
    ): Boolean {
        val comicInfo = comicInfoReader.readComicInfo(entry) ?: return false
        val match = OrphanChapterComicInfoLink.matchCatalogChapterFromComicInfo(comicInfo, dbChapters)
        val catalog = (match as? ComicInfoWebChapterMatch.Unique)?.chapter ?: return false

        chapterRepository.update(
            OrphanChapterComicInfoLink.mergeOrphanProgressOntoCatalogChapter(catalog, orphan),
        )
        chapterRepository.removeChaptersWithIds(listOf(orphan.id))
        return true
    }

    /**
     * @return true when the orphan row was removed. False leaves the row in place.
     */
    private suspend fun retargetOrphan(
        source: Source,
        manga: Manga,
        mangaDir: UniFile,
        orphan: Chapter,
        entry: UniFile,
        decision: Decision.Retarget,
    ): Boolean {
        val catalog = decision.chapter
        if (catalog.id <= 0L) return false
        val entryName = entry.name ?: return false

        val canonicalName = downloadProvider.getChapterDirName(catalog.name, catalog.scanlator, catalog.url)
        val desiredName = if (entry.isFile && entry.extension == "cbz") "$canonicalName.cbz" else canonicalName
        val currentPath = downloadProvider.getRelativeChapterPath(source, manga.ogTitle, entryName)
        val alreadyLinked = chapterDownloadRepository.getByChapterId(catalog.id)?.relativePath == currentPath

        val finalName = if (alreadyLinked || entryName == desiredName) {
            entryName
        } else {
            if (mangaDir.findFile(desiredName) != null) return false
            if (!entry.renameTo(desiredName)) return false
            downloadCache.removeFolders(listOf(entryName), manga)
            downloadCache.addChapter(desiredName, mangaDir, manga)
            desiredName
        }

        if (!alreadyLinked) {
            val relativePath = downloadProvider.getRelativeChapterPath(source, manga.ogTitle, finalName)
            try {
                chapterDownloadRepository.upsert(
                    ChapterDownload(
                        chapterId = catalog.id,
                        relativePath = relativePath,
                        linkedAt = System.currentTimeMillis(),
                    ),
                )
                downloadCache.setRegistryEntry(catalog.id, relativePath)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // Undo the rename so the orphan row still matches the on-disk name on the next run.
                if (finalName != entryName && entry.renameTo(entryName)) {
                    downloadCache.removeFolders(listOf(finalName), manga)
                    downloadCache.addChapter(entryName, mangaDir, manga)
                }
                return false
            }
        } else {
            downloadCache.setRegistryEntry(catalog.id, currentPath)
        }

        return try {
            chapterRepository.update(
                OrphanChapterComicInfoLink.mergeOrphanProgressOntoCatalogChapter(catalog, orphan),
            )
            downloadCache.removeRegistryEntry(orphan.id)
            chapterRepository.removeChaptersWithIds(listOf(orphan.id))
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            false
        }
    }
}
