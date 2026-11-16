package app.komikku.desktop.backup

import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.restore.RestoreOptions
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.io.File

/**
 * 8.2 review fix: backup create / restore run in the app-lifetime [DesktopBackupJobs] (not in the screen's
 * scope, so leaving the screen does not cancel a restore halfway); the screen observes its status, one job
 * runs at a time, and an explicit cancel is reported as cancelled.
 */
class DesktopBackupJobsTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @AfterEach
    fun tearDown() = scope.cancel()

    private suspend fun DesktopBackupJobs.awaitStatus(predicate: (DesktopBackupJobs.Status) -> Boolean) =
        withTimeout(5_000) { status.first(predicate) }

    @Test
    fun `restore reports progress and completes independently of the caller`() = runBlocking<Unit> {
        val gate = CompletableDeferred<Unit>()
        val log = File.createTempFile("restore", ".txt").apply {
            writeText("error one\nerror two\n")
            deleteOnExit()
        }
        val jobs = DesktopBackupJobs(
            createBackup = { target, _ -> target },
            restoreBackup = { _, _, listener ->
                listener.onRestoreProgress("Manga A", 1, 2)
                gate.await()
                listener.onRestoreComplete(1234, 2, log)
            },
            scope = scope,
        )
        // Started from a caller scope that goes away (the screen model), like navigating Back
        val caller = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val started = CompletableDeferred<Boolean>()
        caller.launch { started.complete(jobs.restore(File("backup.tachibk"), RestoreOptions())) }
        started.await() shouldBe true
        caller.cancel()

        jobs.awaitStatus { it is DesktopBackupJobs.Status.Running && it.content == "Manga A" }
        // A second job is refused while one runs
        jobs.create(File("other.tachibk"), BackupOptions()) shouldBe false

        gate.complete(Unit)
        val done = jobs.awaitStatus { it is DesktopBackupJobs.Status.Restored }
        done shouldBe DesktopBackupJobs.Status.Restored(1234, listOf("error one", "error two"), log)

        jobs.acknowledge()
        jobs.status.value shouldBe DesktopBackupJobs.Status.Idle
    }

    @Test
    fun `cancel stops the running job and is reported as cancelled`() = runBlocking<Unit> {
        val jobs = DesktopBackupJobs(
            createBackup = { _, _ -> awaitCancellation() },
            restoreBackup = { _, _, listener ->
                listener.onRestoreProgress("Manga A", 1, 10)
                awaitCancellation()
            },
            scope = scope,
        )
        jobs.restore(File("backup.tachibk"), RestoreOptions()) shouldBe true
        jobs.awaitStatus { it is DesktopBackupJobs.Status.Running && it.content == "Manga A" }
        jobs.cancel()
        jobs.awaitStatus { it is DesktopBackupJobs.Status.Cancelled } shouldBe
            DesktopBackupJobs.Status.Cancelled(DesktopBackupJobs.Kind.Restore)

        // The next job can start; creation can be cancelled too
        jobs.create(File("new.tachibk"), BackupOptions()) shouldBe true
        jobs.awaitStatus { it is DesktopBackupJobs.Status.Running }
        jobs.cancel()
        jobs.awaitStatus { it is DesktopBackupJobs.Status.Cancelled } shouldBe
            DesktopBackupJobs.Status.Cancelled(DesktopBackupJobs.Kind.Create)
    }

    @Test
    fun `failures are reported with their message`() = runBlocking<Unit> {
        val jobs = DesktopBackupJobs(
            createBackup = { _, _ -> throw IllegalStateException("disk full") },
            restoreBackup = { _, _, _ -> },
            scope = scope,
        )
        jobs.create(File("new.tachibk"), BackupOptions()) shouldBe true
        val failed = jobs.awaitStatus { it is DesktopBackupJobs.Status.Failed }
        failed.shouldBeInstanceOf<DesktopBackupJobs.Status.Failed>().let {
            it.kind shouldBe DesktopBackupJobs.Kind.Create
            it.message shouldBe "disk full"
        }
    }
}
