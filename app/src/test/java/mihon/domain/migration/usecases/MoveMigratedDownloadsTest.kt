package mihon.domain.migration.usecases

import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadProvider
import io.mockk.anyOrNull
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.download.repository.ChapterDownloadRepository
import tachiyomi.domain.manga.model.Manga
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals

class MoveMigratedDownloadsTest {

    @Test
    fun `does not copy or register a chapter id that is not in the database`() = runBlocking {
        val repository = mockk<ChapterDownloadRepository>(relaxed = true)
        val chapterRepository = mockk<ChapterRepository>(relaxed = true)
        val mover = mover(mockk(relaxed = true), repository, chapterRepository)
        val counts = mover.move(
            plan = PlanDownloadMigration.Plan(
                moves = listOf(
                    PlanDownloadMigration.Move(
                        oldChapter = chapter(id = 1L, number = 10.0),
                        targetChapter = chapter(id = -1L, number = 10.0),
                    ),
                ),
                orphanOldChapters = emptyList(),
            ),
            current = Manga.create().copy(id = 1L, ogTitle = "Old"),
            target = Manga.create().copy(id = 2L, ogTitle = "New"),
            currentSource = mockk(),
            targetSource = mockk(),
        )

        assertEquals(0, counts.moved)
        assertEquals(1, counts.skipped)
        coVerify(exactly = 0) { repository.upsert(any()) }
    }

    @Test
    fun `leaves the source file in place when the destination name already exists`() = runBlocking {
        val provider = mockk<DownloadProvider>()
        val sourceFile = mockk<UniFile>()
        every { sourceFile.name } returns "Chapter 10_old"
        every { sourceFile.isFile } returns false
        every { sourceFile.isDirectory } returns true
        every { sourceFile.delete() } returns true
        val oldDir = mockk<UniFile>()
        every { oldDir.findFile("Chapter 10_old") } returns sourceFile
        val targetDir = mockk<UniFile>()
        every { targetDir.findFile("Chapter 10_new") } returns mockk()

        every { provider.findMangaDir("Old", any()) } returns oldDir
        every { provider.getValidChapterDirNames(any(), anyOrNull(), any()) } returns listOf("Chapter 10_old")
        every { provider.getMangaDir(any(), any()) } returns Result.success(targetDir)
        every {
            provider.getChapterDirName(any(), anyOrNull(), any(), anyOrNull(), anyOrNull())
        } returns "Chapter 10_new"

        val repository = mockk<ChapterDownloadRepository>(relaxed = true)
        val counts = mover(provider, repository).move(
            plan = PlanDownloadMigration.Plan(
                moves = listOf(
                    PlanDownloadMigration.Move(
                        oldChapter = chapter(id = 4L, number = 10.0),
                        targetChapter = chapter(id = 8L, number = 10.0),
                    ),
                ),
                orphanOldChapters = emptyList(),
            ),
            current = Manga.create().copy(id = 1L, ogTitle = "Old"),
            target = Manga.create().copy(id = 2L, ogTitle = "New"),
            currentSource = mockk(),
            targetSource = mockk(),
        )

        assertEquals(0, counts.moved)
        assertEquals(1, counts.skipped)
        verify(exactly = 0) { sourceFile.delete() }
        coVerify(exactly = 0) { repository.upsert(any()) }
    }

    @Test
    fun `copies a cbz then registers the target chapter and deletes the old file`() = runBlocking {
        val provider = mockk<DownloadProvider>()
        val sourceFile = mockk<UniFile>()
        every { sourceFile.name } returns "old.cbz"
        every { sourceFile.isFile } returns true
        every { sourceFile.isDirectory } returns false
        every { sourceFile.openInputStream() } returns ByteArrayInputStream(byteArrayOf(1, 2, 3))
        every { sourceFile.delete() } returns true

        val oldDir = mockk<UniFile>()
        every { oldDir.findFile("Chapter 10_old") } returns sourceFile
        every { provider.findMangaDir("Old", any()) } returns oldDir
        every { provider.getValidChapterDirNames(any(), anyOrNull(), any()) } returns listOf("Chapter 10_old")

        val destination = mockk<UniFile>()
        every { destination.openOutputStream() } returns ByteArrayOutputStream()
        val targetDir = mockk<UniFile>()
        every { targetDir.findFile(any()) } returns null
        every { targetDir.createFile("Chapter 10_new.cbz") } returns destination
        every { provider.getMangaDir(any(), any()) } returns Result.success(targetDir)
        every {
            provider.getChapterDirName(any(), anyOrNull(), any(), anyOrNull(), anyOrNull())
        } returns "Chapter 10_new"
        every { provider.getRelativeChapterPath(any(), any(), "Chapter 10_new.cbz") } returns "NewSource/New/Chapter 10_new.cbz"

        val repository = mockk<ChapterDownloadRepository>(relaxed = true)
        val cache = mockk<DownloadCache>(relaxed = true)
        val counts = MoveMigratedDownloads(provider, repository, cache, mockk(relaxed = true)).move(
            plan = PlanDownloadMigration.Plan(
                moves = listOf(
                    PlanDownloadMigration.Move(
                        oldChapter = chapter(id = 4L, number = 10.0),
                        targetChapter = chapter(id = 8L, number = 10.0, name = "Ch. 10"),
                    ),
                ),
                orphanOldChapters = emptyList(),
            ),
            current = Manga.create().copy(id = 1L, ogTitle = "Old"),
            target = Manga.create().copy(id = 2L, ogTitle = "New"),
            currentSource = mockk(),
            targetSource = mockk(),
        )

        assertEquals(1, counts.moved)
        assertEquals(0, counts.skipped)
        coVerify {
            repository.upsert(
                match {
                    it.chapterId == 8L && it.relativePath == "NewSource/New/Chapter 10_new.cbz"
                },
            )
        }
        verify(exactly = 1) { sourceFile.delete() }
        coVerify { cache.addChapter("Chapter 10_new.cbz", targetDir, any()) }
    }

