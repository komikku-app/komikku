package app.komikku.desktop.extension.linkcheck

import app.komikku.desktop.compat.UnsupportedOnDesktop
import app.komikku.desktop.extension.ExtensionPackage
import app.komikku.desktop.extension.ExtensionPackageResult
import app.komikku.desktop.extension.ExtensionPaths
import app.komikku.desktop.extension.ExtensionRuntime
import app.komikku.desktop.extension.apk.ApkInspector
import app.komikku.desktop.extension.loader.ExtensionClassLoader
import java.io.File
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Prepares the host for creating extension sources: extensions resolve the app's `Application` and network helper from
 * Injekt in their constructors. The desktop app provides it (`META-INF/services`), so `--create-sources` works on the
 * app's class path only (`:desktopApp:extensionLinkCheck`, or the packaged app's `lib/app` jars).
 */
interface ExtensionHostEnvironment {
    /** Called once, before the first source is created, with an empty data folder for the run. */
    fun prepare(dataDir: File)
}

/**
 * The link check's per-extension checks beyond the references (`LinkCheckMain`'s `--verify`, `--create-sources` and
 * `--compat-usage`), each written as one line of the report after the extension's link check (see [CatalogueSummary]):
 *
 * - `Verification: <n> verified, <n> rejected, <n> unresolved`: every class of the converted jar loaded and linked
 *   (the JVM's verifier), like the self-test;
 * - `Sources: <n> created` / `Sources: unsupported on desktop: needs <class.member>` / `Sources: failed: <cause>` /
 *   `Sources: not an extension: <reason>`: the sources instantiated like the extension manager does;
 * - `Compat classes used: <classes>`: the compat layer's classes the extension references (`COMPAT_REGISTRY.md`).
 */
class ExtensionChecks(
    private val verify: Boolean,
    private val createSources: Boolean,
    private val compatUsage: Boolean,
    private val hostClassLoader: ClassLoader,
    private val dataDir: File,
    private val sourceTimeoutSeconds: Long = 60,
) {

    private var environmentReady = false

    /** The compat layer's classes: those the host loads from the jar (or folder) of `:desktop:android-compat`. */
    private val compatLocation: String = UnsupportedOnDesktop::class.java.protectionDomain.codeSource.location.toString()

    private val compatClasses = HashMap<String, Boolean>()

    /** For [LinkChecker]'s `trackHostClass`: whether [internalName] is a compat class. */
    fun isCompatClass(internalName: String): Boolean = compatClasses.getOrPut(internalName) {
        hostClassLoader.getResource("$internalName.class")?.toString()?.contains(compatLocation.removePrefix("file:")) == true
    }

    /** Appends the enabled checks' lines for the extension [apk] (`null` for a jar input) converted to [jar]. */
    fun appendTo(out: StringBuilder, apk: File?, jar: File, links: LinkCheckReport) {
        if (verify) {
            val verification = ExtensionRuntime.verifyClasses(jar, hostClassLoader)
            out.appendLine(
                "Verification: ${verification.verified} verified, ${verification.rejected.size} rejected, " +
                    "${verification.unresolved.size} unresolved",
            )
            verification.rejected.take(LISTED).forEach { out.appendLine("  rejected: $it") }
            verification.unresolved.take(LISTED).forEach { out.appendLine("  unresolved: $it") }
        }
        if (createSources && apk != null) out.appendLine("Sources: ${sources(apk, jar)}")
        if (compatUsage) {
            val used = links.usedHostClasses.map { it.substringBefore('$').replace('/', '.') }.distinct().sorted()
            out.appendLine("Compat classes used: ${used.joinToString().ifEmpty { "none" }}")
        }
    }

    private fun sources(apk: File, jar: File): String {
        val extension = when (
            val result = runCatching { ExtensionPackage.from(ApkInspector.inspect(apk)) }.getOrElse { e ->
                ExtensionPackageResult.Rejected("${e.message}")
            }
        ) {
            is ExtensionPackageResult.Rejected -> return "not an extension: ${result.reason.firstLine()}"
            is ExtensionPackageResult.Valid -> result.extension
        }
        if (!environmentReady) {
            val environment = java.util.ServiceLoader.load(ExtensionHostEnvironment::class.java, hostClassLoader).firstOrNull()
                ?: error("--create-sources needs the desktop app's class path (:desktopApp:extensionLinkCheck)")
            environment.prepare(File(dataDir, "app").apply { mkdirs() })
            environmentReady = true
        }
        val loader = ExtensionClassLoader(
            jar,
            hostClassLoader,
            extensionDataDir = ExtensionPaths.packageFile(File(dataDir, ExtensionRuntime.DATA_FOLDER), extension.pkgName),
        )
        val executor = Executors.newSingleThreadExecutor { Thread(it, "linkcheck-sources").apply { isDaemon = true } }
        return try {
            val created = executor.submit<Int> {
                ExtensionRuntime.instantiateSources(extension.sourceClassNames, loader).size
            }.get(sourceTimeoutSeconds, TimeUnit.SECONDS)
            "$created created"
        } catch (e: ExecutionException) {
            val chain = generateSequence(e.cause) { it.cause }.toList()
            chain.filterIsInstance<UnsupportedOnDesktop>().firstOrNull()?.let { "unsupported on desktop: needs ${it.needs}" }
                ?: "failed: ${chain.last().toString().firstLine()}"
        } catch (_: TimeoutException) {
            "failed: no result after $sourceTimeoutSeconds s"
        } finally {
            executor.shutdownNow()
            loader.close()
        }
    }

    private fun String.firstLine(): String = lineSequence().first()

    private companion object {
        const val LISTED = 3
    }
}
