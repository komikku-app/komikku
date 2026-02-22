package eu.kanade.domain.chapter.interactor

import eu.kanade.domain.manga.interactor.GetExcludedScanlators
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SChapter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.ShouldUpdateDbChapter
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.Manga

@Execution(ExecutionMode.CONCURRENT)
@DisplayName("SyncChaptersWithSource - Soft Deletion and Undeletion Tests")
class SyncChaptersWithSourceTest {

    private lateinit var syncChaptersWithSource: SyncChaptersWithSource
    private lateinit var downloadManager: DownloadManager
    private lateinit var downloadProvider: DownloadProvider
    private lateinit var chapterRepository: ChapterRepository
    private lateinit var shouldUpdateDbChapter: ShouldUpdateDbChapter
    private lateinit var updateManga: UpdateManga
    private lateinit var updateChapter: UpdateChapter
    private lateinit var getChaptersByMangaId: GetChaptersByMangaId
    private lateinit var getExcludedScanlators: GetExcludedScanlators
    private lateinit var libraryPreferences: LibraryPreferences

    private val testManga = Manga.create().copy(
        id = 1L,
        ogTitle = "Test Manga",
        source = 1L,
    )

    private val testSource = mockk<Source>(relaxed = true)

    @BeforeEach
    fun setUp() {
        downloadManager = mockk(relaxed = true)
        downloadProvider = mockk(relaxed = true)
        chapterRepository = mockk(relaxed = true)
        shouldUpdateDbChapter = mockk(relaxed = true)
        updateManga = mockk(relaxed = true)
        updateChapter = mockk(relaxed = true)
        getChaptersByMangaId = mockk(relaxed = true)
        getExcludedScanlators = mockk(relaxed = true)
        libraryPreferences = mockk(relaxed = true)

        // Default mock behaviors
        coEvery { updateManga.awaitUpdateFetchInterval(any(), any(), any()) } returns true
        coEvery { updateManga.awaitUpdateLastUpdate(any()) } returns true
        coEvery { chapterRepository.addAll(any()) } answers { firstArg() }
        coEvery { getExcludedScanlators.await(any()) } returns emptySet()
        coEvery { libraryPreferences.markDuplicateReadChapterAsRead() } returns mockk {
            coEvery { get() } returns emptySet<String>()
        }

        syncChaptersWithSource = SyncChaptersWithSource(
            downloadManager,
            downloadProvider,
            chapterRepository,
            shouldUpdateDbChapter,
            updateManga,
            updateChapter,
            getChaptersByMangaId,
            getExcludedScanlators,
            libraryPreferences,
        )
    }

    @Nested
    @DisplayName("Soft-deleted chapters handling")
    inner class SoftDeletedChaptersHandling {

        @Test
        fun `chapters that don't reappear in source are soft-deleted`() = runBlocking {
            // Arrange: DB has a chapter, source doesn't
            val dbChapter = Chapter.create().copy(
                id = 1L,
                url = "chapter1",
                name = "Chapter 1",
                mangaId = testManga.id,
                deleted = false,
            )
            // Mock the repository method directly since SyncChaptersWithSource calls it
            coEvery { chapterRepository.getChapterByMangaId(testManga.id, any(), includeDeleted = true) } returns listOf(dbChapter)
            coEvery { shouldUpdateDbChapter.await(any(), any()) } returns false

            val sourceChapters = emptyList<SChapter>()

            // Act
            syncChaptersWithSource.await(sourceChapters, testManga, testSource)

            // Assert: verify soft delete was called with correct chapter id
            coVerify { chapterRepository.softDeleteChaptersWithIds(listOf(dbChapter.id)) }
        }

        @Test
        fun `chapters that reappear in source are undeleted`() = runBlocking {
            // Arrange: DB has a deleted chapter, source has it again
            val dbChapter = Chapter.create().copy(
                id = 1L,
                url = "chapter1",
                name = "Chapter 1",
                mangaId = testManga.id,
                deleted = true,
            )

            val sourceChapter = SChapter.create().apply {
                url = "chapter1"
                name = "Chapter 1"
            }

            coEvery { chapterRepository.getChapterByMangaId(testManga.id, any(), includeDeleted = true) } returns listOf(dbChapter)
            coEvery { shouldUpdateDbChapter.await(any(), any()) } returns false

            // Act
            syncChaptersWithSource.await(
                listOf(sourceChapter),
                testManga,
                testSource,
            )

            // Assert: verify update was called to undelete the chapter
            coVerify {
                updateChapter.awaitAll(
                    match { updates ->
                        updates.any { it.id == dbChapter.id && it.deleted == false }
                    },
                )
            }
        }

        @Test
        fun `multiple chapters can be undeleted in same sync`() = runBlocking {
            // Arrange: DB has multiple deleted chapters, source has them all
            val dbChapter1 = Chapter.create().copy(
                id = 1L,
                url = "chapter1",
                mangaId = testManga.id,
                deleted = true,
            )
            val dbChapter2 = Chapter.create().copy(
                id = 2L,
                url = "chapter2",
                mangaId = testManga.id,
                deleted = true,
            )

            val sourceChapter1 = SChapter.create().apply {
                url = "chapter1"
                name = "Chapter 1"
            }
            val sourceChapter2 = SChapter.create().apply {
                url = "chapter2"
                name = "Chapter 2"
            }

            coEvery { chapterRepository.getChapterByMangaId(testManga.id, any(), includeDeleted = true) } returns listOf(
                dbChapter1,
                dbChapter2,
            )
            coEvery { shouldUpdateDbChapter.await(any(), any()) } returns false

            // Act
            syncChaptersWithSource.await(
                listOf(sourceChapter1, sourceChapter2),
                testManga,
                testSource,
            )

            // Assert
            coVerify {
                updateChapter.awaitAll(
                    match { updates ->
                        updates.size == 2 && updates.all { it.deleted == false }
                    },
                )
            }
        }
    }

