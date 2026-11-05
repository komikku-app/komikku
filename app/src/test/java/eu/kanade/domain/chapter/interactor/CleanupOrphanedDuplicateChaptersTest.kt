package eu.kanade.domain.chapter.interactor

import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.source.Source
import io.mockk.anyOrNull
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.download.model.ChapterDownload
import tachiyomi.domain.download.repository.ChapterDownloadRepository
import tachiyomi.domain.manga.interactor.GetAllManga
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CleanupOrphanedDuplicateChaptersTest {

    @Test
    fun `retargets Fallguy orphan onto catalog chapter 10`() = runBlocking {
        val manga = Manga.create().copy(
            id = 15L,
            source = 99L,
            title = "Gentleman's Maid Sophie",
            ogTitle = "Gentleman's Maid Sophie",
        )
        val catalog = Chapter.create().copy(
            id = 5L,
            mangaId = manga.id,
            name = "Chapter 10",
            chapterNumber = 10.0,
            sourceOrder = 1L,
            url = "/chapter/10/",
            read = false,
        )
        val orphan = Chapter.create().copy(
            id = 9L,
            mangaId = manga.id,
            name = "Fallguy69420_Chapter 10",
            chapterNumber = 69420.0,
            url = "orphaned://Fallguy69420_Chapter 10",
            read = true,
        )

        val entry = mockk<UniFile>()
        every { entry.name } returns "Fallguy69420_Chapter 10"
        every { entry.isFile } returns false
        every { entry.isDirectory } returns true
        every { entry.renameTo("Chapter 10_aabbcc") } returns true

        val mangaDir = mockk<UniFile>()
        every { mangaDir.findFile(any()) } answers {
            if (firstArg<String>() == "Fallguy69420_Chapter 10") entry else null
        }

        val downloadProvider = mockk<DownloadProvider>()
        every { downloadProvider.findMangaDir(manga.ogTitle, any()) } returns mangaDir
        every {
            downloadProvider.getChapterDirName(any(), anyOrNull(), any(), anyOrNull(), anyOrNull())
        } returns "Chapter 10_aabbcc"
        every { downloadProvider.getRelativeChapterPath(any(), any(), any()) } answers {
            "Source/Title/${thirdArg<String>()}"
        }

        val source = mockk<Source>()
        every { source.id } returns manga.source
        val sourceManager = mockk<SourceManager>()
        coEvery { sourceManager.getOrStub(manga.source) } returns source

        val getManga = mockk<GetManga>()
        coEvery { getManga.await(manga.id) } returns manga
        val getChaptersByMangaId = mockk<GetChaptersByMangaId>()
        coEvery { getChaptersByMangaId.await(manga.id) } returns listOf(catalog, orphan)

        val chapterRepository = mockk<ChapterRepository>(relaxed = true)
        val updateSlot = slot<ChapterUpdate>()
        coEvery { chapterRepository.update(capture(updateSlot)) } returns Unit

        val chapterDownloadRepository = mockk<ChapterDownloadRepository>(relaxed = true)
        coEvery { chapterDownloadRepository.getByChapterId(any()) } returns null
        val downloadSlot = slot<ChapterDownload>()
        coEvery { chapterDownloadRepository.upsert(capture(downloadSlot)) } returns Unit

        val cleanup = CleanupOrphanedDuplicateChapters(
            getAllManga = mockk<GetAllManga>(),
            getManga = getManga,
            getChaptersByMangaId = getChaptersByMangaId,
            chapterRepository = chapterRepository,
            downloadProvider = downloadProvider,
            sourceManager = sourceManager,
            comicInfoReader = mockk(relaxed = true),
            chapterDownloadRepository = chapterDownloadRepository,
            downloadCache = mockk<DownloadCache>(relaxed = true),
        )

        val removed = cleanup.await(listOf(manga.id))

        assertEquals(1, removed)
        assertTrue(downloadSlot.captured.chapterId == 5L)
        assertEquals("Source/Title/Chapter 10_aabbcc", downloadSlot.captured.relativePath)
        assertEquals(5L, updateSlot.captured.id)
        assertEquals(true, updateSlot.captured.read)
        coVerify { chapterRepository.removeChaptersWithIds(listOf(9L)) }
        verify { entry.renameTo("Chapter 10_aabbcc") }
    }
}