    @Test
    fun `unmatched download becomes an orphan on the target and keeps read state`() = runBlocking {
        val provider = mockk<DownloadProvider>()
        val sourceFile = mockk<UniFile>()
        every { sourceFile.name } returns "Chapter 1_abc123"
        every { sourceFile.isFile } returns false
        every { sourceFile.isDirectory } returns true
        every { sourceFile.listFiles() } returns emptyArray()
        every { sourceFile.delete() } returns true

        val oldDir = mockk<UniFile>()
        every { oldDir.findFile("Chapter 1_abc123") } returns sourceFile
        every { provider.findMangaDir("Old", any()) } returns oldDir
        every { provider.getValidChapterDirNames(any(), anyOrNull(), any()) } returns listOf("Chapter 1_abc123")

        val createdDir = mockk<UniFile>()
        val targetDir = mockk<UniFile>()
        every { targetDir.findFile(any()) } returns null
        every { targetDir.createDirectory("Chapter 1_abc123") } returns createdDir
        every { createdDir.listFiles() } returns emptyArray()
        every { provider.getMangaDir(any(), any()) } returns Result.success(targetDir)
        every {
            provider.getRelativeChapterPath(any(), any(), "Chapter 1_abc123")
        } returns "NewSource/New/Chapter 1_abc123"

        val chapterRepository = mockk<ChapterRepository>()
        coEvery { chapterRepository.addAll(any()) } answers {
            val draft = firstArg<List<Chapter>>().single()
            listOf(draft.copy(id = 55L))
        }
        val repository = mockk<ChapterDownloadRepository>(relaxed = true)
        val cache = mockk<DownloadCache>(relaxed = true)

        val counts = MoveMigratedDownloads(provider, repository, cache, chapterRepository).move(
            plan = PlanDownloadMigration.Plan(
                moves = emptyList(),
                orphanOldChapters = listOf(
                    chapter(id = 4L, number = 1.0, name = "Chapter 1").copy(read = true, bookmark = true),
                ),
            ),
            current = Manga.create().copy(id = 1L, ogTitle = "Old"),
            target = Manga.create().copy(id = 2L, ogTitle = "New"),
            currentSource = mockk(),
            targetSource = mockk(),
        )

        assertEquals(1, counts.moved)
        assertEquals(0, counts.skipped)
        coVerify {
            chapterRepository.addAll(
                match { chapters ->
                    val orphan = chapters.single()
                    orphan.url == "orphaned://Chapter 1_abc123" &&
                        orphan.name == "Chapter 1_abc123" &&
                        orphan.chapterNumber == 1.0 &&
                        orphan.read &&
                        orphan.bookmark &&
                        orphan.mangaId == 2L
                },
            )
        }
        coVerify {
            repository.upsert(match { it.chapterId == 55L })
        }
        verify(exactly = 1) { sourceFile.delete() }
        coVerify { cache.addChapter("Chapter 1_abc123", targetDir, any()) }
    }

    private fun mover(
        provider: DownloadProvider,
        repository: ChapterDownloadRepository,
        chapterRepository: ChapterRepository = mockk(relaxed = true),
    ): MoveMigratedDownloads = MoveMigratedDownloads(
        downloadProvider = provider,
        chapterDownloadRepository = repository,
        downloadCache = mockk(relaxed = true),
        chapterRepository = chapterRepository,
    )

    private fun chapter(
        id: Long,
        number: Double,
        name: String = "Chapter $number",
    ): Chapter = Chapter.create().copy(
        id = id,
        chapterNumber = number,
        name = name,
        url = "/chapter/$id/",
    )
}
