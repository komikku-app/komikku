// KMK -->
package eu.kanade.tachiyomi.ui.reader

import android.widget.FrameLayout
import androidx.lifecycle.viewModelScope
import eu.kanade.domain.manga.model.readingMode
import eu.kanade.tachiyomi.databinding.ReaderActivityBinding
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import eu.kanade.tachiyomi.ui.reader.viewer.Viewer
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.unmockkObject
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
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
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.source.service.SourceManager

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderActivityTest {
    private val fixture = ReaderViewModelTest()

    @Test
    fun `new viewer immediately receives the current chapters`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = fixture.createViewModel(mockk())
        val chapters = fixture.createChapters().apply { currChapter.requestedPage = 11 }
        fixture.seedState(viewModel, Manga.create().copy(id = 1L), chapters)
        val viewers = mutableListOf<Viewer>()
        mockkObject(ReadingMode.Companion)
        try {
            val activity = createActivity(viewModel, viewers)
            updateViewer(activity)

            assertSame(viewers.single(), viewModel.state.value.viewer)
            verify(exactly = 1) { viewers.single().setChapters(chapters) }
            verifyOrder {
                activity["showLoadingIndicator"]()
                viewers.single().setChapters(chapters)
                activity.startPostponedEnterTransition()
            }
            assertEquals(11, chapters.currChapter.requestedPage)
        } finally {
            viewModel.viewModelScope.cancel()
            runCurrent()
            unmockkObject(ReadingMode.Companion)
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `new viewer receives chapters when another event delays a cancelled reload`() = runTest {
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
        val events = mutableListOf<ReaderViewModel.Event>()

        @Suppress("UNCHECKED_CAST")
        val channel = ReaderViewModel::class.java.getDeclaredField("eventChannel")
            .apply { isAccessible = true }.get(viewModel) as Channel<ReaderViewModel.Event>
        mockkObject(ReadingMode.Companion)
        try {
            val activity = createActivity(viewModel, viewers)
            // Keep the Activity's state and event observers independent, with synchronous handlers.
            backgroundScope.launch(mainDispatcher) {
                viewModel.state.map { it.manga }.distinctUntilChanged().filterNotNull().collect {
                    updateViewer(activity)
                }
            }
            backgroundScope.launch(mainDispatcher) {
                viewModel.state.map { it.viewerChapters }.distinctUntilChanged().filterNotNull().collect {
                    viewModel.state.value.viewer?.setChapters(it)
                }
            }
            backgroundScope.launch(mainDispatcher) {
                viewModel.eventFlow.collect {
                    events.add(it)
                    if (it == ReaderViewModel.Event.ReloadViewerChapters) {
                        viewModel.state.value.viewer?.setChapters(chapters)
                    }
                }
            }
            runCurrent()
            viewModel.setMangaReadingMode(ReadingMode.WEBTOON)
            runCurrent()
            assertEquals(1, writes)

            allowFirstWrite.complete(Unit)
            backgroundScope.launch(mainDispatcher) { viewModel.setMangaReadingMode(ReadingMode.RIGHT_TO_LEFT) }
            val copyEvent = ReaderViewModel.Event.CopyImage(mockk())
            assertTrue(channel.trySend(copyEvent).isSuccess, "The event receiver is active and waiting")
            runCurrent()

            assertEquals(2, writes)
            assertEquals(ReadingMode.WEBTOON.flagValue.toLong(), storedManga.readingMode)
            assertEquals(storedManga.readingMode, viewModel.state.value.manga?.readingMode)
            assertEquals(listOf(copyEvent), events, "The first mode's pending reload was cancelled")
            assertEquals(2, viewers.size)
            assertSame(viewers.last(), viewModel.state.value.viewer)
            verify(exactly = 1) { viewers.last().setChapters(chapters) }
            assertEquals(11, chapters.currChapter.requestedPage)
        } finally {
            allowFirstWrite.complete(Unit)
            viewModel.viewModelScope.cancel()
            runCurrent()
            unmockkObject(ReadingMode.Companion)
            Dispatchers.resetMain()
        }
    }

    private fun createActivity(viewModel: ReaderViewModel, viewers: MutableList<Viewer>): ReaderActivity {
        val activity = spyk(mockk<ReaderActivity>(), recordPrivateCalls = true)
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
        every { activity["showLoadingIndicator"]() } returns Unit
        every { activity["setOrientation"](any<Int>()) } returns Unit
        every { activity["updateViewerInset"](any<Boolean>(), any<Boolean>()) } returns Unit
        every { activity.startPostponedEnterTransition() } returns Unit
        every { activity["setChapters"](any<ViewerChapters>()) } answers {
            viewModel.state.value.viewer?.setChapters(firstArg())
            Unit
        }
        return activity
    }

    private fun updateViewer(activity: ReaderActivity) {
        ReaderActivity::class.java.getDeclaredMethod("updateViewer").apply { isAccessible = true }.invoke(activity)
    }
}
// KMK <--
