package mihon.domain.migration.usecases

import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.source.Source
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import mihon.domain.migration.models.MigrationFlag
import mihon.domain.source.interactor.UpdateMangaFromRemote
import mihon.domain.source.models.RemoteMangaUpdate
import org.junit.jupiter.api.Test
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.history.interactor.UpsertHistory
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.InsertTrack
import kotlin.test.assertEquals

class MigrateMangaUseCaseTest {

    @Test
    fun `move downloads flag calls the mover and does not delete the old manga`() = runBlocking {
        val harness = harness()
        val oldChapter = chapter(id = 1L, number = 10.0)
        val targetChapter = chapter(id = 2L, number = 10.0)
        coEvery { harness.getChaptersByMangaId.await(harness.current.id) } returns listOf(oldChapter)
        coEvery { harness.getChaptersByMangaId.await(harness.target.id) } returns listOf(targetChapter)
        every { harness.downloadManager.isChapterDownloaded(any(), any(), any(), any()) } returns true
        val plan = slot<PlanDownloadMigration.Plan>()
        coEvery {
            harness.mover.move(capture(plan), any(), any(), any(), any())
        } returns MoveMigratedDownloads.Counts(moved = 1, skipped = 0)

        harness.useCase(
            current = harness.current,
            target = harness.target,
            replace = false,
            presetFlags = setOf(MigrationFlag.MOVE_DOWNLOADS, MigrationFlag.REMOVE_DOWNLOAD),
        )

        assertEquals(1L, plan.captured.moves.single().oldChapter.id)
        assertEquals(2L, plan.captured.moves.single().targetChapter.id)
        assertEquals(emptyList(), plan.captured.orphanOldChapters)
        verify(exactly = 0) { harness.downloadManager.deleteManga(any(), any(), any()) }
    }

    @Test
    fun `remove downloads flag deletes when move is off`() = runBlocking {
        val harness = harness()

        harness.useCase(
            current = harness.current,
            target = harness.target,
            replace = false,
            presetFlags = setOf(MigrationFlag.REMOVE_DOWNLOAD),
        )

        verify(exactly = 1) { harness.downloadManager.deleteManga(harness.current, harness.currentSource, any()) }
        coVerify(exactly = 0) { harness.mover.move(plan = any(), current = any(), target = any(), currentSource = any(), targetSource = any()) }
    }

    private fun harness(): Harness {
        val current = Manga.create().copy(id = 1L, source = 10L, ogTitle = "Old title")
        val target = Manga.create().copy(id = 2L, source = 20L, ogTitle = "New title")
        val currentSource = mockk<Source>()
        val targetSource = mockk<Source>()
        val sourceManager = mockk<SourceManager>()
        every { sourceManager.get(current.source) } returns currentSource
        every { sourceManager.get(target.source) } returns targetSource

        val downloadManager = mockk<DownloadManager>(relaxed = true)
        val updateManga = mockk<UpdateManga>(relaxed = true)
        val getChaptersByMangaId = mockk<GetChaptersByMangaId>(relaxed = true)
        val updateMangaFromRemote = mockk<UpdateMangaFromRemote>()
        coEvery {
            updateMangaFromRemote(
                manga = any(),
                fetchDetails = any(),
                fetchChapters = any(),
                manualFetch = any(),
                fetchWindow = any(),
                throttleFunc = any(),
            )
        } returns Result.success(RemoteMangaUpdate(target, emptyList()))

        val mover = mockk<MoveMigratedDownloads>(relaxed = true)
        val useCase = MigrateMangaUseCase(
            sourcePreferences = mockk<SourcePreferences>(relaxed = true),
            trackerManager = mockk<TrackerManager>(relaxed = true),
            sourceManager = sourceManager,
            downloadManager = downloadManager,
            updateManga = updateManga,
            getChaptersByMangaId = getChaptersByMangaId,
            updateMangaFromRemote = updateMangaFromRemote,
            updateChapter = mockk<UpdateChapter>(relaxed = true),
            getCategories = mockk<GetCategories>(relaxed = true),
            setMangaCategories = mockk<SetMangaCategories>(relaxed = true),
            getTracks = mockk<GetTracks>(relaxed = true),
            insertTrack = mockk<InsertTrack>(relaxed = true),
            coverCache = mockk<CoverCache>(relaxed = true),
            getHistory = mockk<GetHistory>(relaxed = true),
            upsertHistory = mockk<UpsertHistory>(relaxed = true),
            moveMigratedDownloads = mover,
        )
        return Harness(
            useCase = useCase,
            current = current,
            target = target,
            currentSource = currentSource,
            downloadManager = downloadManager,
            getChaptersByMangaId = getChaptersByMangaId,
            mover = mover,
        )
    }

    private fun chapter(id: Long, number: Double): Chapter = Chapter.create().copy(
        id = id,
        chapterNumber = number,
        name = "Chapter $number",
        url = "/chapter/$id/",
    )

    private data class Harness(
        val useCase: MigrateMangaUseCase,
        val current: Manga,
        val target: Manga,
        val currentSource: Source,
        val downloadManager: DownloadManager,
        val getChaptersByMangaId: GetChaptersByMangaId,
        val mover: MoveMigratedDownloads,
    )
}
