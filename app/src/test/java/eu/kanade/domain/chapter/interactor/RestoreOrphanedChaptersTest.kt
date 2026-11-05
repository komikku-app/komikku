package eu.kanade.domain.chapter.interactor

import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.source.Source
import io.mockk.CapturingSlot
import io.mockk.capture
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.manga.interactor.GetAllManga
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import kotlin.test.assertEquals

class RestoreOrphanedChaptersTest {

    private val mangaTitle = "Dungeon Tou de Yadoya wo Yarou"

    @Test
    fun `restore skips url-drifted folder when catalog chapter matches by number`() = runBlocking {
        val manga = Manga.create().copy(
            id = 3607L,
            source = 99L,
            title = mangaTitle,
            ogTitle = mangaTitle,
        )
        val catalog = listOf(
            catalogChapter(id = 60L, number = 60.0, name = "Ch. 60", scanlator = "unofficial"),
        )

        val restore = createRestore(
            manga = manga,
            catalog = catalog,
            folderNames = listOf("Chapter 60_a69da5"),
        )

        val restored = restore.await(mangaIds = listOf(manga.id))

        assertEquals(0, restored)
        coVerify(exactly = 0) { restore.chapterRepository.addAll(any()) }
    }

    @Test
    fun `restore inserts orphan when folder has no catalog match`() = runBlocking {
        val manga = Manga.create().copy(
            id = 3607L,
            source = 99L,
            title = mangaTitle,
            ogTitle = mangaTitle,
        )

        val restore = createRestore(
            manga = manga,
            catalog = emptyList(),
            folderNames = listOf("Bonus Story"),
        )

        val restored = restore.await(mangaIds = listOf(manga.id))

        assertEquals(1, restored)
        coVerify(exactly = 1) { restore.chapterRepository.addAll(any()) }
    }

    @Test
    fun `restore skips Fallguy folder when catalog already has that chapter number`() = runBlocking {
        val manga = Manga.create().copy(
            id = 3607L,
            source = 99L,
            title = mangaTitle,
            ogTitle = mangaTitle,
        )
        val catalog = listOf(
            catalogChapter(id = 1L, number = 10.0, name = "Chapter 10", scanlator = "Group A"),
            catalogChapter(id = 2L, number = 10.0, name = "Chapter 10", scanlator = "Group B"),
        )

        val restore = createRestore(
            manga = manga,
            catalog = catalog,
            folderNames = listOf("Fallguy69420_Chapter 10"),
        )

        val restored = restore.await(mangaIds = listOf(manga.id))

        assertEquals(0, restored)
        coVerify(exactly = 0) { restore.chapterRepository.addAll(any()) }
    }

    @Test
    fun `restore inserts orphan when parsed Fallguy number is absent from the catalog`() = runBlocking {
        val manga = Manga.create().copy(
            id = 3607L,
            source = 99L,
            title = mangaTitle,
            ogTitle = mangaTitle,
        )
        val inserted = slot<List<Chapter>>()
        val restore = createRestore(
            manga = manga,
            catalog = listOf(
                catalogChapter(id = 1L, number = 10.0, name = "Chapter 10", scanlator = null),
            ),
            folderNames = listOf("Fallguy69420_Chapter 99"),
            inserted = inserted,
        )

        val restored = restore.await(mangaIds = listOf(manga.id))

        assertEquals(1, restored)
        assertEquals(99.0, inserted.captured.single().chapterNumber)
    }

    private fun createRestore(
        manga: Manga,
        catalog: List<Chapter>,
        folderNames: List<String>,
        inserted: CapturingSlot<List<Chapter>>? = null,
    ): TestRestoreHarness {
        val getAllManga = mockk<GetAllManga>()
        val getManga = mockk<GetManga>()
        val getChaptersByMangaId = mockk<GetChaptersByMangaId>()
        val chapterRepository = mockk<ChapterRepository>(relaxed = true)
        val downloadProvider = mockk<DownloadProvider>()
        val sourceManager = mockk<SourceManager>()
        val comicInfoReader = mockk<ChapterDownloadComicInfoReader>()
        val source = mockk<Source>()

        coEvery { getManga.await(manga.id) } returns manga
        coEvery { getChaptersByMangaId.await(manga.id) } returns catalog
        if (inserted != null) {
            coEvery { chapterRepository.addAll(capture(inserted)) } answers { firstArg() }
        } else {
            coEvery { chapterRepository.addAll(any()) } answers { firstArg() }
        }
        coEvery { sourceManager.getOrStub(manga.source) } returns source
        every { source.id } returns manga.source

        catalog.forEach { chapter ->
            every {
                downloadProvider.getValidChapterDirNames(chapter.name, chapter.scanlator, chapter.url)
            } returns listOf("unofficial_Ch. ${chapter.chapterNumber.toInt()}_currenthash")
        }
        if (catalog.isEmpty()) {
            every { downloadProvider.getValidChapterDirNames(any(), any(), any()) } returns emptyList()
        }

        val chapterEntries = folderNames.map(::mockChapterEntry)
        val mangaDir = mockk<UniFile>()
        every { mangaDir.listFiles() } returns chapterEntries.toTypedArray()
        every { downloadProvider.findMangaDir(manga.ogTitle, source) } returns mangaDir
        coEvery { comicInfoReader.readComicInfo(any()) } returns null

        chapterEntries.forEach { entry ->
            every { downloadProvider.resolveChapterImageDir(entry) } answers {
                val entryName = entry.name.orEmpty()
                DownloadProvider.ResolvedChapterImageDir(
                    chapterName = entryName,
                    imageDir = mockk(),
                    isValid = true,
                )
            }
        }

        val restore = RestoreOrphanedChapters(
            getAllManga = getAllManga,
            getManga = getManga,
            getChaptersByMangaId = getChaptersByMangaId,
            chapterRepository = chapterRepository,
            downloadProvider = downloadProvider,
            sourceManager = sourceManager,
            comicInfoReader = comicInfoReader,
        )

        return TestRestoreHarness(restore, chapterRepository)
    }

    private fun mockChapterEntry(name: String): UniFile {
        val entry = mockk<UniFile>()
        every { entry.name } returns name
        every { entry.isDirectory } returns !name.endsWith(".cbz")
        every { entry.lastModified() } returns 1L
        return entry
    }

    private fun catalogChapter(
        id: Long,
        number: Double,
        name: String,
        scanlator: String?,
    ): Chapter = Chapter.create().copy(
        id = id,
        chapterNumber = number,
        name = name,
        scanlator = scanlator,
        url = "/manga/example/chapter-$number/",
    )

    private data class TestRestoreHarness(
        val restore: RestoreOrphanedChapters,
        val chapterRepository: ChapterRepository,
    ) {
        suspend fun await(mangaIds: List<Long>): Int = restore.await(mangaIds)
    }
}
