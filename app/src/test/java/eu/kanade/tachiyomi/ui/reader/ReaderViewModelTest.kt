// KMK -->
package eu.kanade.tachiyomi.ui.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import eu.kanade.domain.manga.interactor.SetMangaViewerFlags
import eu.kanade.domain.manga.model.readerOrientation
import eu.kanade.domain.manga.model.readingMode
import eu.kanade.tachiyomi.data.database.models.ChapterImpl
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
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
            ).apply { requestedPage = 7 }
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
    fun `latest reading mode survives an older write that finishes after cancellation`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val allowFirstWrite = CompletableDeferred<Unit>()
        val firstWriteStarted = CompletableDeferred<Unit>()
        var storedManga = Manga.create().copy(id = 1L)
        val repository = mockk<MangaRepository>()
        coEvery { repository.getMangaById(1L) } answers { storedManga }
        coEvery { repository.update(any()) } coAnswers {
            val update = firstArg<MangaUpdate>()
            if (!firstWriteStarted.isCompleted) {
                firstWriteStarted.complete(Unit)
                // A native database write may finish despite cancellation of its caller.
                withContext(NonCancellable) { allowFirstWrite.await() }
            }
            storedManga = storedManga.copy(viewerFlags = update.viewerFlags!!)
            true
        }
        val viewModel = createViewModel(repository)
        val chapters = createChapters()
        seedState(viewModel, storedManga, chapters)
        val events = mutableListOf<ReaderViewModel.Event>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.eventFlow.toList(events) }
        try {
            viewModel.setMangaReadingMode(ReadingMode.WEBTOON)
            runCurrent()
            assertTrue(firstWriteStarted.isCompleted)
            viewModel.setMangaReadingMode(ReadingMode.CONTINUOUS_VERTICAL)
            runCurrent()
            viewModel.setMangaReadingMode(ReadingMode.RIGHT_TO_LEFT)
            runCurrent()
            allowFirstWrite.complete(Unit)
            runCurrent()

            assertEquals(ReadingMode.RIGHT_TO_LEFT.flagValue.toLong(), viewModel.state.value.manga?.readingMode)
            assertEquals(ReadingMode.RIGHT_TO_LEFT.flagValue.toLong(), storedManga.readingMode)
            assertSame(chapters, viewModel.state.value.viewerChapters)
            assertEquals(listOf(ReaderViewModel.Event.ReloadViewerChapters), events)
        } finally {
            allowFirstWrite.complete(Unit)
            viewModel.viewModelScope.cancel()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @ParameterizedTest(name = "overlapping viewer flags with mode selected first = {0}")
    @ValueSource(booleans = [true, false])
    fun `overlapping mode and orientation changes retain both selections`(modeFirst: Boolean) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val allowFirstWrite = CompletableDeferred<Unit>()
        val firstWriteStarted = CompletableDeferred<Unit>()
        val allWritesFinished = CompletableDeferred<Unit>()
        var storedManga = Manga.create().copy(id = 1L)
        val writeCount = AtomicInteger()
        val repository = mockk<MangaRepository>()
        coEvery { repository.getMangaById(1L) } answers { storedManga }
        coEvery { repository.update(any()) } coAnswers {
            val update = firstArg<MangaUpdate>()
            if (!firstWriteStarted.isCompleted) {
                firstWriteStarted.complete(Unit)
                allowFirstWrite.await()
            }
            storedManga = storedManga.copy(viewerFlags = update.viewerFlags!!)
            if (writeCount.incrementAndGet() == 2) allWritesFinished.complete(Unit)
            true
        }
        val viewModel = createViewModel(repository)
        seedState(viewModel, storedManga, createChapters())
        val selectMode = { viewModel.setMangaReadingMode(ReadingMode.WEBTOON) }
        val selectOrientation = { viewModel.setMangaOrientationType(ReaderOrientation.LANDSCAPE) }
        try {
            if (modeFirst) selectMode() else selectOrientation()
            firstWriteStarted.await()
            if (modeFirst) selectOrientation() else selectMode()
            runCurrent()
            allowFirstWrite.complete(Unit)
            // Neither write may depend on the UI receiving the other change's events.
            allWritesFinished.await()
            val events = viewModel.eventFlow.take(3).toList()

            assertEquals(ReadingMode.WEBTOON.flagValue.toLong(), storedManga.readingMode)
            assertEquals(ReaderOrientation.LANDSCAPE.flagValue.toLong(), storedManga.readerOrientation)
            assertEquals(storedManga.viewerFlags, viewModel.state.value.manga?.viewerFlags)
            assertEquals(2, events.count { it == ReaderViewModel.Event.ReloadViewerChapters })
            assertTrue(events.contains(ReaderViewModel.Event.SetOrientation(ReaderOrientation.LANDSCAPE.flagValue)))
        } finally {
            allowFirstWrite.complete(Unit)
            viewModel.viewModelScope.cancel()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `successful mode change reloads when a follow-up manga lookup is unavailable`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var storedManga = Manga.create().copy(id = 1L)
        val repository = mockk<MangaRepository>()
        coEvery { repository.getMangaById(1L) } answers { storedManga }
        coEvery { repository.update(any()) } answers {
            storedManga = storedManga.copy(viewerFlags = firstArg<MangaUpdate>().viewerFlags!!)
            true
        }
        val getManga = mockk<GetManga> { coEvery { await(1L) } returns null }
        val viewModel = createViewModel(repository, getManga)
        seedState(viewModel, storedManga, createChapters())
        val events = mutableListOf<ReaderViewModel.Event>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.eventFlow.toList(events) }
        try {
            viewModel.setMangaReadingMode(ReadingMode.WEBTOON)
            runCurrent()

            assertEquals(ReadingMode.WEBTOON.flagValue.toLong(), storedManga.readingMode)
            assertEquals(storedManga.readingMode, viewModel.state.value.manga?.readingMode)
            assertEquals(listOf(ReaderViewModel.Event.ReloadViewerChapters), events)
        } finally {
            viewModel.viewModelScope.cancel()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @ParameterizedTest(name = "successful write preserves reader metadata with orientation = {0}")
    @ValueSource(booleans = [true, false])
    fun `successful flag write preserves newer reader metadata`(orientationChange: Boolean) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val allowWrite = CompletableDeferred<Unit>()
        val writeStarted = CompletableDeferred<Unit>()
        var storedManga = Manga.create().copy(id = 1L, ogTitle = "Original title", notes = "Original notes")
        val repository = mockk<MangaRepository>()
        coEvery { repository.getMangaById(1L) } answers { storedManga }
        coEvery { repository.update(any()) } coAnswers {
            val update = firstArg<MangaUpdate>()
            writeStarted.complete(Unit)
            allowWrite.await()
            storedManga = storedManga.copy(viewerFlags = update.viewerFlags!!)
            true
        }
        val viewModel = createViewModel(repository)
        val chapters = createChapters()
        seedState(viewModel, storedManga, chapters)
        val events = mutableListOf<ReaderViewModel.Event>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.eventFlow.toList(events) }
        try {
            if (orientationChange) {
                viewModel.setMangaOrientationType(ReaderOrientation.LANDSCAPE)
            } else {
                viewModel.setMangaReadingMode(ReadingMode.WEBTOON)
            }
            runCurrent()
            assertTrue(writeStarted.isCompleted)

            // Model a newer reader snapshot while the flag write is suspended.
            storedManga = storedManga.copy(ogTitle = "Updated title", notes = "Updated notes")
            seedState(viewModel, storedManga, chapters)
            viewModel.showMenus(true)
            val stateBeforeCompletion = viewModel.state.value
            val expectedFlags = if (orientationChange) {
                ReaderOrientation.LANDSCAPE.flagValue.toLong()
            } else {
                ReadingMode.WEBTOON.flagValue.toLong()
            }
            val expectedManga = storedManga.copy(viewerFlags = expectedFlags)
            allowWrite.complete(Unit)
            runCurrent()

            assertEquals(expectedManga, storedManga)
            assertEquals(stateBeforeCompletion.copy(manga = expectedManga), viewModel.state.value)
            assertSame(chapters, viewModel.state.value.viewerChapters)
            val expectedEvents = buildList {
                if (orientationChange) add(ReaderViewModel.Event.SetOrientation(ReaderOrientation.LANDSCAPE.flagValue))
                add(ReaderViewModel.Event.ReloadViewerChapters)
            }
            assertEquals(expectedEvents, events)
            coVerify(exactly = 1) { repository.update(any()) }
        } finally {
            allowWrite.complete(Unit)
            viewModel.viewModelScope.cancel()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @ParameterizedTest(name = "failed write leaves unchanged flags with orientation = {0}")
    @ValueSource(booleans = [true, false])
    fun `failed flag write does not publish a successful change`(orientationChange: Boolean) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val storedManga = Manga.create().copy(id = 1L)
        val repository = mockk<MangaRepository>()
        coEvery { repository.getMangaById(1L) } returns storedManga
        coEvery { repository.update(any()) } returns false
        val viewModel = createViewModel(repository)
        seedState(viewModel, storedManga, createChapters())
        val events = mutableListOf<ReaderViewModel.Event>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.eventFlow.toList(events) }
        try {
            if (orientationChange) {
                viewModel.setMangaOrientationType(ReaderOrientation.LANDSCAPE)
            } else {
                viewModel.setMangaReadingMode(ReadingMode.WEBTOON)
            }
            runCurrent()

            assertEquals(storedManga, viewModel.state.value.manga)
            assertTrue(events.isEmpty())
            coVerify(exactly = 1) { repository.update(any()) }
        } finally {
            viewModel.viewModelScope.cancel()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `mode change keeps the live page advanced while its database write is pending`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val allowWrite = CompletableDeferred<Unit>()
        val writeStarted = CompletableDeferred<Unit>()
        var storedManga = Manga.create().copy(id = 1L)
        val repository = mockk<MangaRepository>()
        coEvery { repository.getMangaById(1L) } answers { storedManga }
        coEvery { repository.update(any()) } coAnswers {
            val update = firstArg<MangaUpdate>()
            writeStarted.complete(Unit)
            allowWrite.await()
            storedManga = storedManga.copy(viewerFlags = update.viewerFlags!!)
            true
        }
        val viewModel = createViewModel(repository)
        val chapters = createChapters()
        seedState(viewModel, storedManga, chapters)
        try {
            viewModel.setMangaReadingMode(ReadingMode.WEBTOON)
            runCurrent()
            assertTrue(writeStarted.isCompleted)
            // Incognito progress advances the live page without changing saved progress.
            chapters.currChapter.requestedPage = 11
            allowWrite.complete(Unit)
            runCurrent()

            assertEquals(ReaderViewModel.Event.ReloadViewerChapters, viewModel.eventFlow.first())
            assertEquals(11, chapters.currChapter.requestedPage)
            assertEquals(7, chapters.currChapter.chapter.last_page_read)
            assertSame(chapters, viewModel.state.value.viewerChapters)
        } finally {
            allowWrite.complete(Unit)
            viewModel.viewModelScope.cancel()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    internal fun createChapters() = ViewerChapters(
        ReaderChapter(
            ChapterImpl().apply {
                id = 10L
                manga_id = 1L
                url = "/chapter/10"
                last_page_read = 7
            },
        ).apply { requestedPage = 7 },
        null,
        null,
    )

    internal fun seedState(viewModel: ReaderViewModel, manga: Manga, chapters: ViewerChapters) {
        // Seed an already opened chapter without invoking the Android/network chapter loader.
        val stateField = ReaderViewModel::class.java.getDeclaredField("mutableState").apply { isAccessible = true }

        @Suppress("UNCHECKED_CAST")
        val state = stateField.get(viewModel) as MutableStateFlow<ReaderViewModel.State>
        state.value = ReaderViewModel.State(manga = manga, viewerChapters = chapters)
    }

    internal fun createViewModel(repository: MangaRepository, getManga: GetManga = GetManga(repository)) = ReaderViewModel(
        savedState = SavedStateHandle(),
        sourceManager = mockk(),
        downloadManager = mockk(),
        downloadProvider = mockk(),
        tempFileManager = mockk(),
        imageSaver = mockk(),
        readerPreferences = mockk { every { defaultOrientationType().get() } returns ReaderOrientation.FREE.flagValue },
        basePreferences = mockk(),
        downloadPreferences = mockk { every { autoDownloadWhileReading().get() } returns 0 },
        trackPreferences = mockk(),
        trackChapter = mockk(),
        getManga = getManga,
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
