package app.komikku.desktop.smoke

import app.komikku.desktop.log.DesktopLogFile
import app.komikku.desktop.notification.DesktopTrayPreferences
import app.komikku.desktop.window.DesktopWindowPreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.ThemeMode
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import tachiyomi.domain.backup.service.BackupPreferences
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.storage.service.StorageManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One UI smoke run, from before the DI start to the exit code. The report, every screenshot so far, the app log and
 * the accessibility tree of the last frame are in the report folder on every exit, the watchdog's included (ANSWER 011,
 * condition 3).
 */
internal class UiSmokeSession(
    val reportFolder: File,
    val dataFolder: File,
    private val keepDataFolder: Boolean,
    private val trap: NetworkTrap,
    private val watchdogMillis: Long = WATCHDOG_MILLIS,
    private val halt: (Int) -> Unit = { Runtime.getRuntime().halt(it) },
) {

    val report = SmokeReport(reportFolder)
    val navigators = SmokeNavigators()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val ended = AtomicBoolean(false)
    private val watchdog = Thread(::watch, "ui-smoke-watchdog").apply { isDaemon = true }

    @Volatile
    private var driver: WindowSmokeDriver? = null

    /** The script's result: `null` while it runs. */
    @Volatile
    private var failure: String? = null

    @Volatile
    private var scriptEnded = false

    @Volatile
    private var closeRequestedAt = 0L

    /** Before the DI start: the fixed frame, the navigator registry and the watchdog. */
    fun start() {
        UiSmoke.fixLocale()
        // App update checks off: the packaged app's launcher may turn them on (`komikku.updater`, read at the DI start)
        System.setProperty("komikku.updater", "false")
        UiSmoke.installNavigators(navigators)
        report.environment("os", "${System.getProperty("os.name")} ${System.getProperty("os.version")} ${System.getProperty("os.arch")}")
        report.environment("java", "${System.getProperty("java.vendor")} ${System.getProperty("java.runtime.version")}")
        report.environment("data folder", dataFolder)
        report.environment("locale", java.util.Locale.getDefault().toLanguageTag())
        report.write()
        watchdog.start()
    }

    /**
     * After the DI start, before the scheduler and the window: the presets that make every run the same and keep it
     * offline, the fixture, then the script once the window shows.
     */
    fun onInjektReady() {
        prepareApp()
        scope.launch { runScript() }
    }

    private suspend fun runScript() {
        try {
            val window = WindowSmokeDriver.awaitWindow(WINDOW_TIMEOUT_MILLIS)
                ?: error("The window did not show within $WINDOW_TIMEOUT_MILLIS ms")
            val driver = WindowSmokeDriver(window, navigators).also { driver = it }
            withTimeoutOrNull(WINDOW_TIMEOUT_MILLIS) { while (!navigators.hasTabs) delay(50) }
                ?: error("The home shell did not show within $WINDOW_TIMEOUT_MILLIS ms")
            driver.onUi {
                val layer = driver.layer()
                report.environment("render api", layer.renderApi)
                report.environment("scale", layer.contentScale)
                report.environment("window", "${window.width}x${window.height} (content ${layer.width}x${layer.height})")
                report.environment("key target", driver.keyTarget().javaClass.name)
                check(window.width == WINDOW_WIDTH && window.height == WINDOW_HEIGHT) {
                    "The window is ${window.width}x${window.height}, not ${WINDOW_WIDTH}x$WINDOW_HEIGHT"
                }
            }
            failure = SmokeScript(driver).run(report, reportFolder)
        } catch (e: Throwable) {
            failure = "start: ${e.stackTraceToString()}"
        } finally {
            writeAccessibilityTree()
            scriptEnded = true
            closeRequestedAt = System.nanoTime()
            runCatching { driver?.close() }
        }
    }

    /** `main()`, after the app's shutdown: the final report, the log, the clean-up; returns the exit code. */
    fun finish(): Int {
        if (!ended.compareAndSet(false, true)) return UiSmoke.EXIT_WATCHDOG
        watchdog.interrupt()
        scope.cancel()
        if (closeRequestedAt != 0L) {
            report.environment("shutdown", "${(System.nanoTime() - closeRequestedAt) / 1_000_000} ms after the close request")
        }
        val attempts = trap.attempts
        val problem = when {
            !scriptEnded -> "The window closed before the script ended"
            failure != null -> failure
            attempts.isNotEmpty() -> "${attempts.size} network attempts (listed above)"
            else -> null
        }
        val code = if (problem == null) UiSmoke.EXIT_PASSED else UiSmoke.EXIT_FAILED
        end(code, problem, deleteNow = false)
        // Shutdown hooks still write to the data folder (the cookie store's flush): delete it after them
        if (!keepDataFolder) {
            Runtime.getRuntime().addShutdownHook(
                Thread({
                    // The hooks start in no set order: give them a moment to start, then wait for the writers
                    Thread.sleep(HOOKS_START_MILLIS)
                    Thread.getAllStackTraces().keys.filter { it.name in DATA_FOLDER_WRITERS }.forEach { it.join(UI_TIMEOUT_MILLIS) }
                    deleteDataFolder()
                }, "ui-smoke-clean-up"),
            )
        }
        return code
    }

    private fun watch() {
        try {
            Thread.sleep(watchdogMillis)
        } catch (_: InterruptedException) {
            return
        }
        if (!ended.compareAndSet(false, true)) return
        val shot = File(reportFolder, "watchdog.png")
        val taken = runCatching { runBlocking { withTimeoutOrNull(UI_TIMEOUT_MILLIS) { driver?.screenshot(shot) } } }.getOrNull()
        if (taken != null) report.note("last frame at the watchdog: ${shot.name}")
        writeAccessibilityTree()
        end(UiSmoke.EXIT_WATCHDOG, "Watchdog: no result after ${TimeUnit.MILLISECONDS.toSeconds(watchdogMillis)} s\n${threadDump()}")
        halt(UiSmoke.EXIT_WATCHDOG)
    }

    private fun end(code: Int, problem: String?, deleteNow: Boolean = true) {
        copyLog()
        report.environment("exit code", code)
        report.finish(if (problem == null) SmokeReport.Outcome.PASSED else SmokeReport.Outcome.FAILED, problem, trap.attempts)
        if (deleteNow && !keepDataFolder) deleteDataFolder()
    }

    private fun writeAccessibilityTree() {
        val file = File(reportFolder, SmokeReport.ACCESSIBILITY_FILE)
        val tree = runCatching {
            runBlocking { withTimeoutOrNull(UI_TIMEOUT_MILLIS) { driver?.accessibilityTree() } }
                ?: "No window, or its UI thread did not answer within $UI_TIMEOUT_MILLIS ms\n"
        }.getOrElse { "Could not read the accessibility tree: ${it.stackTraceToString()}" }
        runCatching {
            reportFolder.mkdirs()
            file.writeText(tree)
        }
    }

    private fun copyLog() {
        val log = File(DesktopLogFile.directoryFor(dataFolder), SmokeReport.LOG_FILE)
        runCatching { if (log.isFile) log.copyTo(File(reportFolder, SmokeReport.LOG_FILE), overwrite = true) }
    }

    private fun deleteDataFolder() {
        if (!dataFolder.name.startsWith(SmokeDataFolder.PREFIX)) return
        if (!dataFolder.deleteRecursively()) System.err.println("UI smoke: could not delete all of the data folder $dataFolder")
    }

    private fun threadDump(): String = Thread.getAllStackTraces().entries.joinToString("\n\n") { (thread, stack) ->
        "\"${thread.name}\" ${thread.state}\n" + stack.joinToString("\n") { "    at $it" }
    }

    companion object {
        const val WINDOW_WIDTH = 1280
        const val WINDOW_HEIGHT = 800
        private const val WATCHDOG_MILLIS = 5 * 60_000L
        private const val WINDOW_TIMEOUT_MILLIS = 60_000L
        private const val UI_TIMEOUT_MILLIS = 5_000L

        /** Shutdown hooks that write to the data folder. */
        private val DATA_FOLDER_WRITERS = setOf("android-compat-cookie-flush")
        private const val HOOKS_START_MILLIS = 200L

        /** The presets of a smoke run (the same frame every time, nothing scheduled) and the fixture. */
        fun prepareApp() {
            Injekt.get<UiPreferences>().themeMode().set(ThemeMode.LIGHT)
            Injekt.get<DesktopWindowPreferences>().apply {
                windowWidth().set(WINDOW_WIDTH)
                windowHeight().set(WINDOW_HEIGHT)
                windowMaximized().set(false)
            }
            Injekt.get<DesktopTrayPreferences>().trayIcon().set(false)
            Injekt.get<BackupPreferences>().backupInterval().set(0)
            Injekt.get<LibraryPreferences>().autoUpdateInterval().set(0)
            // One page per screen: the automatic layout shows two pages side by side in a landscape window
            Injekt.get<ReaderPreferences>().pageLayout().set(PagerConfig.PageLayout.SINGLE_PAGE)
            val localFolder = Injekt.get<StorageManager>().getLocalSourceDirectory()?.filePath
                ?: error("The local source has no folder")
            SmokeFixture.write(File(localFolder))
        }
    }
}
