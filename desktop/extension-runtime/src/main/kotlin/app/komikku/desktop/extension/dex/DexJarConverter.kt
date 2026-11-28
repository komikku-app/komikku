package app.komikku.desktop.extension.dex

import com.googlecode.d2j.Method
import com.googlecode.d2j.dex.BaseDexExceptionHandler
import com.googlecode.d2j.node.DexMethodNode
import org.objectweb.asm.MethodVisitor
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Thrown when an APK's dex code cannot be converted at all. */
class DexConversionException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Converts the dex code of an APK (`classes.dex`, `classes2.dex`, …) to a jar of verifiable JVM class files
 * with dex2jar (`de.femtopedia.dex2jar:dex-translator`, through [DexTranslator]), [InlinedConstructors] and
 * [StackMapFrames], with the APK's other entries as resources, and caches the result as
 * `<cacheDir>/<apk sha256>-v<CACHE_VERSION>.jar`, so every APK version is converted once.
 *
 * Methods dex2jar cannot translate are replaced by a body that throws
 * `RuntimeException("d2j fail translate: …")` (dex2jar's own fallback); they are reported in
 * [Conversion.failedMethods] and logged by the caller.
 *
 * @param classPath where the classes an extension builds on (compat layer, source-api, JDK) are looked up to compute
 * stack map frames; the parent of the extension class loaders.
 */
class DexJarConverter(
    private val cacheDir: File,
    private val classPath: ClassLoader = DexJarConverter::class.java.classLoader,
) {

    /** Result of [jarFor]. */
    data class Conversion(
        val jar: File,
        val fromCache: Boolean,
        val failedMethods: List<String>,
        /** Classes that could not be made verifiable (`name: reason`); they fail verification if they are loaded. */
        val unverifiableClasses: List<String> = emptyList(),
    )

    /** Result of [convert]. */
    data class Converted(val failedMethods: List<String>, val unverifiableClasses: List<String>)

    private val locks = ConcurrentHashMap<String, Any>()

    /** The cached jar of the APK with SHA-256 [sha256] (whether it exists or not). */
    fun cachedJar(sha256: String): File = File(cacheDir, "$sha256$CACHE_SUFFIX")

    /** The converted jar for [apk] (whose SHA-256 is [sha256]), converting it if it is not cached. */
    fun jarFor(apk: File, sha256: String): Conversion {
        require(sha256.matches(SHA256_PATTERN)) { "Not a SHA-256: $sha256" }
        val target = cachedJar(sha256)
        synchronized(locks.getOrPut(sha256) { Any() }) {
            if (target.isFile && target.length() > 0) return Conversion(target, fromCache = true, failedMethods = emptyList())
            cacheDir.mkdirs()
            val temp = File.createTempFile("$sha256-", ".jar.tmp", cacheDir)
            try {
                val converted = convert(apk, temp, classPath)
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                return Conversion(target, fromCache = false, converted.failedMethods, converted.unverifiableClasses)
            } finally {
                temp.delete()
            }
        }
    }

    /**
     * Deletes cached jars whose APK hash is not in [keep], and every jar of an older cache version (converted without
     * stack map frames before version 2), so they are converted again.
     */
    fun prune(keep: Set<String>) {
        val kept = keep.mapTo(HashSet()) { "$it$CACHE_SUFFIX" }
        cacheDir.listFiles()
            ?.filter { it.isFile && (it.name.endsWith(".jar.tmp") || (it.extension == "jar" && it.name !in kept)) }
            ?.forEach { it.delete() }
    }

    companion object {
        /**
         * Bumped whenever converted jars change, so cached ones are converted again. 2: stack map frames and
         * R8-inlined constructors (version 1 jars fail the JVM's verifier). 3: an inlined `new C` inside one of `C`'s own
         * constructors is re-targeted too (review M3, finding 2).
         */
        const val CACHE_VERSION = 3
        private const val CACHE_SUFFIX = "-v$CACHE_VERSION.jar"

        private val SHA256_PATTERN = Regex("[0-9a-f]{64}")
        private val DEX_ENTRY = Regex("classes\\d*\\.dex")
        private val SIGNING_ENTRY = Regex("META-INF/(MANIFEST\\.MF|[^/]+\\.(SF|RSA|DSA|EC))", RegexOption.IGNORE_CASE)

        /**
         * Converts [input] — an APK / zip with `classes*.dex` entries, or a bare `.dex` file — to the jar
         * [output] of verifiable class files, with the stack map frames computed against [classPath] (see
         * [StackMapFrames]).
         */
        fun convert(
            input: File,
            output: File,
            classPath: ClassLoader = DexJarConverter::class.java.classLoader,
        ): Converted {
            val failed = mutableListOf<String>()
            val handler = object : BaseDexExceptionHandler() {
                override fun handleFileException(e: Exception) {
                    throw DexConversionException("dex2jar failed for ${input.name}: ${e.message}", e)
                }

                override fun handleMethodTranslateException(method: Method, node: DexMethodNode, mv: MethodVisitor, e: Exception) {
                    synchronized(failed) { failed += "${method.owner}->${method.name}${method.desc}: ${e.message}" }
                    super.handleMethodTranslateException(method, node, mv, e)
                }
            }
            val dexBytes = readDex(input)
            output.delete()
            val translation = try {
                DexTranslator.translate(dexBytes, handler)
            } catch (e: DexConversionException) {
                throw e
            } catch (e: Exception) {
                throw DexConversionException("dex2jar failed for ${input.name}: ${e.message}", e)
            }
            if (translation.classes.isEmpty()) throw DexConversionException("dex2jar wrote no classes for ${input.name}")
            val classes = LinkedHashMap(translation.classes)
            val unverifiable = try {
                InlinedConstructors.addTo(classes, translation.inlinedConstructors) +
                    StackMapFrames.addTo(classes, classPath)
            } catch (e: Exception) {
                throw DexConversionException("Could not make the classes of ${input.name} verifiable: ${e.message}", e)
            }
            ZipOutputStream(output.outputStream().buffered()).use { jar ->
                for ((name, bytes) in classes) {
                    jar.putNextEntry(ZipEntry("$name.class"))
                    jar.write(bytes)
                    jar.closeEntry()
                }
                if (!isDex(input)) copyResources(input, jar)
            }
            return Converted(failed, unverifiable)
        }

        /**
         * Copies the APK's other entries (`assets/`, Java resources) to [jar]: Android's class loader reads resources
         * from the APK, and Keiyoushi's i18n library loads `assets/i18n/messages_<lang>.properties` through it
         * (MangaDex). Not the dex code, the signature, the jar manifest or class files.
         */
        private fun copyResources(apk: File, jar: ZipOutputStream) {
            ZipFile(apk).use { zip ->
                for (entry in zip.entries()) {
                    val name = entry.name
                    if (entry.isDirectory || DEX_ENTRY.matches(name) || name.endsWith(".class") || SIGNING_ENTRY.matches(name)) continue
                    jar.putNextEntry(ZipEntry(name))
                    zip.getInputStream(entry).use { it.copyTo(jar) }
                    jar.closeEntry()
                }
            }
        }

        private fun isDex(input: File): Boolean = input.inputStream().use { stream ->
            val magic = ByteArray(3)
            stream.read(magic) == 3 && magic.decodeToString() == "dex"
        }

        /**
         * The dex bytes to convert: a bare dex file as is; for an APK, the APK bytes themselves when it
         * has several `classes*.dex` entries (dex2jar's multi-dex reader opens zips), else its
         * `classes.dex`.
         */
        private fun readDex(input: File): ByteArray {
            val bytes = input.readBytes()
            if (bytes.size >= 4 && bytes[0] == 'd'.code.toByte() && bytes[1] == 'e'.code.toByte() && bytes[2] == 'x'.code.toByte()) {
                return bytes
            }
            val dexEntries = ZipFile(input).use { zip ->
                zip.entries().asSequence().map { it.name }.filter { DEX_ENTRY.matches(it) }.toList()
            }
            return when (dexEntries.size) {
                0 -> throw DexConversionException("${input.name} has no classes.dex")
                1 -> ZipFile(input).use { zip -> zip.getInputStream(zip.getEntry(dexEntries.single())).use { it.readBytes() } }
                else -> bytes
            }
        }
    }
}
