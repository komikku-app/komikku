package eu.kanade.domain.chapter.interactor

import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.data.download.Downloader
import tachiyomi.core.metadata.comicinfo.ComicInfo
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.manga.interactor.GetAllManga
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.source.local.isLocal

/**
 * Scans each manga download directory for chapter folders (or `.cbz`) whose names are absent from
 * [eu.kanade.tachiyomi.data.download.DownloadProvider.getValidChapterDirNames] for chapters already
 * in the database. Each such path becomes a new chapter row with `url` prefixed by `orphaned://`.
 *
 * This interactor is invoked after [ReconcileChapterDownloads] during reindex (see
 * [eu.kanade.tachiyomi.ui.download.reindexDownloads]) and after reindex merge (parent manga only).
 * Folders that uniquely match a catalog chapter via [DownloadFolderMatcher] are skipped so
 * URL-drifted downloads are linked by reconcile instead of inserting `orphaned://` rows.
 *
 * Before inserting an orphan, `ComicInfo.xml` is read when present (see [tachiyomi.core.metadata.comicinfo.COMIC_INFO_FILE]); if
 * [OrphanChapterComicInfoLink] finds exactly one matching catalog chapter via `<Web>`, the folder
 * is skipped. Ambiguous `<Web>` matches skip orphan insertion to avoid wrong merges.
 */
class RestoreOrphanedChapters(
    private val getAllManga: GetAllManga,
    private val getManga: GetManga,
    private val getChaptersByMangaId: GetChaptersByMangaId,
    private val chapterRepository: ChapterRepository,
    private val downloadProvider: DownloadProvider,
    private val sourceManager: SourceManager,
    private val comicInfoReader: ChapterDownloadComicInfoReader,
) {
    /**
     * @param mangaIds When null, scans every manga in the library. When non-null, only those IDs
     *   are processed (after reindex merge, only the parent id is passed).
     */
    suspend fun await(mangaIds: Collection<Long>? = null): Int {
        return resolveMangaList(mangaIds).sumOf { restoreForManga(it) }
    }

    private suspend fun resolveMangaList(mangaIds: Collection<Long>?): List<Manga> {
        if (mangaIds == null) {
            return getAllManga.await()
        }
        return mangaIds.distinct().mapNotNull { getManga.await(it) }
    }

    private suspend fun restoreForManga(manga: Manga): Int {
        val source = sourceManager.getOrStub(manga.source)
        if (source.isLocal()) return 0

        val mangaDir = downloadProvider.findMangaDir(manga.ogTitle, source) ?: return 0
        val dbChapters = getChaptersByMangaId.await(manga.id)

        val knownFolderNames = dbChapters.flatMap { chapter ->
            downloadProvider.getValidChapterDirNames(chapter.name, chapter.scanlator, chapter.url)
        }.toHashSet()

        val knownChapterNumbers = dbChapters
            .filter { !it.url.startsWith("orphaned://") && it.isRecognizedNumber }
            .map { it.chapterNumber }
            .toHashSet()

        val candidateFiles = mangaDir.listFiles().orEmpty()
            .filter { file ->
                val fileName = file.name ?: return@filter false
                (file.isDirectory || fileName.endsWith(".cbz")) &&
                    fileName !in knownFolderNames &&
                    !fileName.endsWith(Downloader.TMP_DIR_SUFFIX)
            }

        val catalogChapters = dbChapters.filter { !it.url.startsWith("orphaned://", ignoreCase = true) }

        val orphanedChapters = mutableListOf<Chapter>()
        for (dir in candidateFiles) {
            val fileName = dir.name ?: continue
            val comicInfo = readComicInfoFromDownloadEntry(dir)

            if (DownloadFolderMatcher.matchCatalogChapter(
                    catalogChapters = catalogChapters,
                    folderName = fileName,
                    mangaTitle = manga.title,
                    comicInfo = comicInfo,
                ) != null
            ) {
                continue
            }

            val chapterName = if (fileName.endsWith(".cbz")) {
                fileName.dropLast(4)
            } else {
                if (!dir.isDirectory) continue
                val resolved = downloadProvider.resolveChapterImageDir(dir)
                if (!resolved.isValid) continue
                resolved.chapterName
            }

            val chapterNumber = DownloadFolderMatcher.parseChapterNumberFromFolder(
                manga.title,
                fileName,
            )
            // A folder that re-parses to a number the catalog already has must not become
            // orphaned://, including when several scanlators share that number.
            if (chapterNumber >= 0 && chapterNumber in knownChapterNumbers) continue

            when (OrphanChapterComicInfoLink.matchCatalogChapterFromComicInfo(comicInfo, dbChapters)) {
                is OrphanChapterComicInfoLink.ComicInfoWebChapterMatch.Unique,
                is OrphanChapterComicInfoLink.ComicInfoWebChapterMatch.Ambiguous,
                -> continue
                is OrphanChapterComicInfoLink.ComicInfoWebChapterMatch.None -> Unit
            }

            orphanedChapters.add(
                Chapter.create().copy(
                    mangaId = manga.id,
                    url = "orphaned://$chapterName",
                    name = chapterName,
                    chapterNumber = chapterNumber,
                    sourceOrder = -1L,
                    dateFetch = dir.lastModified(),
                    dateUpload = 0L,
                ),
            )
        }

        if (orphanedChapters.isEmpty()) {
            return 0
        }
        chapterRepository.addAll(orphanedChapters)
        return orphanedChapters.size
    }

    private suspend fun readComicInfoFromDownloadEntry(file: UniFile): ComicInfo? =
        comicInfoReader.readComicInfo(file)
}
