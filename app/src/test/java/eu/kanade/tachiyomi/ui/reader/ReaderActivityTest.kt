// KMK -->
package eu.kanade.tachiyomi.ui.reader

import android.net.Uri
import android.view.View
import android.widget.FrameLayout
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.WebtoonLayoutManager
import eu.kanade.domain.manga.model.readingMode
import eu.kanade.tachiyomi.databinding.ReaderActivityBinding
import eu.kanade.tachiyomi.ui.reader.loader.ChapterLoader
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import eu.kanade.tachiyomi.ui.reader.viewer.Viewer
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonAdapter
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonConfig
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonRecyclerView
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonViewer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.unmockkObject
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.source.service.SourceManager

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderActivityTest {
    private val fixture = ReaderViewModelTest()

    @ParameterizedTest(name = "webtoon binding selects the live page with replacement = {0}")
    @ValueSource(booleans = [true, false])
    fun `webtoon chapter binding scrolls to the live page instead of saved progress`(replacement: Boolean) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val initialMode = if (replacement) ReadingMode.RIGHT_TO_LEFT else ReadingMode.WEBTOON
        var storedManga = Manga.create().copy(id = 1L, viewerFlags = initialMode.flagValue.toLong())
        val repository = mockk<MangaRepository>()
        coEvery { repository.getMangaById(1L) } answers { storedManga }
        coEvery { repository.update(any()) } answers {
            storedManga = storedManga.copy(viewerFlags = firstArg<MangaUpdate>().viewerFlags!!)
            true
        }
        val viewModel = fixture.createViewModel(repository)
        val chapters = fixture.createChapters().apply {
            currChapter.chapter.name = "Chapter 10"
            currChapter.requestedPage = 11
        }
        val pages = List(15) { index -> ReaderPage(index).apply { chapter = chapters.currChapter } }
        chapters.currChapter.state = ReaderChapter.State.Loaded(pages)
        fixture.seedState(viewModel, storedManga, chapters)
        val sourceManager = ReaderViewModel::class.java.getDeclaredField("sourceManager")
            .apply { isAccessible = true }.get(viewModel) as SourceManager
        every { sourceManager.getOrStub(any()) } returns mockk()
        val viewers = mutableListOf<Viewer>()
        mockkObject(ReadingMode.Companion)
        val activity = createActivity(viewModel, viewers)
        val layoutManager = mockk<WebtoonLayoutManager>(relaxed = true)
        val (webtoon, adapter) = createWebtoonViewer(layoutManager)
        every { ReadingMode.toViewer(ReadingMode.WEBTOON.flagValue, activity, any()) } answers {
            webtoon.also(viewers::add)
        }
        every { activity["setChapters"](any<ViewerChapters>()) } answers { callOriginal() }
        every { activity["updateDiscordRPC"](any<Boolean>()) } returns Unit
        try {
            observeReader(activity)
            runCurrent()
            if (replacement) {
                viewModel.setMangaReadingMode(ReadingMode.WEBTOON)
                runCurrent()
            }

            assertEquals(if (replacement) 2 else 1, viewers.size)
            assertSame(webtoon, viewModel.state.value.viewer)
            verify(exactly = 1) { webtoon.setChapters(chapters) }
            verify(exactly = 1) { adapter.setChapters(chapters, false) }
            verify(exactly = 1) { webtoon.moveToPage(pages[11]) }
            // The real adapter inserts a previous-chapter transition before the chapter's pages.
            verify(exactly = 1) { layoutManager.scrollToPositionWithOffset(12, 0) }
            assertSame(pages[11], adapter.items[12])
            assertEquals(7, chapters.currChapter.chapter.last_page_read)
        } finally {
            activity.lifecycleScope.cancel()
            viewModel.viewModelScope.cancel()
            runCurrent()
            unmockkObject(ReadingMode.Companion)
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `initial reader state binds chapters to the new viewer once`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = fixture.createViewModel(mockk())
        val chapters = fixture.createChapters().apply { currChapter.requestedPage = 11 }
        fixture.seedState(viewModel, Manga.create().copy(id = 1L), chapters)
        val previousViewer = mockk<Viewer>(relaxed = true)
        viewModel.onViewerLoaded(previousViewer)
        val viewers = mutableListOf<Viewer>()
        mockkObject(ReadingMode.Companion)
        val activity = createActivity(viewModel, viewers)
        try {
            observeReader(activity)
            runCurrent()

            assertSame(viewers.single(), viewModel.state.value.viewer)
            verify(exactly = 1) { previousViewer.destroy() }
            verify(exactly = 1) { viewers.single().setChapters(chapters) }
            verifyOrder {
                activity["showLoadingIndicator"]()
                viewers.single().setChapters(chapters)
                activity.startPostponedEnterTransition()
            }
            assertEquals(11, chapters.currChapter.requestedPage)
        } finally {
            activity.lifecycleScope.cancel()
            viewModel.viewModelScope.cancel()
            runCurrent()
            unmockkObject(ReadingMode.Companion)
            Dispatchers.resetMain()
        }
    }

    @ParameterizedTest(name = "setting change binds replacement chapters once with orientation = {0}")
    @ValueSource(booleans = [true, false])
    fun `successful setting change binds chapters to the replacement viewer once`(orientationChange: Boolean) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var storedManga = Manga.create().copy(id = 1L)
        val repository = mockk<MangaRepository>()
        coEvery { repository.getMangaById(1L) } answers { storedManga }
        coEvery { repository.update(any()) } answers {
            storedManga = storedManga.copy(viewerFlags = firstArg<MangaUpdate>().viewerFlags!!)
            true
        }
        val viewModel = fixture.createViewModel(repository)
        val chapters = fixture.createChapters().apply { currChapter.requestedPage = 11 }
        fixture.seedState(viewModel, storedManga, chapters)
        val viewers = mutableListOf<Viewer>()
        mockkObject(ReadingMode.Companion)
        val activity = createActivity(viewModel, viewers)
        try {
            observeReader(activity)
            runCurrent()
            if (orientationChange) {
                viewModel.setMangaOrientationType(ReaderOrientation.LANDSCAPE)
            } else {
                viewModel.setMangaReadingMode(ReadingMode.WEBTOON)
            }
            runCurrent()

            assertEquals(2, viewers.size)
            verify(exactly = 1) { viewers.last().setChapters(chapters) }
            assertEquals(11, chapters.currChapter.requestedPage)

            viewModel.showMenus(true)
            if (orientationChange) {
                viewModel.setMangaOrientationType(ReaderOrientation.LANDSCAPE)
            } else {
                viewModel.setMangaReadingMode(ReadingMode.WEBTOON)
            }
            runCurrent()

            assertEquals(2, viewers.size)
            verify(exactly = 1) { viewers.last().setChapters(chapters) }
        } finally {
            activity.lifecycleScope.cancel()
            viewModel.viewModelScope.cancel()
            runCurrent()
            unmockkObject(ReadingMode.Companion)
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `new viewer receives chapters despite cancelled reload delivery and a failed later write`() = runTest {
        val mainDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(mainDispatcher)
        val allowFirstWrite = CompletableDeferred<Unit>()
        var writes = 0
        var storedManga = Manga.create().copy(id = 1L)
        val repository = mockk<MangaRepository>()
        coEvery { repository.getMangaById(1L) } answers { storedManga }
        coEvery { repository.update(any()) } coAnswers {
            val update = firstArg<MangaUpdate>()
            writes++
            if (writes == 1) {
                allowFirstWrite.await()
                storedManga = storedManga.copy(viewerFlags = update.viewerFlags!!)
                true
            } else {
                false
            }
        }
        val viewModel = fixture.createViewModel(repository)
        val chapters = fixture.createChapters().apply { currChapter.requestedPage = 11 }
        fixture.seedState(viewModel, storedManga, chapters)
        val viewers = mutableListOf<Viewer>()

        @Suppress("UNCHECKED_CAST")
        val channel = ReaderViewModel::class.java.getDeclaredField("eventChannel")
            .apply { isAccessible = true }.get(viewModel) as Channel<ReaderViewModel.Event>
        mockkObject(ReadingMode.Companion)
        val activity = createActivity(viewModel, viewers)
        try {
            observeReader(activity)
            runCurrent()
            viewModel.setMangaReadingMode(ReadingMode.WEBTOON)
            runCurrent()
            assertEquals(1, writes)

            allowFirstWrite.complete(Unit)
            backgroundScope.launch(mainDispatcher) { viewModel.setMangaReadingMode(ReadingMode.RIGHT_TO_LEFT) }
            val copyEvent = ReaderViewModel.Event.CopyImage(mockk())
            assertTrue(channel.trySend(copyEvent).isSuccess, "The event receiver is active and waiting")
            val pendingReload = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
                channel.send(ReaderViewModel.Event.ReloadViewerChapters)
            }
            assertTrue(pendingReload.isActive, "Reload delivery must be suspended behind the other event")
            pendingReload.cancel()
            runCurrent()

            assertTrue(pendingReload.isCancelled)
            assertEquals(2, writes)
            assertEquals(ReadingMode.WEBTOON.flagValue.toLong(), storedManga.readingMode)
            assertEquals(storedManga.readingMode, viewModel.state.value.manga?.readingMode)
            verify(exactly = 1) { activity["onCopyImageResult"](copyEvent.uri) }
            assertEquals(2, viewers.size)
            assertSame(viewers.last(), viewModel.state.value.viewer)
            verify(exactly = 1) { viewers.first().setChapters(chapters) }
            verify(exactly = 1) { viewers.last().setChapters(chapters) }
            assertEquals(11, chapters.currChapter.requestedPage)
        } finally {
            allowFirstWrite.complete(Unit)
            activity.lifecycleScope.cancel()
            viewModel.viewModelScope.cancel()
            runCurrent()
            unmockkObject(ReadingMode.Companion)
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `late chapters and subsequent changes are bound once to the current viewer`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = fixture.createViewModel(mockk())
        updateState(viewModel) { it.copy(manga = Manga.create().copy(id = 1L)) }
        val viewers = mutableListOf<Viewer>()
        mockkObject(ReadingMode.Companion)
        val activity = createActivity(viewModel, viewers)
        try {
            observeReader(activity)
            runCurrent()
            assertEquals(1, viewers.size)
            verify(exactly = 0) { viewers.single().setChapters(any()) }

            val chapters = fixture.createChapters()
            updateState(viewModel) { it.copy(viewerChapters = chapters) }
            runCurrent()
            verify(exactly = 1) { viewers.single().setChapters(chapters) }

            val nextChapters = fixture.createChapters().apply { currChapter.chapter.id = 20L }
            updateState(viewModel) { it.copy(viewerChapters = nextChapters) }
            runCurrent()
            assertEquals(1, viewers.size)
            verify(exactly = 1) { viewers.single().setChapters(nextChapters) }

            val latestChapters = fixture.createChapters().apply {
                currChapter.chapter.id = 30L
                currChapter.chapter.last_page_read = 11
            }
            updateState(viewModel) {
                it.copy(
                    manga = it.manga?.copy(viewerFlags = ReadingMode.WEBTOON.flagValue.toLong()),
                    viewerChapters = latestChapters,
                )
            }
            runCurrent()
            assertEquals(2, viewers.size)
            verify(exactly = 0) { viewers.first().setChapters(latestChapters) }
            verify(exactly = 1) { viewers.last().setChapters(latestChapters) }
            assertEquals(11, latestChapters.currChapter.requestedPage)
        } finally {
            activity.lifecycleScope.cancel()
            viewModel.viewModelScope.cancel()
            runCurrent()
            unmockkObject(ReadingMode.Companion)
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `preloading pages in the same chapter object still reloads the current viewer`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = fixture.createViewModel(mockk())
        val nextChapter = fixture.createChapters().currChapter.apply { chapter.id = 20L }
        val chapters = fixture.createChapters().copy(nextChapter = nextChapter)
        fixture.seedState(viewModel, Manga.create().copy(id = 1L), chapters)
        val pages = listOf(mockk<ReaderPage>())
        val loader = mockk<ChapterLoader>()
        coEvery { loader.loadChapter(nextChapter, null) } coAnswers {
            nextChapter.state = ReaderChapter.State.Loaded(pages)
        }
        ReaderViewModel::class.java.getDeclaredField("loader").apply { isAccessible = true }.set(viewModel, loader)
        val viewers = mutableListOf<Viewer>()
        mockkObject(ReadingMode.Companion)
        val activity = createActivity(viewModel, viewers)
        try {
            observeReader(activity)
            runCurrent()
            verify(exactly = 1) { viewers.single().setChapters(chapters) }
            val stateBeforePreload = viewModel.state.value

            viewModel.preload(nextChapter)
            runCurrent()

            assertSame(stateBeforePreload, viewModel.state.value)
            assertSame(pages, nextChapter.pages)
            verify(exactly = 2) { viewers.single().setChapters(chapters) }
            coVerify(exactly = 1) { loader.loadChapter(nextChapter, null) }
        } finally {
            activity.lifecycleScope.cancel()
            viewModel.viewModelScope.cancel()
            runCurrent()
            unmockkObject(ReadingMode.Companion)
            Dispatchers.resetMain()
        }
    }

    private fun createWebtoonViewer(layoutManager: WebtoonLayoutManager): Pair<WebtoonViewer, WebtoonAdapter> {
        // Skip Android view construction while running the production binding and page-selection methods.
        val viewer = mockk<WebtoonViewer>(relaxed = true)
        val adapter = mockk<WebtoonAdapter>(relaxed = true)
        WebtoonAdapter::class.java.getDeclaredField("items").apply { isAccessible = true }
            .set(adapter, emptyList<Any>())
        every { adapter.items } answers { callOriginal() }
        every { adapter.setChapters(any(), any()) } answers { callOriginal() }
        every { adapter["updateItems"](any<List<Any>>()) } answers { callOriginal() }
        val recycler = mockk<WebtoonRecyclerView>(relaxed = true)
        var visibility = View.GONE
        every { recycler.visibility } answers { visibility }
        every { recycler.visibility = any() } answers { visibility = firstArg() }
        val config = mockk<WebtoonConfig> {
            every { alwaysShowChapterTransition } returns false
        }
        for ((name, value) in listOf(
            "adapter" to adapter,
            "config" to config,
            "recycler" to recycler,
            "layoutManager" to layoutManager,
        )) {
            WebtoonViewer::class.java.getDeclaredField(name).apply { isAccessible = true }.set(viewer, value)
        }
        every { viewer.getView() } returns recycler
        every { viewer.setChapters(any()) } answers { callOriginal() }
        every { viewer.moveToPage(any()) } answers { callOriginal() }
        // Scroll callbacks and drawing belong to the RecyclerView, outside this binding test.
        every { layoutManager.findLastEndVisibleItemPosition() } returns 0
        return viewer to adapter
    }

    private fun updateState(viewModel: ReaderViewModel, update: (ReaderViewModel.State) -> ReaderViewModel.State) {
        @Suppress("UNCHECKED_CAST")
        val state = ReaderViewModel::class.java.getDeclaredField("mutableState")
            .apply { isAccessible = true }.get(viewModel) as MutableStateFlow<ReaderViewModel.State>
        state.value = update(state.value)
    }

    private fun createActivity(viewModel: ReaderViewModel, viewers: MutableList<Viewer>): ReaderActivity {
        val activity = spyk(mockk<ReaderActivity>(), recordPrivateCalls = true)
        every { activity.lifecycle } returns LifecycleRegistry.createUnsafe(activity)
        val preferences = mockk<ReaderPreferences> {
            every { fullscreen().get() } returns false
            every { drawUnderCutout().get() } returns false
            every { useAutoWebtoon().get() } returns false
            every { showReadingMode().get() } returns false
        }
        every { viewModel.readerPreferences.defaultReadingMode().get() } returns ReadingMode.RIGHT_TO_LEFT.flagValue
        every { viewModel.readerPreferences.useAutoWebtoon().get() } returns false
        every { activity.viewModel } returns viewModel
        every { activity.intent } returns mockk { every { extras } returns null }
        every { activity.window } returns mockk { every { sharedElementEnterTransition } returns null }
        val binding = ReaderActivityBinding::class.java.declaredConstructors.single().apply { isAccessible = true }
            .newInstance(mockk<FrameLayout>(), null, null, mockk<FrameLayout>(relaxed = true), mockk<FrameLayout>(relaxed = true))
        ReaderActivity::class.java.getDeclaredField("binding").apply { isAccessible = true }.set(activity, binding)
        ReaderActivity::class.java.getDeclaredField("readerPreferences").apply { isAccessible = true }
            .set(activity, preferences)
        val sourceManager = mockk<SourceManager>()
        every { sourceManager.get(any()) } returns null
        ReaderActivity::class.java.getDeclaredField("sourceManager").apply { isAccessible = true }
            .set(activity, sourceManager)
        every { ReadingMode.toViewer(any(), activity, any()) } answers {
            mockk<Viewer>(relaxed = true).also(viewers::add)
        }
        // Run the real coordinator; replace Android rendering and other UI-only operations.
        every { activity["updateViewer"]() } answers { callOriginal() }
        every { activity["observeViewerState"]() } answers { callOriginal() }
        every { activity["observeReaderEvents"]() } answers { callOriginal() }
        every { activity["showLoadingIndicator"]() } returns Unit
        every { activity["setOrientation"](any<Int>()) } returns Unit
        every { activity["updateViewerInset"](any<Boolean>(), any<Boolean>()) } returns Unit
        every { activity.startPostponedEnterTransition() } returns Unit
        every { activity["onCopyImageResult"](any<Uri>()) } returns Unit
        every { activity["setChapters"](any<ViewerChapters>()) } answers {
            viewModel.state.value.viewer?.setChapters(firstArg())
            Unit
        }
        return activity
    }

    private fun observeReader(activity: ReaderActivity) {
        ReaderActivity::class.java.getDeclaredMethod("observeViewerState").apply { isAccessible = true }.invoke(activity)
        ReaderActivity::class.java.getDeclaredMethod("observeReaderEvents").apply { isAccessible = true }.invoke(activity)
    }
}
// KMK <--
