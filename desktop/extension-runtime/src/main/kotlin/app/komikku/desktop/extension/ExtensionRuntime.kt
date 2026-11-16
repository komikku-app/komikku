package app.komikku.desktop.extension

import app.komikku.desktop.extension.apk.ApkInspector
import app.komikku.desktop.extension.dex.DexJarConverter
import app.komikku.desktop.extension.loader.ExtensionClassLoader
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.io.File

/** Thrown when an extension's code cannot be converted, loaded or instantiated. */
class ExtensionLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** An extension whose sources were instantiated. */
class LoadedExtension(
    val extension: ExtensionPackage,
    val sources: List<Source>,
    val classLoader: ClassLoader,
    /** Methods dex2jar could not translate (they throw when called). */
    val failedMethods: List<String>,
)

/**
 * The desktop extension pipeline below the extension manager: inspect an APK
 * ([inspect] → [ExtensionPackage]), then convert, class-load and instantiate its sources ([load]).
 *
 * Jars converted from APKs are cached in `<extensionsDir>/jars/<apk sha256>.jar`.
 *
 * @param hostClassLoader the parent of every extension class loader; it must see the compat layer,
 * `source-api` and the network stack (the application class loader on desktop).
 */
class ExtensionRuntime(
    extensionsDir: File,
    private val hostClassLoader: ClassLoader = ExtensionRuntime::class.java.classLoader,
) {

    val jarCacheDir: File = File(extensionsDir, "jars")

    private val converter = DexJarConverter(jarCacheDir)

    /** Reads [apk] and validates it as an extension (see [ExtensionPackage.from]). */
    fun inspect(apk: File): ExtensionPackageResult = try {
        ExtensionPackage.from(ApkInspector.inspect(apk))
    } catch (e: Exception) {
        ExtensionPackageResult.Rejected("Not a readable APK (${apk.name}): ${e.message}")
    }

    /**
     * Converts [extension]'s dex code (cached), creates its class loader and instantiates its
     * sources exactly like `ExtensionLoader`: every class in `tachiyomi.extension.class` is created
     * through its no-argument constructor and must be a [Source] or a [SourceFactory].
     */
    fun load(extension: ExtensionPackage): LoadedExtension {
        val conversion = try {
            converter.jarFor(extension.apk.file, extension.apk.sha256)
        } catch (e: Exception) {
            throw ExtensionLoadException("Could not convert ${extension.pkgName}: ${e.message}", e)
        }
        if (conversion.failedMethods.isNotEmpty()) {
            logcat(LogPriority.WARN) {
                "dex2jar could not translate ${conversion.failedMethods.size} method(s) of ${extension.pkgName}:\n" +
                    conversion.failedMethods.joinToString("\n")
            }
        }
        val classLoader = ExtensionClassLoader(conversion.jar, hostClassLoader)
        val sources = try {
            instantiateSources(extension.sourceClassNames, classLoader)
        } catch (e: Throwable) {
            classLoader.close()
            throw ExtensionLoadException("Extension load error: ${extension.name} (${extension.pkgName}): $e", e)
        }
        return LoadedExtension(extension, sources, classLoader, conversion.failedMethods)
    }

    /** Deletes converted jars of APKs that are no longer installed. */
    fun pruneJarCache(installedApkHashes: Set<String>) = converter.prune(installedApkHashes)

    companion object {
        /** Instantiates [classNames] from [classLoader] like `ExtensionLoader`. */
        fun instantiateSources(classNames: List<String>, classLoader: ClassLoader): List<Source> =
            classNames.flatMap { className ->
                when (val obj = Class.forName(className, false, classLoader).getDeclaredConstructor().newInstance()) {
                    is Source -> listOf(obj)
                    is SourceFactory -> obj.createSources()
                    else -> throw ExtensionLoadException("Unknown source class type: ${obj.javaClass}")
                }
            }
    }
}
