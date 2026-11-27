package app.komikku.desktop.extension

import app.komikku.desktop.extension.apk.ApkInspector
import app.komikku.desktop.extension.dex.DexJarConverter
import app.komikku.desktop.extension.loader.ExtensionClassLoader
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.io.File
import java.util.zip.ZipFile

/** Thrown when an extension's code cannot be converted, loaded or instantiated. */
class ExtensionLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** An extension whose sources were instantiated. */
class LoadedExtension(
    val extension: ExtensionPackage,
    val sources: List<Source>,
    val classLoader: ClassLoader,
    /** Methods dex2jar could not translate (they throw when called). */
    val failedMethods: List<String>,
    /** The converted jar the classes come from. */
    val jar: File,
)

/** Result of [ExtensionRuntime.verifyClasses]. */
data class ClassVerification(
    /** Classes that loaded, linked and passed the bytecode verifier. */
    val verified: Int,
    /** Classes the verifier or the class file parser rejected (`name: error`): a broken conversion. */
    val rejected: List<String>,
    /** Classes that refer to a class missing from the host (`name: missing class`): a gap in the compat layer. */
    val unresolved: List<String>,
)

/**
 * The desktop extension pipeline below the extension manager: inspect an APK
 * ([inspect] → [ExtensionPackage]), then convert, class-load and instantiate its sources ([load]).
 *
 * Jars converted from APKs are cached in `<extensionsDir>/jars/` (see [DexJarConverter]).
 *
 * @param hostClassLoader the parent of every extension class loader; it must see the compat layer,
 * `source-api` and the network stack (the application class loader on desktop).
 */
class ExtensionRuntime(
    extensionsDir: File,
    private val hostClassLoader: ClassLoader = ExtensionRuntime::class.java.classLoader,
) {

    val jarCacheDir: File = File(extensionsDir, "jars")

    private val converter = DexJarConverter(jarCacheDir, hostClassLoader)

    /** Reads [apk] and validates it as an extension (see [ExtensionPackage.from]). */
    fun inspect(apk: File): ExtensionPackageResult = try {
        ExtensionPackage.from(ApkInspector.inspect(apk))
    } catch (e: Exception) {
        ExtensionPackageResult.Rejected("Not a readable APK (${apk.name}): ${e.message}")
    }

    /** The cached converted jar of [extension] (whether it exists or not). */
    fun cachedJar(extension: ExtensionPackage): File = converter.cachedJar(extension.apk.sha256)

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
        if (conversion.unverifiableClasses.isNotEmpty()) {
            logcat(LogPriority.WARN) {
                "${conversion.unverifiableClasses.size} class(es) of ${extension.pkgName} could not be made verifiable:\n" +
                    conversion.unverifiableClasses.joinToString("\n")
            }
        }
        val classLoader = ExtensionClassLoader(conversion.jar, hostClassLoader)
        val sources = try {
            instantiateSources(extension.sourceClassNames, classLoader)
        } catch (e: Throwable) {
            classLoader.close()
            throw ExtensionLoadException("Extension load error: ${extension.name} (${extension.pkgName}): $e", e)
        }
        return LoadedExtension(extension, sources, classLoader, conversion.failedMethods, conversion.jar)
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

        /**
         * Loads and links every class of the converted [jar] in its own extension class loader (parent [parent]),
         * which runs the JVM's bytecode verifier on all of them, without initialising any.
         */
        fun verifyClasses(jar: File, parent: ClassLoader = ExtensionRuntime::class.java.classLoader): ClassVerification {
            val names = ZipFile(jar).use { zip ->
                zip.entries().asSequence()
                    .map { it.name }
                    .filter { it.endsWith(".class") && it != "module-info.class" }
                    .map { it.removeSuffix(".class").replace('/', '.') }
                    .sorted()
                    .toList()
            }
            var verified = 0
            val rejected = mutableListOf<String>()
            val unresolved = mutableListOf<String>()
            ExtensionClassLoader(jar, parent).use { loader ->
                for (name in names) {
                    try {
                        // Reflection on the declared members links the class (and verifies it) without initialising it.
                        Class.forName(name, false, loader).declaredMethods
                        verified++
                    } catch (e: NoClassDefFoundError) {
                        unresolved += "$name: ${e.message}"
                    } catch (e: TypeNotPresentException) {
                        unresolved += "$name: ${e.typeName()}"
                    } catch (e: LinkageError) {
                        rejected += "$name: ${e.toString().lineSequence().first()}"
                    }
                }
            }
            return ClassVerification(verified, rejected, unresolved)
        }
    }
}
