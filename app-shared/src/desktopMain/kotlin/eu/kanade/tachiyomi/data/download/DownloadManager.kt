package eu.kanade.tachiyomi.data.download

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.Page
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * KMK: desktop counterpart of the Android `DownloadManager` (same FQN and member signatures), for the
 * `desktopCopy` borrowed files that reference it (`SyncChaptersWithSource`, `DeleteDownload`,
 * `UpdateMangaFromRemote`, the reader's `ChapterLoader` / `DownloadPageLoader`).
 *
 * There is no download queue on desktop yet (Phase 8). Downloaded chapters are looked up directly on
 * disk through the borrowed [DownloadProvider] (same folder layout as Android, so chapters copied
 * from an Android device are found), without Android's `DownloadCache`. Deleting works on the
 * folders; renaming folders after a source or title change is not done yet (logged).
 */
class DownloadManager(
    private val context: Context,
    private val provider: DownloadProvider = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
) {

    /** Same as Android: the image files of a downloaded chapter folder, sorted by name. */
    fun buildPageList(source: Source, manga: Manga, chapter: Chapter): List<Page> {
        val chapterDir = provider.findChapterDir(chapter.name, chapter.scanlator, chapter.url, manga.ogTitle, source)
        val files = chapterDir?.listFiles().orEmpty()
            .filter { it.isFile && ImageUtil.isImage(it.name) { it.openInputStream() } }

        if (files.isEmpty()) {
            throw Exception(context.stringResource(MR.strings.page_list_empty_error))
        }

        return files.sortedBy { it.name }
            .mapIndexed { i, file ->
                Page(i, uri = file.uri).apply { status = Page.State.Ready }
            }
    }

    /** Checks the file system (no cache on desktop, so [skipCache] has no effect). */
    @Suppress("UNUSED_PARAMETER")
    fun isChapterDownloaded(
        chapterName: String,
        chapterScanlator: String?,
        chapterUrl: String,
        mangaTitle: String,
        sourceId: Long,
        skipCache: Boolean = false,
    ): Boolean {
        val source = sourceManager.get(sourceId) ?: return false
        return provider.findChapterDir(chapterName, chapterScanlator, chapterUrl, mangaTitle, source) != null
    }

    fun getDownloadCount(manga: Manga): Int {
        val source = sourceManager.get(manga.source) ?: return 0
        return provider.findMangaDir(manga.ogTitle, source)?.listFiles()?.size ?: 0
    }

    @Suppress("UNUSED_PARAMETER")
    fun deleteChapters(
        chapters: List<Chapter>,
        manga: Manga,
        source: Source,
        ignoreCategoryExclusion: Boolean = false,
    ) {
        val (mangaDir, chapterDirs) = provider.findChapterDirs(chapters, manga, source)
        chapterDirs.forEach(UniFile::delete)
        if (mangaDir?.listFiles()?.isEmpty() == true) mangaDir.delete()
    }

    @Suppress("UNUSED_PARAMETER")
    fun deleteManga(manga: Manga, source: Source, removeQueued: Boolean = true) {
        provider.findMangaDir(manga.ogTitle, source)?.delete()
    }

    @Suppress("RedundantSuspendModifier")
    suspend fun renameManga(manga: Manga, newTitle: String) {
        if (provider.findMangaDir(manga.ogTitle, sourceManager.getOrStub(manga.source)) != null) {
            logcat(LogPriority.WARN) { "Desktop: download folder of '${manga.ogTitle}' not renamed to '$newTitle'" }
        }
    }

    @Suppress("RedundantSuspendModifier")
    suspend fun renameChapter(source: Source, manga: Manga, oldChapter: Chapter, newChapter: Chapter) {
        logcat(LogPriority.WARN) { "Desktop: download folder of chapter '${oldChapter.name}' not renamed" }
    }
}
