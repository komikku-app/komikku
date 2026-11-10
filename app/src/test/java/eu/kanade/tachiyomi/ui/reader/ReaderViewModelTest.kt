// KMK -->
package eu.kanade.tachiyomi.ui.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import eu.kanade.domain.manga.interactor.SetMangaViewerFlags
import eu.kanade.domain.manga.model.readingMode
import eu.kanade.tachiyomi.data.database.models.ChapterImpl
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.repository.MangaRepository
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {

    @ParameterizedTest(name = "reading mode change stays responsive with pending database = {0}")
    @ValueSource(booleans = [true, false])
    fun `reading mode change does not block the UI and preserves the current page`(pendingDatabase: Boolean) = runBlocking {
        val mainExecutor = Executors.newSingleThreadExecutor()
        val mainDispatcher = mainExecutor.asCoroutineDispatcher()
        Dispatchers.setMain(mainDispatcher)
        val writeStarted = CountDownLatch(1)
        val allowWrite = CompletableDeferred<Unit>().apply {
            if (!pendingDatabase) complete(Unit)
        }
        var createdViewModel: ReaderViewModel? = null
        var submittedChange: Future<*>? = null
        try {
            var storedManga = Manga.create().copy(id = 1L)
            val repository = mockk<MangaRepository>()
            coEvery { repository.getMangaById(1L) } answers { storedManga }
            coEvery { repository.update(any()) } coAnswers {
                writeStarted.countDown()
                allowWrite.await()
                storedManga = storedManga.copy(viewerFlags = firstArg<MangaUpdate>().viewerFlags!!)
                true
            }
            val chapter = ReaderChapter(
                ChapterImpl().apply {
                    id = 10L
                    manga_id = 1L
                    url = "/chapter/10"
                    last_page_read = 7
                },
            )
            val chapters = ViewerChapters(chapter, null, null)
            val viewModel = createViewModel(repository).also { createdViewModel = it }
            seedState(viewModel, storedManga, chapters)

            val invocation = mainExecutor.submit { viewModel.setMangaReadingMode(ReadingMode.WEBTOON) }
                .also { submittedChange = it }
            assertTrue(writeStarted.await(2, TimeUnit.SECONDS), "The reading mode update must reach the repository")
            assertDoesNotThrow(
                { invocation.get(2, TimeUnit.SECONDS) },
                "The UI callback must return while database work or event delivery is pending",
            )
            allowWrite.complete(Unit)
            // No receiver is registered until the UI callback has returned.
            assertEquals(
                ReaderViewModel.Event.ReloadViewerChapters,
                withTimeout(2_000.milliseconds) { viewModel.eventFlow.first() },
            )
            assertEquals(ReadingMode.WEBTOON.flagValue.toLong(), viewModel.state.value.manga?.readingMode)
            assertSame(chapters, viewModel.state.value.viewerChapters)
            assertEquals(7, chapter.requestedPage)
            coVerify(exactly = 1) { repository.update(any()) }
        } finally {
            allowWrite.complete(Unit)
            submittedChange?.cancel(true)
            createdViewModel?.viewModelScope?.cancel()
            mainExecutor.shutdownNow()
            mainExecutor.awaitTermination(2, TimeUnit.SECONDS)
            Dispatchers.resetMain()
            mainDispatcher.close()
        }
    }

    @Test
    fun `repeated reading mode changes ignore a cancelled database result`() = runBlocking {
        val mainExecutor = Executors.newSingleThreadExecutor()
        val mainDispatcher = mainExecutor.asCoroutineDispatcher()
        Dispatchers.setMain(mainDispatcher)
        val staleReadStarted = CountDownLatch(1)
        val staleReadReturned = CountDownLatch(1)
        val allowStaleRead = CompletableDeferred<Unit>()
        val readCount = AtomicInteger()
        var createdViewModel: ReaderViewModel? = null
        var firstInvocation: Future<*>? = null
        var latestInvocation: Future<*>? = null
        try {
            var storedManga = Manga.create().copy(id = 1L)
            val repository = mockk<MangaRepository>()
            coEvery { repository.getMangaById(1L) } coAnswers {
                val snapshot = storedManga
                if (readCount.incrementAndGet() == 2) {
                    staleReadStarted.countDown()
                    try {
                        allowStaleRead.await()
                    } catch (_: CancellationException) {
                        // Model a database layer that returns a stale result after swallowing cancellation.
                        withContext(NonCancellable) { allowStaleRead.await() }
                    }
                    staleReadReturned.countDown()
                }
                snapshot
            }
            coEvery { repository.update(any()) } answers {
                storedManga = storedManga.copy(viewerFlags = firstArg<MangaUpdate>().viewerFlags!!)
                true
            }
            val chapters = ViewerChapters(
                ReaderChapter(ChapterImpl().apply { url = "/chapter/10" }),
                null,
                null,
            )
            val viewModel = createViewModel(repository).also { createdViewModel = it }
            seedState(viewModel, storedManga, chapters)
            firstInvocation = mainExecutor.submit { viewModel.setMangaReadingMode(ReadingMode.WEBTOON) }
            assertTrue(staleReadStarted.await(2, TimeUnit.SECONDS))
            firstInvocation.get(2, TimeUnit.SECONDS)
            latestInvocation = mainExecutor.submit {
                viewModel.setMangaReadingMode(ReadingMode.CONTINUOUS_VERTICAL)
                viewModel.setMangaReadingMode(ReadingMode.RIGHT_TO_LEFT)
            }
            latestInvocation.get(2, TimeUnit.SECONDS)
            assertEquals(
                ReaderViewModel.Event.ReloadViewerChapters,
                withTimeout(2_000.milliseconds) { viewModel.eventFlow.first() },
            )
            allowStaleRead.complete(Unit)
            assertTrue(staleReadReturned.await(2, TimeUnit.SECONDS))
            mainExecutor.submit {}.get(2, TimeUnit.SECONDS)
            assertEquals(ReadingMode.RIGHT_TO_LEFT.flagValue.toLong(), viewModel.state.value.manga?.readingMode)
            assertEquals(ReadingMode.RIGHT_TO_LEFT.flagValue.toLong(), storedManga.readingMode)
            assertSame(chapters, viewModel.state.value.viewerChapters)
        } finally {
            allowStaleRead.complete(Unit)
            firstInvocation?.cancel(true)
            latestInvocation?.cancel(true)
            createdViewModel?.viewModelScope?.cancel()
            mainExecutor.shutdownNow()
            mainExecutor.awaitTermination(2, TimeUnit.SECONDS)
            Dispatchers.resetMain()
            mainDispatcher.close()
        }
    }

    private fun seedState(viewModel: ReaderViewModel, manga: Manga, chapters: ViewerChapters) {
        // Seed an already opened chapter without invoking the Android/network chapter loader.
        val stateField = ReaderViewModel::class.java.getDeclaredField("mutableState").apply { isAccessible = true }

        @Suppress("UNCHECKED_CAST")
        val state = stateField.get(viewModel) as MutableStateFlow<ReaderViewModel.State>
        state.value = ReaderViewModel.State(manga = manga, viewerChapters = chapters)
    }

    private fun createViewModel(repository: MangaRepository) = ReaderViewModel(
        savedState = SavedStateHandle(),
        sourceManager = mockk(),
        downloadManager = mockk(),
        downloadProvider = mockk(),
        tempFileManager = mockk(),
        imageSaver = mockk(),
        readerPreferences = mockk(),
        basePreferences = mockk(),
        downloadPreferences = mockk { every { autoDownloadWhileReading().get() } returns 0 },
        trackPreferences = mockk(),
        trackChapter = mockk(),
        getManga = GetManga(repository),
        getChaptersByMangaId = mockk(),
        getNextChapters = mockk(),
        upsertHistory = mockk(),
        updateChapter = mockk(),
        setMangaViewerFlags = SetMangaViewerFlags(repository),
        getIncognitoState = mockk(),
        libraryPreferences = mockk(),
        syncPreferences = mockk(),
        uiPreferences = mockk(),
        getFlatMetadataById = mockk(),
        getMergedMangaById = mockk(),
        getMergedReferencesById = mockk(),
        getMergedChaptersByMangaId = mockk(),
    )
}
// KMK <--
