package mihon.domain.source.interactor

import eu.kanade.domain.chapter.interactor.SyncChaptersWithSource
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.repository.MangaRepository

class UpdateMangaFromRemoteTest {

    // A manga whose user has set a custom cover URL: `thumbnailUrl` returns it, `ogThumbnailUrl` is the source's
    private val manga: Manga = spyk(
        Manga.create().copy(id = 1L, source = 2L, url = "/manga/1", ogTitle = "Title", ogThumbnailUrl = SOURCE_URL),
    ).also { every { it.thumbnailUrl } returns CUSTOM_URL }

    private val mangaUpdate = slot<MangaUpdate>()
    private val mangaRepository = mockk<MangaRepository> {
        coEvery { update(capture(mangaUpdate)) } returns true
        coEvery { getMangaById(any()) } returns manga
    }
    private val chapterRepository = mockk<ChapterRepository> {
        coEvery { getChapterByMangaId(any(), any()) } returns emptyList()
    }
    private val syncChaptersWithSource = mockk<SyncChaptersWithSource> {
        coEvery { await(any(), any(), any(), any(), any()) } returns emptyList()
    }
    private val coverCache = mockk<CoverCache>(relaxed = true)

    private val updateMangaFromRemote = UpdateMangaFromRemote(
        sourceManager = mockk(),
        chapterRepository = chapterRepository,
        mangaRepository = mangaRepository,
        syncChaptersWithSource = syncChaptersWithSource,
        coverCache = coverCache,
        libraryPreferences = mockk(relaxed = true),
        downloadManager = mockk<DownloadManager>(relaxed = true),
    )

    @Test
    fun `toSManga passes the source cover URL, not the custom one`() {
        manga.toSManga().thumbnail_url shouldBe SOURCE_URL
    }

    @Test
    fun `update without details keeps the source cover URL and the cover cache`() = runBlocking<Unit> {
        // Like a source that skips the details request: it hands back the SManga it was given
        val source = mockk<Source> {
            coEvery { getMangaUpdate(any(), any(), false, true) } answers { SMangaUpdate(firstArg(), secondArg()) }
        }

        updateMangaFromRemote(source, manga, fetchDetails = false, fetchChapters = true).isSuccess shouldBe true

        mangaUpdate.captured.thumbnailUrl shouldBe SOURCE_URL
        mangaUpdate.captured.coverLastModified shouldBe null
        verify(exactly = 0) { coverCache.deleteFromCache(any(), any()) }
    }

    @Test
    fun `update with details stores the new source cover URL`() = runBlocking<Unit> {
        val source = mockk<Source> {
            coEvery { getMangaUpdate(any(), any(), true, true) } answers {
                val details = SManga.create().apply {
                    url = manga.url
                    title = "Title"
                    thumbnail_url = NEW_SOURCE_URL
                }
                SMangaUpdate(details, secondArg())
            }
        }

        updateMangaFromRemote(source, manga, fetchDetails = true, fetchChapters = true).isSuccess shouldBe true

        mangaUpdate.captured.thumbnailUrl shouldBe NEW_SOURCE_URL
    }

    private companion object {
        const val SOURCE_URL = "https://source.example/cover.jpg"
        const val NEW_SOURCE_URL = "https://source.example/cover-v2.jpg"
        const val CUSTOM_URL = "https://custom.example/my-cover.jpg"
    }
}
