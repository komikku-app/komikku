package app.komikku.desktop.scheduler

import android.app.Application
import android.widget.Toast
import app.komikku.desktop.backup.DesktopBackupJobs
import app.komikku.desktop.backup.DesktopBackupService
import app.komikku.desktop.di.DesktopInjekt
import app.komikku.desktop.library.desktopLibraryUpdateRunner
import app.komikku.desktop.platform.DesktopFileDialogs
import app.komikku.desktop.ui.more.DataStorageScreenModel
import app.komikku.shared.library.LibraryUpdateRunner
import eu.kanade.tachiyomi.data.backup.create.BackupCreator
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.restore.RestoreOptions
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.storage.DesktopStorageFolderProvider
import tachiyomi.domain.backup.service.BackupPreferences
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
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

    private val schedulerPreferences by lazy { Injekt.get<DesktopSchedulerPreferences>() }

    private fun updateRunner(sourceManager: SourceManager = Injekt.get()) = desktopLibraryUpdateRunner(
        getLibraryManga = Injekt.get(),
        libraryPreferences = libraryPreferences,
        fetchInterval = Injekt.get(),
        sourceManager = sourceManager,
        updateMangaFromRemote = Injekt.get(),
        insertErrors = Injekt.get(),
        insertErrorMessages = Injekt.get(),
        scope = scope,
        now = { clock },
    )

    @Test
    fun `the library update runs on its interval, stores the last update and waits for a running one`() = runBlocking<Unit> {
        val runner = updateRunner()
        val scheduler = DesktopScheduler(scope, listOf(libraryUpdateTask(libraryPreferences, schedulerPreferences, runner) { clock }), now = { clock })
        // "Never" (Android's default): nothing runs
        libraryPreferences.autoUpdateInterval().set(0)
        scheduler.checkNow() shouldBe emptyList()

        // Every 12 hours, never updated: runs the whole library at once and stores the start as "last updated" and as
        // the scheduler's own last run
        libraryPreferences.autoUpdateInterval().set(12)
        scheduler.checkNow() shouldBe listOf(LIBRARY_UPDATE_TASK)
        withTimeout(10_000) { scheduler.awaitRunning() }
        runner.progress.value?.target shouldBe LibraryUpdateRunner.Target.All
        runner.progress.value?.finished shouldBe true
        libraryPreferences.lastUpdatedTimestamp().get() shouldBe clock
        schedulerPreferences.lastScheduledLibraryUpdate().get() shouldBe clock
        scheduler.checkNow() shouldBe emptyList()

        // A manual (partial) update 11 hours later is shown as "last updated" but does not postpone the scheduled one
        // (Android's periodic work keeps its period); a fresh scheduler (next launch) sees the same due time
        clock += 11 * HOUR
        runner.start(LibraryUpdateRunner.Target.Category(0)) shouldBe true
        withTimeout(10_000) { runner.progress.first { it?.finished == true } }
        libraryPreferences.lastUpdatedTimestamp().get() shouldBe clock
        clock += 1 * HOUR
        val nextLaunch = DesktopScheduler(scope, listOf(libraryUpdateTask(libraryPreferences, schedulerPreferences, runner) { clock }), now = { clock })
        nextLaunch.checkNow() shouldBe listOf(LIBRARY_UPDATE_TASK)
        withTimeout(10_000) { nextLaunch.awaitRunning() }
        schedulerPreferences.lastScheduledLibraryUpdate().get() shouldBe clock

        // While another update runs, the scheduled one is "busy" and does not start a second update
        clock += 12 * HOUR
        val blocking = object : LibraryUpdateRunner by runner {
            override fun start(target: LibraryUpdateRunner.Target): Boolean = false
        }
        val busyScheduler = DesktopScheduler(scope, listOf(libraryUpdateTask(libraryPreferences, schedulerPreferences, blocking) { clock }), now = { clock })
        busyScheduler.checkNow() shouldBe listOf(LIBRARY_UPDATE_TASK)
        busyScheduler.awaitRunning()
        busyScheduler.checkNow() shouldBe emptyList()
        clock += 10 * MINUTE
        busyScheduler.checkNow() shouldBe listOf(LIBRARY_UPDATE_TASK)
        busyScheduler.awaitRunning()
        libraryPreferences.autoUpdateInterval().set(0)
    }

    @Test
    fun `a library update waits for the sources to load`() = runBlocking<Unit> {
        val initialized = MutableStateFlow(false)
        val loading = object : SourceManager by Injekt.get<SourceManager>() {
            override val isInitialized: StateFlow<Boolean> = initialized
        }
        val runner = updateRunner(loading)
        runner.start(LibraryUpdateRunner.Target.All) shouldBe true
        delay(300)
        runner.progress.value?.finished shouldBe false
        initialized.value = true
        withTimeout(10_000) { runner.progress.first { it?.finished == true } }
    }

    @Test
    fun `the automatic backup writes into the automatic backups folder and stores its time`() = runBlocking<Unit> {
        val jobs = DesktopBackupJobs(Injekt.get<DesktopBackupService>())
        val scheduler = DesktopScheduler(scope, listOf(automaticBackupTask(backupPreferences, jobs)), now = { clock })
        backupPreferences.backupInterval().set(0)
        scheduler.checkNow() shouldBe emptyList()

        val folder = File(Injekt.get<DesktopStorageFolderProvider>().directory(), "autobackup")
        folder.listFiles().orEmpty().forEach { it.deleteRecursively() }
        backupPreferences.backupInterval().set(6)
        backupPreferences.lastAutoBackupTimestamp().set(0)
        scheduler.checkNow() shouldBe listOf(AUTOMATIC_BACKUP_TASK)
        withTimeout(30_000) { scheduler.awaitRunning() }
        folder.listFiles().orEmpty().count { it.name.endsWith(".tachibk") } shouldBe 1
        // Set by the backup service (real time), so the next one is not due on the injected clock either way
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

    @Test
    fun `a cancelled automatic backup waits a whole interval`() = runBlocking<Unit> {
        val jobs = DesktopBackupJobs(
            createBackup = { target, _ -> target },
            restoreBackup = { _, _, _ -> },
            scope = scope,
            createAutomaticBackup = { awaitCancellation() },
        )
        val scheduler = DesktopScheduler(scope, listOf(automaticBackupTask(backupPreferences, jobs)), now = { clock })
        backupPreferences.backupInterval().set(6)
        backupPreferences.lastAutoBackupTimestamp().set(0)
        scheduler.checkNow() shouldBe listOf(AUTOMATIC_BACKUP_TASK)
        withTimeout(5_000) { jobs.status.first { it is DesktopBackupJobs.Status.Running } }
        // The data screen's cancel
        jobs.cancel()
        withTimeout(5_000) { scheduler.awaitRunning() }
        clock += 1 * MINUTE
        scheduler.checkNow() shouldBe emptyList()
        clock += 6 * HOUR
        scheduler.checkNow() shouldBe listOf(AUTOMATIC_BACKUP_TASK)
        withTimeout(5_000) { jobs.status.first { it is DesktopBackupJobs.Status.Running } }
        jobs.cancel()
        withTimeout(5_000) { scheduler.awaitRunning() }
        backupPreferences.backupInterval().set(0)
    }

    @Test
    fun `exit stops the scheduled and the manual work and waits for it to clean up`() = runBlocking<Unit> {
        val cleanedUp = CompletableDeferred<Unit>()
        val jobs = DesktopBackupJobs(
            createBackup = { target, _ -> target },
            restoreBackup = { _, _, _ -> },
            scope = scope,
            createAutomaticBackup = {
                try {
                    awaitCancellation()
                } finally {
                    withContext(NonCancellable) {
                        delay(300)
                        cleanedUp.complete(Unit)
                    }
                }
            },
        )
        val runner = updateRunner()
        val scheduler = DesktopScheduler(scope, listOf(automaticBackupTask(backupPreferences, jobs)), now = { clock })
        backupPreferences.backupInterval().set(6)
        backupPreferences.lastAutoBackupTimestamp().set(0)
        scheduler.checkNow() shouldBe listOf(AUTOMATIC_BACKUP_TASK)
        withTimeout(5_000) { jobs.status.first { it is DesktopBackupJobs.Status.Running } }
        stopDesktopWork(scheduler, jobs, runner) shouldBe true
        cleanedUp.isCompleted shouldBe true
        (jobs.status.value is DesktopBackupJobs.Status.Running) shouldBe false
        // Stopped: no new check starts it
        clock += 100 * HOUR
        scheduler.awaitRunning()
        backupPreferences.backupInterval().set(0)
    }

    @Test
    fun `the automatic backup rotates old files only after the new one is complete`() = runBlocking<Unit> {
        val service = Injekt.get<DesktopBackupService>()
        val folder = File(Injekt.get<DesktopStorageFolderProvider>().directory(), "autobackup").apply { mkdirs() }
        folder.listFiles().orEmpty().forEach { it.delete() }
        val applicationId = BackupCreator.getFilename().removeSuffix(".tachibk").dropLast("_yyyy-MM-dd_HH-mm".length)
        val old = (1..5).map { File(folder, "${applicationId}_2020-01-0${it}_00-00.tachibk").apply { writeText("old $it") } }
        val unrelated = File(folder, "notes.txt").apply { writeText("keep") }
        backupPreferences.lastAutoBackupTimestamp().set(0)

        val created = service.createAutomatic()
        created.parentFile shouldBe folder
        val left = folder.listFiles().orEmpty().map { it.name }.toSet()
        left shouldBe setOf(created.name, old[4].name, old[3].name, old[2].name, unrelated.name)
        (backupPreferences.lastAutoBackupTimestamp().get() > 0) shouldBe true

        // A failing run (an unwritable target: a directory with the new file's name) keeps every old backup
        created.delete()
        val name = BackupCreator.getFilename()
        File(folder, name).mkdirs()
        val before = folder.listFiles().orEmpty().map { it.name }.toSet()
        val result = runCatching { service.createAutomatic() }
        // (Unless the minute, and so the name, changed meanwhile)
        if (BackupCreator.getFilename() == name) {
            result.isFailure shouldBe true
            folder.listFiles().orEmpty().map { it.name }.toSet() shouldBe before
            folder.listFiles().orEmpty().none { it.name.endsWith(".tmp") } shouldBe true
        }
        folder.listFiles().orEmpty().forEach { it.deleteRecursively() }
    }

    @Test
    fun `a manual backup or restore while the slot is taken shows Android's message`() = runBlocking<Unit> {
        val gate = CompletableDeferred<Unit>()
        val jobs = DesktopBackupJobs(
            createBackup = { target, _ -> target },
            restoreBackup = { _, _, _ -> },
            scope = scope,
            createAutomaticBackup = { gate.await() },
        )
        val dialogs = object : DesktopFileDialogs {
            override fun saveFile(title: String, directory: File?, suggestedName: String) = File(folderForDialogs, suggestedName)
            override fun openFile(title: String, directory: File?, extensions: Set<String>) = null
            override fun chooseDirectory(title: String, directory: File?) = null
        }
        val toasts = mutableListOf<String>()
        val presenter = Toast.presenter
        Toast.presenter = { text, _ -> toasts += text.toString() }
        try {
            val model = DataStorageScreenModel(backupJobs = jobs, fileDialogs = dialogs)
            jobs.createAutomatic() shouldNotBe null
            model.createBackup(BackupOptions(), "Create backup")
            model.restore(File("backup.tachibk"), RestoreOptions())
            toasts shouldBe listOf(
                Injekt.get<Application>().stringResource(MR.strings.backup_in_progress),
                Injekt.get<Application>().stringResource(MR.strings.restore_in_progress),
            )
        } finally {
            Toast.presenter = presenter
            gate.complete(Unit)
        }
    }

    private val folderForDialogs by lazy { File(Injekt.get<DesktopStorageFolderProvider>().directory(), "manual").apply { mkdirs() } }

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
    }
}