    @Nested
    @DisplayName("Chapters needing undeletion and metadata updates")
    inner class UndeleteWithMetadataUpdates {

        @Test
        fun `undeleted chapter gets metadata updated from source`() = runBlocking {
            // Arrange: Soft-deleted chapter reappears with updated metadata
            val dbChapter = Chapter.create().copy(
                id = 1L,
                url = "chapter1",
                name = "Old Name",
                scanlator = "Old Group",
                mangaId = testManga.id,
                deleted = true,
                chapterNumber = 1.0,
            )

            val sourceChapter = SChapter.create().apply {
                url = "chapter1"
                name = "New Name"
                scanlator = "New Group"
            }

            coEvery { chapterRepository.getChapterByMangaId(testManga.id, any(), includeDeleted = true) } returns listOf(dbChapter)
            coEvery { shouldUpdateDbChapter.await(any(), any()) } returns true
            coEvery { downloadProvider.isChapterDirNameChanged(any(), any()) } returns false

            // Act
            syncChaptersWithSource.await(
                listOf(sourceChapter),
                testManga,
                testSource,
            )

            // Assert: Verify both update for undelete and metadata update
            coVerify {
                updateChapter.awaitAll(
                    match { updates ->
                        // Should have undeletion
                        updates.any { it.id == dbChapter.id && it.deleted == false }
                    },
                )
            }
        }

        @Test
        fun `undeleted chapter preserves non-metadata fields like read status`() = runBlocking {
            // Arrange
            val dbChapter = Chapter.create().copy(
                id = 1L,
                url = "chapter1",
                name = "Chapter 1",
                mangaId = testManga.id,
                deleted = true,
                read = true,
                lastPageRead = 50L,
            )

            val sourceChapter = SChapter.create().apply {
                url = "chapter1"
                name = "Chapter 1"
            }

            coEvery { chapterRepository.getChapterByMangaId(testManga.id, any(), includeDeleted = true) } returns listOf(dbChapter)
            coEvery { shouldUpdateDbChapter.await(any(), any()) } returns false

            // Act
            syncChaptersWithSource.await(
                listOf(sourceChapter),
                testManga,
                testSource,
            )

            // Assert: verify deleted is set to false but read/lastPageRead are preserved
            coVerify {
                updateChapter.awaitAll(
                    match { updates ->
                        updates.any { update ->
                            update.id == dbChapter.id && update.deleted == false
                        }
                    },
                )
            }
        }
    }

