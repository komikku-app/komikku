package app.komikku.desktop.track

import android.app.Application
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.komikku.desktop.KomikkuApp
import app.komikku.desktop.di.DesktopInjekt
import app.komikku.desktop.reader.FakeHttpSource
import app.komikku.desktop.reader.TestLibrary
import app.komikku.desktop.ui.browse.FixedSourceManager
import app.komikku.desktop.ui.reader.ReaderLauncher
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.track.TrackerManager
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * 8.3 UI, headless, with the in-test tracker: More → Tracking → credentials login (wrong password shows
 * the error, right one logs in), then manga details → Tracking → Add tracking → search → bind → status,
 * chapter and start date edits → remove. MyAnimeList's paste dialog rejects another tracker's redirect.
 */
@OptIn(ExperimentalTestApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrackingUiTest {

    private lateinit var app: Application
    private val tracker = FakeTracker()
    private lateinit var manga: Manga

    @BeforeAll
    fun boot() {
        val dir = File(System.getProperty("komikku.dataDir"), "tracking-ui").apply { mkdirs() }
        app = DesktopInjekt.init(dir)
        Injekt.addSingletonFactory<SourceManager> { FixedSourceManager(listOf(FakeHttpSource("http://localhost:1"))) }
        Injekt.addSingletonFactory { TrackerManager(extraTrackers = listOf(tracker)) }
        manga = TestLibrary.insertManga(FakeHttpSource.ID, "/manga/track-ui", "Tracked UI manga")
        TestLibrary.insertChapters(manga, "/chapter/1" to "Chapter 1")
    }

    private fun ComposeUiTest.waitForTag(tag: String, present: Boolean = true, timeoutMillis: Long = 15_000) {
        waitUntil(timeoutMillis = timeoutMillis) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() == present }
    }

    private fun ComposeUiTest.waitForText(text: String, timeoutMillis: Long = 15_000) {
        waitUntil(timeoutMillis = timeoutMillis) { onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun track() = runBlocking { Injekt.get<GetTracks>().await(manga.id).singleOrNull() }

    @Test
    fun `login, bind and edit tracking`() = runDesktopComposeUiTest(width = 1280, height = 1000) {
        Injekt.get<ReaderLauncher>().close()
        setContent { KomikkuApp(context = app) }

        // More → Tracking → login
        onNodeWithText(MR.strings.label_more.localized()).performClick()
        waitForTag("more-tracking")
        onNodeWithTag("more-tracking").performClick()
        waitForTag("tracker-login-${FakeTracker.ID}")

        // OAuth tracker: a redirect of another tracker is rejected (browser opening is a no-op headless)
        val mal = Injekt.get<TrackerManager>().myAnimeList
        onNodeWithTag("tracker-login-${mal.id}").performClick()
        waitForTag("tracker-oauth")
        // REVIEW_A4.md 3.4: opening the login page again keeps this login's PKCE verifier, so a redirect pasted from the
        // first page still works
        val verifier = Injekt.get<TrackPreferences>().trackPkceCodeVerifier(mal)
        val pending = verifier.get()
        pending.isNotEmpty() shouldBe true
        onNodeWithTag("tracker-open-login").performClick()
        waitForIdle()
        verifier.get() shouldBe pending
        onNodeWithTag("tracker-redirect").performTextInput("komikku://anilist-auth#access_token=x")
        onNodeWithTag("tracker-login-confirm").performClick()
        waitForText("This is not a login result for")
        onNodeWithText(MR.strings.action_cancel.localized()).performClick()
        waitForTag("tracker-oauth", present = false)

        onNodeWithTag("tracker-login-${FakeTracker.ID}").performClick()
        waitForTag("tracker-credentials")
        onNodeWithTag("tracker-username").performTextInput("user")
        onNodeWithTag("tracker-password").performTextInput("wrong")
        onNodeWithTag("tracker-login-confirm").performClick()
        waitForText("Wrong password")
        onNodeWithTag("tracker-password").performTextReplacement(FakeTracker.PASSWORD)
        onNodeWithTag("tracker-login-confirm").performClick()
        waitForTag("tracker-logout-${FakeTracker.ID}")
        tracker.isLoggedIn shouldBe true

        // Library → details → Tracking
        onNodeWithText(MR.strings.label_library.localized()).performClick()
        waitForText("Tracked UI manga")
        onNodeWithText("Tracked UI manga").performClick()
        waitForTag("details-tracking")
        onNodeWithTag("details-tracking").performClick()
        waitForTag("tracker-add-${FakeTracker.ID}")
        onNodeWithTag("tracker-add-${FakeTracker.ID}").performClick()
        waitForTag("track-result-1")
        onNodeWithTag("track-result-1").performClick()
        waitForTag("tracker-status-${FakeTracker.ID}")
        track()!!.remoteId shouldBe 1L

        // Status
        onNodeWithTag("tracker-status-${FakeTracker.ID}").performClick()
        onNodeWithTag("tracker-status-${FakeTracker.ID}-${MR.strings.completed.localized()}").performClick()
        waitUntil(timeoutMillis = 10_000) { track()?.status == FakeTracker.COMPLETED }

        // Chapter
        onNodeWithTag("tracker-chapter-${FakeTracker.ID}").performClick()
        onNodeWithTag("tracker-chapter-${FakeTracker.ID}-input").performTextReplacement("7")
        onNodeWithTag("tracker-chapter-${FakeTracker.ID}-ok").performClick()
        waitUntil(timeoutMillis = 10_000) { track()?.lastChapterRead == 7.0 }
        tracker.remote[1L]!!.lastChapterRead shouldBe 7.0

        // Start date
        onNodeWithTag("tracker-start-${FakeTracker.ID}").performClick()
        onNodeWithTag("tracker-start-${FakeTracker.ID}-input").performTextReplacement("2024-03-05")
        onNodeWithTag("tracker-start-${FakeTracker.ID}-ok").performClick()
        waitUntil(timeoutMillis = 10_000) { (track()?.startDate ?: 0L) > 0L }

        // Remove
        onNodeWithTag("tracker-remove-${FakeTracker.ID}").performClick()
        waitUntil(timeoutMillis = 10_000) { track() == null }
        waitForTag("tracker-add-${FakeTracker.ID}")
        onNodeWithText(MR.strings.action_close.localized()).performClick()
        waitForTag("tracking-dialog", present = false)
        onNodeWithContentDescription(MR.strings.action_bar_up_description.localized()).assertExists()
        Unit
    }
}
