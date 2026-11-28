package app.komikku.desktop.smoke

import app.komikku.desktop.ui.navigation.NavigatorRegistry
import dev.icerock.moko.resources.desc.StringDesc
import tachiyomi.core.common.desktop.DesktopDirs
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.system.exitProcess

/**
 * `--ui-smoke=<report folder>` (UI_SMOKE_DESIGN.md, ATLAS_INSTRUCTIONS.md ANSWER 011): the app's real start on a new
 * temporary data folder with a generated manga ([SmokeFixture]), driven through a fixed script ([SmokeScript]) by its
 * own navigation and keyboard input, a screenshot after each step and a report ([SmokeReport]). Exit codes:
 * [EXIT_PASSED], [EXIT_FAILED] (a step, a network attempt or the shutdown), [EXIT_REFUSED] (before anything started),
 * [EXIT_WATCHDOG]. `--ui-smoke-keep` keeps the data folder for debugging.
 *
 * Only that argument switches it on (no environment variable, no preference) and it runs alone: any other argument is
 * refused. `main()` calls [startIfRequested] first, [UiSmokeSession.onInjektReady] after the DI start and
 * [UiSmokeSession.finish] for the exit code.
 */
internal object UiSmoke {

    const val ARGUMENT = "--ui-smoke"
    const val KEEP_ARGUMENT = "--ui-smoke-keep"

    const val EXIT_PASSED = 0
    const val EXIT_FAILED = 1
    const val EXIT_REFUSED = 2
    const val EXIT_WATCHDOG = 3

    sealed interface Request {
        data class Run(val reportFolder: File, val keepDataFolder: Boolean) : Request
        data class Refused(val reason: String, val reportFolder: File?) : Request
    }

    fun isRequested(args: Array<String>): Boolean = args.any { isSmokeArgument(it) }

    private fun isSmokeArgument(arg: String) = arg == ARGUMENT || arg.startsWith("$ARGUMENT=") || arg == KEEP_ARGUMENT

    fun parse(args: Array<String>): Request {
        val reports = args.filter { it == ARGUMENT || it.startsWith("$ARGUMENT=") }
        val folder = reports.singleOrNull()?.substringAfter('=', "")?.takeIf { it.isNotBlank() }
            ?.let { File(it).absoluteFile.normalize() }
        val others = args.filterNot(::isSmokeArgument)
        return when {
            reports.size > 1 -> Request.Refused("$ARGUMENT is given more than once", null)
            folder == null -> Request.Refused("$ARGUMENT needs a report folder: $ARGUMENT=<report folder>", null)
            others.isNotEmpty() -> Request.Refused("$ARGUMENT runs alone, without ${others.joinToString(" ")}", folder)
            else -> Request.Run(folder, keepDataFolder = KEEP_ARGUMENT in args)
        }
    }

    /**
     * `main()`'s first call: `null` for a normal start. Otherwise installs the network trap (before anything else),
     * creates and checks the data folder and returns the started session; a refusal is reported and exits with
     * [EXIT_REFUSED].
     */
    fun startIfRequested(
        args: Array<String>,
        tempRoot: Path = Path.of(System.getProperty("java.io.tmpdir")),
        exit: (Int) -> Nothing = { exitProcess(it) },
    ): UiSmokeSession? {
        if (!isRequested(args)) return null
        val trap = NetworkTrap().also { it.install() }
        val request = when (val parsed = parse(args)) {
            is Request.Refused -> refuse(parsed, exit)
            is Request.Run -> parsed
        }
        val protectedFolders = SmokeDataFolder.protectedFolders()
        val dataFolder = try {
            SmokeDataFolder.create(tempRoot)
        } catch (e: Exception) {
            refuse(Request.Refused("cannot create a temporary data folder in $tempRoot: $e", request.reportFolder), exit)
        }
        SmokeDataFolder.refusal(dataFolder, tempRoot, protectedFolders)?.let { reason ->
            runCatching { Files.deleteIfExists(dataFolder) }
            refuse(Request.Refused(reason, request.reportFolder), exit)
        }
        System.setProperty(DesktopDirs.DATA_DIR_PROPERTY, dataFolder.toString())
        val resolved = DesktopDirs.dataDir.toPath()
        if (runCatching { Files.isSameFile(resolved, dataFolder) }.getOrDefault(false).not()) {
            refuse(Request.Refused("the data folder resolves to $resolved, not to $dataFolder", request.reportFolder), exit)
        }
        return UiSmokeSession(request.reportFolder, dataFolder.toFile(), request.keepDataFolder, trap).also { it.start() }
    }

    private fun refuse(refused: Request.Refused, exit: (Int) -> Nothing): Nothing {
        System.err.println("UI smoke refused: ${refused.reason}")
        refused.reportFolder?.let { folder ->
            runCatching {
                SmokeReport(folder).apply {
                    environment("exit code", EXIT_REFUSED)
                    finish(SmokeReport.Outcome.REFUSED, refused.reason, emptyList())
                }
            }
        }
        exit(EXIT_REFUSED)
    }

    /** The frame every run shows the same way (ANSWER 011, condition 2), set before the DI start. */
    internal fun fixLocale() {
        Locale.setDefault(Locale.ENGLISH)
        StringDesc.localeType = StringDesc.LocaleType.Custom("en")
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"))
    }

    internal fun installNavigators(navigators: SmokeNavigators) = NavigatorRegistry.install(navigators)
}