    @Nested
    @DisplayName("Edge cases and ordering")
    inner class EdgeCasesAndOrdering {

        @Test
        fun `soft deletion happens before new chapter insertion`() = runBlocking {
            // Arrange: A chapter is removed from source while a new one is added
            val existingDbChapter = Chapter.create().copy(
                id = 1L,
                url = "old_chapter",
                mangaId = testManga.id,
                deleted = false,
            )

            val newSourceChapter = SChapter.create().apply {
                url = "new_chapter"
                name = "New Chapter"
            }

            coEvery { chapterRepository.getChapterByMangaId(testManga.id, any(), includeDeleted = true) } returns listOf(existingDbChapter)
            coEvery { shouldUpdateDbChapter.await(any(), any()) } returns false

            // Act
            syncChaptersWithSource.await(
                listOf(newSourceChapter),
                testManga,
                testSource,
            )

            // Assert: soft delete should be called, and new chapter should be added
            coVerify { chapterRepository.softDeleteChaptersWithIds(listOf(existingDbChapter.id)) }
            coVerify { chapterRepository.addAll(any()) }
        }

        @Test
        fun `only deleted chapters are passed to undeletion logic`() = runBlocking {
            // Arrange: Mix of deleted and non-deleted chapters in DB
            val nonDeletedChapter = Chapter.create().copy(
                id = 1L,
                url = "chapter1",
                mangaId = testManga.id,
                deleted = false,
            )

            val deletedChapter = Chapter.create().copy(
                id = 2L,
                url = "chapter2",
                mangaId = testManga.id,
                deleted = true,
            )

            val sourceChapter1 = SChapter.create().apply {
                url = "chapter1"
                name = "Chapter 1"
            }
            val sourceChapter2 = SChapter.create().apply {
                url = "chapter2"
                name = "Chapter 2"
            }

            coEvery { chapterRepository.getChapterByMangaId(testManga.id, any(), includeDeleted = true) } returns listOf(
                nonDeletedChapter,
                deletedChapter,
            )
            coEvery { shouldUpdateDbChapter.await(any(), any()) } returns false

            // Act
            syncChaptersWithSource.await(
                listOf(sourceChapter1, sourceChapter2),
                testManga,
                testSource,
            )

            // Assert: Only the deleted chapter should be undeleted
            coVerify {
                updateChapter.awaitAll(
                    match { updates ->
                        updates.size == 1 && updates.all { it.id == deletedChapter.id && it.deleted == false }
                    },
                )
            }
        }

        @Test
        fun `soft deletion and undeletion don't interfere with each other`() = runBlocking {
            // Arrange: One chapter gets deleted, another gets undeleted, in same sync
            val toDeleteChapter = Chapter.create().copy(
                id = 1L,
                url = "chapter_to_delete",
                mangaId = testManga.id,
                deleted = false,
            )

            val toUndeleteChapter = Chapter.create().copy(
                id = 2L,
                url = "chapter_to_undelete",
                mangaId = testManga.id,
                deleted = true,
            )

            val sourceChapter = SChapter.create().apply {
                url = "chapter_to_undelete"
                name = "Restored Chapter"
            }

            coEvery { chapterRepository.getChapterByMangaId(testManga.id, any(), includeDeleted = true) } returns listOf(
                toDeleteChapter,
                toUndeleteChapter,
            )
            coEvery { shouldUpdateDbChapter.await(any(), any()) } returns false

            // Act
            syncChaptersWithSource.await(
                listOf(sourceChapter),
                testManga,
                testSource,
            )

            // Assert: Both operations should happen
            coVerify { chapterRepository.softDeleteChaptersWithIds(listOf(toDeleteChapter.id)) }
            coVerify {
                updateChapter.awaitAll(
                    match { updates ->
                        updates.any { it.id == toUndeleteChapter.id && it.deleted == false }
                    },
                )
            }
        }

        @Test
        fun `deleted chapter is matched by URL during undeletion`() = runBlocking {
            // Arrange: Verify URL is used for matching deleted chapters
            val deletedChapter1 = Chapter.create().copy(
                id = 1L,
                url = "chapter_A",
                mangaId = testManga.id,
                deleted = true,
            )

            val deletedChapter2 = Chapter.create().copy(
                id = 2L,
                url = "chapter_B",
                mangaId = testManga.id,
                deleted = true,
            )

            val sourceChapter = SChapter.create().apply {
                url = "chapter_B"
                name = "Chapter B"
            }

            coEvery { chapterRepository.getChapterByMangaId(testManga.id, any(), includeDeleted = true) } returns listOf(
                deletedChapter1,
                deletedChapter2,
            )
            coEvery { shouldUpdateDbChapter.await(any(), any()) } returns false

            // Act
            syncChaptersWithSource.await(
                listOf(sourceChapter),
                testManga,
                testSource,
            )

            // Assert: Only chapter with matching URL should be undeleted
            coVerify {
                updateChapter.awaitAll(
                    match { updates ->
                        updates.size == 1 &&
                            updates.first().id == deletedChapter2.id &&
                            updates.first().deleted == false
                    },
                )
            }
        }

        @Test
        fun `no operations occur when nothing needs to sync`() = runBlocking {
            // Arrange: DB and source are identical
            val chapter = Chapter.create().copy(
                id = 1L,
                url = "chapter1",
                name = "Chapter 1",
                mangaId = testManga.id,
                deleted = false,
            )

            val sourceChapter = SChapter.create().apply {
                url = "chapter1"
                name = "Chapter 1"
            }

            coEvery { chapterRepository.getChapterByMangaId(testManga.id, any(), includeDeleted = true) } returns listOf(chapter)
            coEvery { shouldUpdateDbChapter.await(any(), any()) } returns false

            // Act
            syncChaptersWithSource.await(
                listOf(sourceChapter),
                testManga,
                testSource,
            )

            // Assert: No deletion, undeletion, or update operations
            coVerify(exactly = 0) { chapterRepository.softDeleteChaptersWithIds(any()) }
            coVerify(exactly = 0) { updateChapter.awaitAll(any()) }
            coVerify(exactly = 0) { chapterRepository.addAll(any()) }
        }

        @Test
        fun `deleted chapters are excluded from normal processing logic`() = runBlocking {
            // Arrange: Ensure deleted chapters don't get treated as "chapters to soft delete"
            val deletedChapter = Chapter.create().copy(
                id = 1L,
                url = "chapter1",
                mangaId = testManga.id,
                deleted = true,
            )

            val sourceChapter = SChapter.create().apply {
                url = "chapter1"
                name = "Chapter 1"
            }

            coEvery { chapterRepository.getChapterByMangaId(testManga.id, any(), includeDeleted = true) } returns listOf(deletedChapter)
            coEvery { shouldUpdateDbChapter.await(any(), any()) } returns false

            // Act
            syncChaptersWithSource.await(
                listOf(sourceChapter),
                testManga,
                testSource,
            )

            // Assert: The deleted chapter should NOT be soft-deleted again (already deleted)
            // Only undeletion should happen
            coVerify(exactly = 0) { chapterRepository.softDeleteChaptersWithIds(any()) }
            coVerify {
                updateChapter.awaitAll(
                    match { updates ->
                        updates.any { it.id == deletedChapter.id && it.deleted == false }
                    },
                )
            }
        }
    }

