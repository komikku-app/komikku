package mihon.domain.migration.usecases

import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import io.kotest.matchers.shouldBe
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import mihon.domain.migration.models.MigrationFlag
import mihon.domain.source.interactor.UpdateMangaFromRemote
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager

class MigrateMangaUseCaseTest {

    // Strict mocks: any call fails the test
    private val sourcePreferences = mockk<SourcePreferences>()
    private val sourceManager = mockk<SourceManager>()
    private val downloadManager = mockk<DownloadManager>()
    private val updateManga = mockk<UpdateManga>()
    private val coverCache = mockk<CoverCache>()
    private val updateMangaFromRemote = mockk<UpdateMangaFromRemote>()

    private val migrateManga = MigrateMangaUseCase(
        sourcePreferences = sourcePreferences,
        trackerManager = mockk(),
        sourceManager = sourceManager,
        downloadManager = downloadManager,
        updateManga = updateManga,
        getChaptersByMangaId = mockk(),
        updateChapter = mockk(),
        getCategories = mockk(),
        setMangaCategories = mockk(),
        getTracks = mockk(),
        insertTrack = mockk(),
        coverCache = coverCache,
        updateMangaFromRemote = updateMangaFromRemote,
        getHistory = mockk(),
        upsertHistory = mockk(),
    )

    private val manga = Manga.create().copy(id = 1L, source = 2L, url = "/manga/1", ogTitle = "Title")

    @Test
    fun `migrating an entry onto itself changes nothing`() = runBlocking {
        val migrated = migrateManga(
            current = manga,
            target = manga.copy(),
            replace = true,
            presetFlags = MigrationFlag.entries.toSet(),
        )

        migrated shouldBe false

        verify {
            sourcePreferences wasNot Called
            sourceManager wasNot Called
            downloadManager wasNot Called
            updateManga wasNot Called
            coverCache wasNot Called
            updateMangaFromRemote wasNot Called
        }
    }

    @Test
    fun `a migration to a source that is not installed reports that nothing was migrated`() = runBlocking {
        every { sourceManager.get(any()) } returns null
        val target = manga.copy(id = 3L, source = 4L)

        migrateManga(current = manga, target = target, replace = true) shouldBe false

        verify {
            downloadManager wasNot Called
            updateManga wasNot Called
        }
    }
}
