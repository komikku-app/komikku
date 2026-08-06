package eu.kanade.domain.download.interactor

import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.util.storage.DiskUtil
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.manga.interactor.GetAllManga
import tachiyomi.domain.source.service.SourceManager

class CleanInvalidDownloads(
    private val getAllManga: GetAllManga,
    private val getChaptersByMangaId: GetChaptersByMangaId,
    private val sourceManager: SourceManager,
    private val downloadManager: DownloadManager,
) {

    suspend fun await(): Int {
        val mangaList = getAllManga.await()
        var foldersCleared = 0
        sourceManager.getOnlineSources().forEach { source ->
            val mangaFolders = downloadManager.getMangaFolders(source)
            val sourceManga = mangaList
                .asSequence()
                .filter { it.source == source.id }
                .map { it to DiskUtil.buildValidFilename(it.ogTitle) }
                .toList()

            mangaFolders.forEach { mangaFolder ->
                val manga = sourceManga.find { (_, folderName) ->
                    folderName == mangaFolder.name
                }?.first
                if (manga == null) {
                    // download is orphaned delete it
                    foldersCleared += 1 + (mangaFolder.listFiles().orEmpty().size)
                    mangaFolder.delete()
                } else {
                    val chapterList = getChaptersByMangaId.await(manga.id)
                    foldersCleared += downloadManager.cleanupChapters(
                        chapterList,
                        manga,
                        source,
                        removeRead = false,
                        removeNonFavorite = false,
                    )
                }
            }
        }
        return foldersCleared
    }
}
