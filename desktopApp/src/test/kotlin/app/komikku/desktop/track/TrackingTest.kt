package app.komikku.desktop.track

import android.app.Application
import app.komikku.desktop.backup.DesktopBackupService
import app.komikku.desktop.di.DesktopInjekt
import app.komikku.desktop.reader.FakeHttpSource
import app.komikku.desktop.reader.TestLibrary
import app.komikku.desktop.ui.browse.FixedSourceManager
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.interactor.DeleteTrack
import tachiyomi.domain.track.interactor.GetTracks
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
    private lateinit var manga: Manga

    @BeforeAll
    fun boot() {
        dir = File(System.getProperty("komikku.dataDir"), "tracking").apply { mkdirs() }
        app = DesktopInjekt.init(dir)
        Injekt.addSingletonFactory<SourceManager> { FixedSourceManager(listOf(FakeHttpSource("http://localhost:1"))) }
        Injekt.addSingletonFactory { TrackerManager(extraTrackers = listOf(tracker)) }
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
        DesktopTrackerLogin.method(Injekt.get<TrackerManager>().mdList) shouldBe TrackerLoginMethod.Unsupported
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
}
