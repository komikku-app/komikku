package app.komikku.desktop.scheduler

import app.komikku.desktop.backup.DesktopBackupJobs
import app.komikku.desktop.backup.DesktopBackupService
import app.komikku.desktop.di.DesktopInjekt
import app.komikku.desktop.library.desktopLibraryUpdateRunner
import app.komikku.shared.library.LibraryUpdateRunner
import eu.kanade.tachiyomi.data.backup.restore.RestoreOptions
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import tachiyomi.core.common.storage.DesktopStorageFolderProvider
import tachiyomi.domain.backup.service.BackupPreferences
import tachiyomi.domain.library.service.LibraryPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * P3: the scheduled library update and automatic backup on Android's preferences, with the desktop update runner and
 * the borrowed `BackupCreator` (automatic mode) on a fresh data directory.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DesktopScheduledJobsTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var clock = 1_800_000_000_000L
    private val libraryPreferences by lazy { Injekt.get<LibraryPreferences>() }
    private val backupPreferences by lazy { Injekt.get<BackupPreferences>() }

    @BeforeAll
    fun boot(@TempDir dir: File) {
        DesktopInjekt.init(dir)
    }

    @AfterAll
    fun stop() = scope.cancel()

    private fun updateRunner() = desktopLibraryUpdateRunner(
        getLibraryManga = Injekt.get(),
        libraryPreferences = libraryPreferences,
        fetchInterval = Injekt.get(),
        sourceManager = Injekt.get(),
        updateMangaFromRemote = Injekt.get(),
        insertErrors = Injekt.get(),
        insertErrorMessages = Injekt.get(),
        scope = scope,
        now = { clock },
    )

    @Test
    fun `the library update runs on its interval, stores the last update and waits for a running one`() = runBlocking<Unit> {
        val runner = updateRunner()
        val scheduler = DesktopScheduler(scope, listOf(libraryUpdateTask(libraryPreferences, runner)), now = { clock })
        // "Never" (Android's default): nothing runs
        libraryPreferences.autoUpdateInterval().set(0)
        scheduler.checkNow() shouldBe emptyList()

        // Every 12 hours, never updated: runs the whole library at once and stores the start as "last updated"
        libraryPreferences.autoUpdateInterval().set(12)
        scheduler.checkNow() shouldBe listOf(LIBRARY_UPDATE_TASK)
        withTimeout(10_000) { scheduler.awaitRunning() }
        runner.progress.value?.target shouldBe LibraryUpdateRunner.Target.All
        runner.progress.value?.finished shouldBe true
        libraryPreferences.lastUpdatedTimestamp().get() shouldBe clock
        scheduler.checkNow() shouldBe emptyList()

        // A manual update 11 hours later postpones the next scheduled one
        clock += 11 * HOUR
        runner.start(LibraryUpdateRunner.Target.Category(0)) shouldBe true
        withTimeout(10_000) { runner.progress.first { it?.finished == true } }
        clock += 2 * HOUR
        scheduler.checkNow() shouldBe emptyList()

        // While another update runs, the scheduled one is "busy" and does not start a second update
        clock += 11 * HOUR
        val blocking = object : LibraryUpdateRunner by runner {
            override fun start(target: LibraryUpdateRunner.Target): Boolean = false
        }
        val busyScheduler = DesktopScheduler(scope, listOf(libraryUpdateTask(libraryPreferences, blocking)), now = { clock })
        busyScheduler.checkNow() shouldBe listOf(LIBRARY_UPDATE_TASK)
        busyScheduler.awaitRunning()
        busyScheduler.checkNow() shouldBe emptyList()
        clock += 10 * MINUTE
        busyScheduler.checkNow() shouldBe listOf(LIBRARY_UPDATE_TASK)
        busyScheduler.awaitRunning()
        libraryPreferences.autoUpdateInterval().set(0)
    }

    @Test
    fun `the automatic backup writes into the automatic backups folder and stores its time`() = runBlocking<Unit> {
        val jobs = DesktopBackupJobs(Injekt.get<DesktopBackupService>())
        val scheduler = DesktopScheduler(scope, listOf(automaticBackupTask(backupPreferences, jobs)), now = { clock })
        backupPreferences.backupInterval().set(0)
        scheduler.checkNow() shouldBe emptyList()

        backupPreferences.backupInterval().set(6)
        backupPreferences.lastAutoBackupTimestamp().set(0)
        scheduler.checkNow() shouldBe listOf(AUTOMATIC_BACKUP_TASK)
        withTimeout(30_000) { scheduler.awaitRunning() }
        val folder = File(Injekt.get<DesktopStorageFolderProvider>().directory(), "autobackup")
        folder.listFiles().orEmpty().count { it.name.endsWith(".tachibk") } shouldBe 1
        // Set by the borrowed creator (real time), so the next one is not due on the injected clock either way
        (backupPreferences.lastAutoBackupTimestamp().get() > 0) shouldBe true
        jobs.status.value shouldBe DesktopBackupJobs.Status.Idle

        // While a restore runs, the automatic backup waits ("busy")
        val gate = CompletableDeferred<Unit>()
        val restoring = DesktopBackupJobs(
            createBackup = { target, _ -> target },
            restoreBackup = { _, _, _ -> gate.await() },
            scope = scope,
        )
        restoring.restore(File("backup.tachibk"), RestoreOptions()) shouldBe true
        restoring.createAutomatic() shouldBe null
        gate.complete(Unit)
        backupPreferences.backupInterval().set(0)
    }

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
    }
}