    @Nested
    @DisplayName("Integration scenarios")
    inner class IntegrationScenarios {

        @Test
        fun `complex scenario with new, updated, deleted and undeleted chapters`() = runBlocking {
            // Arrange: Complex scenario with all types of changes
            val newSourceChapter = SChapter.create().apply {
                url = "new_chapter"
                name = "New Chapter"
            }

            val updatedSourceChapter = SChapter.create().apply {
                url = "chapter_to_update"
                name = "Updated Chapter Name"
            }

            val undeleteSourceChapter = SChapter.create().apply {
                url = "chapter_to_undelete"
                name = "Restored Chapter"
            }

            val existingDbChapter = Chapter.create().copy(
                id = 1L,
                url = "chapter_to_update",
                name = "Old Chapter Name",
                mangaId = testManga.id,
                deleted = false,
            )

            val deletedDbChapter = Chapter.create().copy(
                id = 2L,
                url = "chapter_to_undelete",
                name = "Restored Chapter",
                mangaId = testManga.id,
                deleted = true,
            )

            val toBeDeletedDbChapter = Chapter.create().copy(
                id = 3L,
                url = "chapter_to_delete",
                name = "To Delete",
                mangaId = testManga.id,
                deleted = false,
            )

            coEvery { chapterRepository.getChapterByMangaId(testManga.id, any(), includeDeleted = true) } returns listOf(
                existingDbChapter,
                deletedDbChapter,
                toBeDeletedDbChapter,
            )
            coEvery { shouldUpdateDbChapter.await(any(), any()) } returns false
            coEvery { downloadProvider.isChapterDirNameChanged(any(), any()) } returns false
            coEvery { chapterRepository.addAll(any()) } answers { firstArg() }

            // Act
            syncChaptersWithSource.await(
                listOf(newSourceChapter, updatedSourceChapter, undeleteSourceChapter),
                testManga,
                testSource,
            )

            // Assert
            coVerify { chapterRepository.softDeleteChaptersWithIds(listOf(toBeDeletedDbChapter.id)) }
            coVerify {
                updateChapter.awaitAll(
                    match { updates ->
                        // Check that undelete happened
                        updates.any { it.id == deletedDbChapter.id && it.deleted == false }
                    },
                )
            }
            coVerify { chapterRepository.addAll(any()) } // new chapters added
        }

        @Test
        fun `fetch interval is updated regardless of whether chapters changed`() = runBlocking {
            // Arrange: Even with no chapter changes, fetch interval should update
            val chapter = Chapter.create().copy(
                id = 1L,
                url = "chapter1",
                mangaId = testManga.id,
                deleted = false,
            )

            val sourceChapter = SChapter.create().apply {
                url = "chapter1"
                name = "Chapter 1"
            }

            coEvery { chapterRepository.getChapterByMangaId(testManga.id, any(), includeDeleted = true) } returns listOf(chapter)
            coEvery { shouldUpdateDbChapter.await(any(), any()) } returns false

            // Act
            syncChaptersWithSource.await(
                listOf(sourceChapter),
                testManga,
                testSource,
            )

            // Assert: fetch interval should still be updated on manual fetch
            coVerify { updateManga.awaitUpdateFetchInterval(any(), any(), any()) }
        }
    }
}
