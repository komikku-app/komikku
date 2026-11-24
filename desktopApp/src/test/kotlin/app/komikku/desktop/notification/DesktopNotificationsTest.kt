package app.komikku.desktop.notification

import android.app.Application
import app.komikku.desktop.backup.DesktopBackupJobs
import app.komikku.desktop.di.DesktopInjekt
import app.komikku.shared.download.DownloadEvent
import app.komikku.shared.library.CoroutineLibraryUpdateRunner
import app.komikku.shared.library.LibraryUpdateProgress
import app.komikku.shared.library.LibraryUpdateRunner
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.release.model.Release
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.awt.GraphicsEnvironment
import java.io.File
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

/**
 * P17: Android's notifiers' rules on desktop ([DesktopNotifications]) with a recording [DesktopNotifier]: new chapters
 * and update errors after a library update (not after a cancelled one or a single entry's refresh), the tray status while
 * it runs, downloader errors / warnings, backup and restore results, an app update once per version, entry titles left
 * out with "Hide notification content" or while the app is locked; and the tray degrading to "no tray" headless.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DesktopNotificationsTest {

    private lateinit var app: Application
    private lateinit var security: SecurityPreferences

    private class RecordingNotifier : DesktopNotifier {
        val shown: MutableList<DesktopNotification> = Collections.synchronizedList(mutableListOf())
        val statuses: MutableList<String?> = Collections.synchronizedList(mutableListOf())
        override fun show(notification: DesktopNotification): Boolean = shown.add(notification)
        override fun status(text: String?) {
            statuses += text
        }
    }

    private val notifier = RecordingNotifier()
    private var locked = false
    private val library = MutableStateFlow<LibraryUpdateProgress?>(null)
    private val downloads = MutableSharedFlow<DownloadEvent>(extraBufferCapacity = 16)
    private val backups = MutableStateFlow<DesktopBackupJobs.Status>(DesktopBackupJobs.Status.Idle)
    private val updates = MutableStateFlow<Release?>(null)
    private var job: Job? = null

    @BeforeAll
    fun boot(@TempDir dir: File) {
        app = DesktopInjekt.init(dir)
        security = Injekt.get()
    }

    @BeforeEach
    fun start() {
        security.hideNotificationContent().delete()
        locked = false
        notifier.shown.clear()
        notifier.statuses.clear()
        job = DesktopNotifications(
            context = app,
            notifier = notifier,
            securityPreferences = security,
            isLocked = { locked },
            libraryUpdates = library,
            downloadEvents = downloads,
            backupStatus = backups,
            appUpdates = updates,
        ).start(CoroutineScope(Dispatchers.Default))
        // Found in a full run under load: a download event emitted (or a backup status set) before the collectors
        // subscribed was lost (a shared flow without replay) or read as the start-up status. Wait for them.
        runBlocking {
            withTimeout(5_000) {
                downloads.subscriptionCount.first { it > 0 }
                backups.subscriptionCount.first { it > 0 }
                library.subscriptionCount.first { it > 0 }
                updates.subscriptionCount.first { it > 0 }
            }
        }
    }

    @AfterEach
    fun stop() = runBlocking {
        job?.cancelAndJoin()
        library.value = null
        backups.value = DesktopBackupJobs.Status.Idle
        updates.value = null
    }

    private fun manga(id: Long, title: String) = Manga.create().copy(id = id, ogTitle = title, favorite = true)

    private fun awaitShown(count: Int) = runBlocking {
        withTimeout(5_000) { while (notifier.shown.size < count) yield() }
    }

    /** Lets the collectors run, for checks that nothing (more) was shown. */
    private fun settle() = Thread.sleep(200)

    private var runs = 0L

    private fun finished(
        target: LibraryUpdateRunner.Target = LibraryUpdateRunner.Target.All,
        cancelled: Boolean = false,
        updated: List<Manga> = emptyList(),
    ) =
        LibraryUpdateProgress(
            target = target,
            total = updated.size + 1,
            done = updated.size + 1,
            newChapters = updated.size,
            updated = updated.map { it to 1 },
            failed = listOf(manga(99, "Broken entry") to "HTTP 500"),
            finished = true,
            cancelled = cancelled,
            // Each finished progress stands for another run, as the runner numbers them
            runId = ++runs,
        )

    @Test
    fun `a finished library update notifies its new chapters and its errors`() {
        library.value = LibraryUpdateProgress(target = LibraryUpdateRunner.Target.All, total = 4, done = 1)
        runBlocking { withTimeout(5_000) { while (notifier.statuses.none { it != null }) yield() } }
        notifier.statuses.last()!! shouldContain "25"
        library.value = finished(updated = listOf(manga(1, "Alpha"), manga(2, "Beta")))
        awaitShown(2)
        val (chapters, errors) = notifier.shown
        chapters.channel shouldBe DesktopNotification.Channel.NewChapters
        chapters.title shouldBe MR.strings.notification_new_chapters.localized()
        chapters.text shouldContain "Alpha"
        chapters.text shouldContain "Beta"
        errors.channel shouldBe DesktopNotification.Channel.LibraryErrors
        errors.level shouldBe DesktopNotification.Level.Warning
        errors.text shouldNotContain "Broken entry"
        notifier.statuses.last() shouldBe null

        // One entry: its title is the text
        library.value = finished(updated = listOf(manga(3, "Gamma"))).copy(failed = emptyList())
        awaitShown(3)
        notifier.shown[2].text shouldBe "Gamma"
    }

    @Test
    fun `a cancelled update and a single entry's refresh notify nothing`() {
        library.value = finished(cancelled = true, updated = listOf(manga(1, "Alpha")))
        library.value = finished(target = LibraryUpdateRunner.Target.Single(1), updated = listOf(manga(1, "Alpha")))
        settle()
        notifier.shown.shouldBeEmpty()
    }

    @Test
    fun `entry titles are left out with hide notification content and while the app is locked`() {
        security.hideNotificationContent().set(true)
        library.value = finished(updated = listOf(manga(1, "Alpha")))
        awaitShown(2)
        notifier.shown[0].text shouldNotContain "Alpha"
        security.hideNotificationContent().set(false)

        locked = true
        library.value = finished(updated = listOf(manga(2, "Beta"), manga(3, "Gamma")))
        awaitShown(4)
        notifier.shown[2].text shouldNotContain "Beta"
        notifier.shown[2].text shouldNotContain "Gamma"
        downloads.tryEmit(DownloadEvent.Error("Timeout", chapter = "Chapter 3", mangaTitle = "Beta", mangaId = 2))
        awaitShown(5)
        notifier.shown[4].title shouldBe MR.strings.download_notifier_downloader_title.localized()
        notifier.shown[4].text shouldBe "Timeout"

        locked = false
        downloads.tryEmit(DownloadEvent.Error("Timeout", chapter = "Chapter 3", mangaTitle = "Beta", mangaId = 2))
        awaitShown(6)
        notifier.shown[5].title shouldBe "Beta: Chapter 3"
    }

    @Test
    fun `downloader errors and warnings, backup and restore results and an app update are notified`() {
        downloads.tryEmit(DownloadEvent.Paused)
        downloads.tryEmit(DownloadEvent.Completed)
        downloads.tryEmit(DownloadEvent.Warning("Lots of chapters from one source"))
        awaitShown(1)
        notifier.shown[0].level shouldBe DesktopNotification.Level.Warning
        notifier.shown[0].text shouldBe "Lots of chapters from one source"

        backups.value = DesktopBackupJobs.Status.Running(DesktopBackupJobs.Kind.Create)
        backups.value = DesktopBackupJobs.Status.Created(File("komikku_2026-11-24.tachibk"))
        awaitShown(2)
        notifier.shown[1].title shouldBe MR.strings.backup_created.localized()
        notifier.shown[1].text shouldBe "komikku_2026-11-24.tachibk"
        backups.value = DesktopBackupJobs.Status.Restored(timeMillis = 65_000, errors = listOf("x"), log = null)
        awaitShown(3)
        notifier.shown[2].channel shouldBe DesktopNotification.Channel.Restore
        notifier.shown[2].text shouldContain "01 min, 05 sec"
        backups.value = DesktopBackupJobs.Status.Failed(DesktopBackupJobs.Kind.Restore, "Not a backup")
        awaitShown(4)
        notifier.shown[3].title shouldBe MR.strings.restoring_backup_error.localized()
        notifier.shown[3].level shouldBe DesktopNotification.Level.Error

        val release = Release("v1.15.0", "notes", "https://example.org/r", "https://example.org/d")
        updates.value = release
        awaitShown(5)
        notifier.shown[4].text shouldBe "v1.15.0"
        // Dismissed and found again: once per version
        updates.value = null
        updates.value = release.copy(info = "other notes")
        settle()
        notifier.shown shouldHaveSize 5
    }

    @Test
    fun `a backup result is notified once, also when an automatic backup puts it back`() {
        val created = DesktopBackupJobs.Status.Created(File("komikku_2026-11-24.tachibk"))
        backups.value = DesktopBackupJobs.Status.Running(DesktopBackupJobs.Kind.Create)
        backups.value = created
        awaitShown(1)
        // `DesktopBackupJobs.createAutomatic()`: Running, then the previous status object back
        backups.value = DesktopBackupJobs.Status.Running(DesktopBackupJobs.Kind.Create)
        settle()
        backups.value = created
        settle()
        notifier.shown shouldHaveSize 1
        // A new result is news again
        backups.value = DesktopBackupJobs.Status.Failed(DesktopBackupJobs.Kind.Create, "Disk full")
        awaitShown(2)
    }

    @Test
    fun `a download error without a chapter names the entry only`() {
        downloads.tryEmit(DownloadEvent.Error("Timeout", chapter = null, mangaTitle = "Beta", mangaId = 2))
        awaitShown(1)
        notifier.shown[0].title shouldBe "Beta"
    }

    @Test
    fun `a second update that ends like the first one is notified too`() {
        val entry = LibraryManga(
            manga = manga(1, "Alpha"),
            categories = listOf(0),
            totalChapters = 5,
            readCount = 5,
            bookmarkCount = 0,
            bookmarkReadCount = 0,
            chapterFlags = 0,
            latestUpload = 0,
            chapterFetchedAt = 0,
            lastRead = 0,
        )
        val runner = CoroutineLibraryUpdateRunner(
            scope = CoroutineScope(Dispatchers.Default),
            getLibrary = { listOf(entry) },
            preferences = Injekt.get(),
            fetchWindowUpperBound = { Long.MAX_VALUE },
            updateManga = { 1 },
        )
        val collectorThread = Executors.newSingleThreadExecutor()
        val following = DesktopNotifications(
            context = app,
            notifier = notifier,
            securityPreferences = security,
            isLocked = { false },
            libraryUpdates = runner.progress,
            downloadEvents = MutableSharedFlow(),
            backupStatus = MutableStateFlow(DesktopBackupJobs.Status.Idle),
            appUpdates = MutableStateFlow(null),
        ).start(CoroutineScope(collectorThread.asCoroutineDispatcher()))
        try {
            runner.start(LibraryUpdateRunner.Target.Entries(setOf(1))) shouldBe true
            awaitShown(1)
            // The collector does not run while the second update starts and ends: it only sees its finished progress
            val gate = CountDownLatch(1)
            collectorThread.execute { gate.await() }
            // (the first run's job ends just after its finished progress)
            runBlocking { withTimeout(5_000) { while (!runner.start(LibraryUpdateRunner.Target.Entries(setOf(1)))) delay(10) } }
            runBlocking { withTimeout(5_000) { runner.progress.first { it?.finished == true } } }
            gate.countDown()
            awaitShown(2)
            notifier.shown[1].text shouldBe "Alpha"
        } finally {
            runBlocking { following.cancelAndJoin() }
            collectorThread.shutdownNow()
        }
    }

    @Test
    fun `without a usable system tray nothing is installed and notifications report not shown`() {
        val unsupported = DesktopTray(traySupported = { false })
        unsupported.install("Open", "Exit", onOpen = {}, onExit = {}) shouldBe false
        unsupported.isInstalled shouldBe false
        unsupported.show(DesktopNotification(DesktopNotification.Channel.AppUpdate, "t", "x")) shouldBe false
        unsupported.status("Updating")

        // Reported as supported in a headless JVM: creating the icon fails, which counts as no tray (logged, no crash)
        val headless = DesktopTray(traySupported = { true })
        val installed = headless.install("Open", "Exit", onOpen = {}, onExit = {})
        if (GraphicsEnvironment.isHeadless()) {
            installed shouldBe false
            headless.isInstalled shouldBe false
        }
        DesktopTray(traySupported = { error("no AWT") }).install("Open", "Exit", onOpen = {}, onExit = {}) shouldBe false
        headless.remove()
    }
}
