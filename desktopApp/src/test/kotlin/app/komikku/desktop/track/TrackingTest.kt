package app.komikku.desktop.track

import android.app.Application
import app.komikku.desktop.backup.DesktopBackupService
import app.komikku.desktop.di.DesktopInjekt
import app.komikku.desktop.reader.FakeHttpSource
import app.komikku.desktop.reader.TestLibrary
import app.komikku.desktop.ui.browse.FixedSourceManager
import app.komikku.desktop.ui.manga.TrackingScreenModel
import app.komikku.desktop.ui.more.TrackingSettingsScreenModel
import app.komikku.shared.reader.ReaderTrackerHook
import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.domain.track.service.DelayedTrackingUpdateJob
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.domain.track.store.DelayedTrackingStore
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.anilist.AnilistApi
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeListApi
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.interactor.DeleteTrack
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.i18n.kmk.KMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * 8.3: the borrowed trackers / track interactors on desktop with an in-test tracker: credentials login,
 * search + bind (`AddTracks`, local read progress pushed), the reader hook (`TrackChapter`, honouring
 * "update progress after reading"), failure → persisted delayed store → retry job, delete, and the
 * backup validator's missing-tracker report. Also: the real OAuth authorize URLs build on desktop.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class TrackingTest {

    private lateinit var app: Application
    private lateinit var dir: File
    private val tracker = FakeTracker()
    private val enhanced = FakeEnhancedTracker()
    private lateinit var manga: Manga

    @BeforeAll
    fun boot() {
        dir = File(System.getProperty("komikku.dataDir"), "tracking").apply { mkdirs() }
        app = DesktopInjekt.init(dir)
        Injekt.addSingletonFactory<SourceManager> { FixedSourceManager(listOf(FakeHttpSource("http://localhost:1"))) }
        Injekt.addSingletonFactory { TrackerManager(extraTrackers = listOf(tracker, enhanced)) }
        manga = TestLibrary.insertManga(FakeHttpSource.ID, "/manga/tracked", "Tracked manga")
        val chapters = TestLibrary.insertChapters(manga, "/chapter/1" to "Chapter 1", "/chapter/2" to "Chapter 2")
        // Chapter 1 (number 2.0 in TestLibrary's numbering: first inserted = highest) — mark the lowest read
        runBlocking {
            val lowest = chapters.minBy { it.chapterNumber }
            Injekt.get<ChapterRepository>().update(ChapterUpdate(id = lowest.id, read = true))
        }
    }

    private suspend fun track() = Injekt.get<GetTracks>().await(manga.id).singleOrNull { it.trackerId == FakeTracker.ID }

    private suspend fun awaitCondition(what: String, condition: suspend () -> Boolean) {
        try {
            withTimeout(15_000) { while (!condition()) delay(50) }
        } catch (e: Exception) {
            throw AssertionError("Timed out waiting for: $what", e)
        }
    }

    @Test
    @Order(1)
    fun `oauth authorize urls build on desktop`() {
        MyAnimeListApi.authUrl().toString().let {
            it shouldStartWith "https://myanimelist.net/v1/oauth2/authorize"
            it shouldContain "client_id="
            it shouldContain "code_challenge="
        }
        AnilistApi.authUrl().toString() shouldContain "response_type=token"
        DesktopTrackerLogin.method(Injekt.get<TrackerManager>().myAnimeList).let { (it as TrackerLoginMethod.OAuthPaste).host } shouldBe "myanimelist-auth"
        DesktopTrackerLogin.method(Injekt.get<TrackerManager>().kitsu) shouldBe TrackerLoginMethod.Credentials
        DesktopTrackerLogin.method(Injekt.get<TrackerManager>().komga) shouldBe TrackerLoginMethod.Enhanced
        // MDList (P4): MangaDex's OAuth with PKCE, the code goes to the enabled MangaDex source; none here, so no login
        val mdList = Injekt.get<TrackerManager>().mdList
        val mdMethod = DesktopTrackerLogin.method(mdList) as TrackerLoginMethod.OAuthPaste
        mdMethod.host shouldBe "mangadex-auth"
        mdMethod.authUrl().let {
            it shouldContain "client_id=tachiyomisy"
            it shouldContain "code_challenge_method=S256"
        }
        DesktopTrackerLogin.credentialFromRedirect(mdMethod, "tachiyomisy://mangadex-auth?code=abc") shouldBe "abc"
        runBlocking { DesktopTrackerLogin.loginWithRedirect(mdList, mdMethod, "tachiyomisy://mangadex-auth?code=abc") } shouldBe false
        mdList.isLoggedIn shouldBe false
        DesktopTrackerLogin.enhancedAvailable(Injekt.get<TrackerManager>().komga, Injekt.get()) shouldBe false
    }

    @Test
    @Order(2)
    fun `missing tracker reported before login, login with credentials`() = runBlocking<Unit> {
        tracker.isLoggedIn shouldBe false
        // A backup with a track of the (logged out) fake tracker: validator lists it
        Injekt.get<tachiyomi.domain.track.interactor.InsertTrack>().await(
            tachiyomi.domain.track.model.Track(
                id = 0, mangaId = manga.id, trackerId = FakeTracker.ID, remoteId = 99, libraryId = null, title = "x",
                lastChapterRead = 0.0, totalChapters = 0, status = 1, score = 0.0, remoteUrl = "", startDate = 0,
                finishDate = 0, private = false,
            ),
        )
        val service = Injekt.get<DesktopBackupService>()
        val file = service.create(File(dir, "tracking.tachibk"), BackupOptions())
        service.validate(file).missingTrackers shouldContain "Fake tracker"
        Injekt.get<DeleteTrack>().await(manga.id, FakeTracker.ID)

        runCatching { tracker.login("user", "wrong") }.isFailure shouldBe true
        tracker.login("user", FakeTracker.PASSWORD)
        tracker.isLoggedIn shouldBe true
        Injekt.get<TrackerManager>().loggedInTrackers().map { it.id } shouldContain FakeTracker.ID
    }

    @Test
    @Order(3)
    fun `search and bind stores the track and pushes local read progress`() = runBlocking<Unit> {
        val result = tracker.search("Tracked manga").first()
        tracker.register(result, manga.id)
        val track = track()!!
        track.remoteId shouldBe 1L
        track.title shouldBe "Tracked manga (manga)"
        // One chapter (number 1.0) was read locally before binding: AddTracks pushes it
        track.lastChapterRead shouldBe 1.0
        tracker.remote[1L]!!.lastChapterRead shouldBe 1.0
    }

    @Test
    @Order(4)
    fun `reader hook updates the tracker, honours the preference`() = runBlocking<Unit> {
        val hook = Injekt.get<ReaderTrackerHook>()
        hook.onChapterRead(manga, 2.0)
        awaitCondition("remote at chapter 2") { tracker.remote[1L]?.lastChapterRead == 2.0 }
        awaitCondition("local track at chapter 2") { track()?.lastChapterRead == 2.0 }

        Injekt.get<TrackPreferences>().autoUpdateTrack().set(false)
        hook.onChapterRead(manga, 3.0)
        delay(500)
        tracker.remote[1L]!!.lastChapterRead shouldBe 2.0
        Injekt.get<TrackPreferences>().autoUpdateTrack().set(true)
    }

    @Test
    @Order(5)
    fun `failed update goes to the delayed store and is retried`() = runBlocking<Unit> {
        val store = Injekt.get<DelayedTrackingStore>()
        DelayedTrackingUpdateJob.initialDelay = kotlin.time.Duration.INFINITE
        tracker.failUpdates = true
        Injekt.get<ReaderTrackerHook>().onChapterRead(manga, 4.0)
        awaitCondition("delayed item stored") { store.getItems().isNotEmpty() }
        store.getItems().single().lastChapterRead shouldBe 4f

        tracker.failUpdates = false
        DelayedTrackingUpdateJob.runOnce(app) shouldBe true
        store.getItems().isEmpty() shouldBe true
        tracker.remote[1L]!!.lastChapterRead shouldBe 4.0
        track()!!.lastChapterRead shouldBe 4.0
    }

    @Test
    @Order(6)
    fun `remote status and score, then delete`() = runBlocking<Unit> {
        val dbTrack = track()!!.toDbTrack()
        tracker.setRemoteStatus(dbTrack, FakeTracker.COMPLETED)
        tracker.setRemoteScore(dbTrack, "8")
        track()!!.status shouldBe FakeTracker.COMPLETED
        track()!!.score shouldBe 8.0
        tracker.remote[1L]!!.score shouldBe 8.0

        Injekt.get<DeleteTrack>().await(manga.id, FakeTracker.ID)
        track() shouldBe null
    }

    @Test
    @Order(7)
    fun `add tracking with an enhanced tracker binds its match without searching`() = runBlocking<Unit> {
        enhanced.loginNoop()
        val model = TrackingScreenModel(manga.id)
        model.startSearch(enhanced, manga.title)
        awaitCondition("enhanced track bound") {
            Injekt.get<GetTracks>().await(manga.id).any { it.trackerId == enhanced.id && it.remoteId == 77L }
        }
        model.state.value.search shouldBe null
        enhanced.searches shouldBe emptyList()
    }

    @Test
    @Order(8)
    fun `a slow search of another tracker does not land in the open search`() = runBlocking<Unit> {
        val other = FakeTracker(id = 903L, name = "Other tracker")
        val gate = CompletableDeferred<Unit>()
        tracker.beforeSearch = { if (it == "slow") gate.await() }
        try {
            val model = TrackingScreenModel(manga.id)
            model.startSearch(tracker, "slow")
            awaitCondition("slow search started") { "slow" in tracker.searches }
            model.closeSearch()
            model.startSearch(other, "fast")
            awaitCondition("fast search done") { model.state.value.search?.results != null }
            gate.complete(Unit)
            delay(300)
            val search = model.state.value.search!!
            search.tracker.id shouldBe other.id
            search.results!!.map { it.title } shouldBe listOf("fast (manga)", "fast (novel)")
        } finally {
            gate.complete(Unit)
            tracker.beforeSearch = {}
        }
    }

    @Test
    @Order(9)
    fun `a login that ends logged out shows the login failed message`() = runBlocking<Unit> {
        val silent = object : FakeTracker(id = 904L, name = "Silent tracker") {
            override suspend fun login(username: String, password: String) {}
        }
        val model = TrackingSettingsScreenModel()
        model.loginWithCredentials(silent, "user", "pass")
        awaitCondition("login finished") { !model.state.value.busy && model.state.value.error != null }
        model.state.value.error shouldBe app.stringResource(KMR.strings.login_failed)
    }
}
