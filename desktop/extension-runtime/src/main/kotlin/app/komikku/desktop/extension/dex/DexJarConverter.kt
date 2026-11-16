package app.komikku.desktop.extension.dex

import com.googlecode.d2j.Method
import com.googlecode.d2j.dex.BaseDexExceptionHandler
import com.googlecode.d2j.dex.Dex2jar
import com.googlecode.d2j.node.DexMethodNode
import org.objectweb.asm.MethodVisitor
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile

/** Thrown when an APK's dex code cannot be converted at all. */
class DexConversionException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Converts the dex code of an APK (`classes.dex`, `classes2.dex`, …) to a jar of JVM class files
 * with dex2jar (`de.femtopedia.dex2jar:dex-translator`), and caches the result as
 * `<cacheDir>/<apk sha256>.jar`, so every APK version is converted once.
 *
 * Methods dex2jar cannot translate are replaced by a body that throws
 * `RuntimeException("d2j fail translate: …")` (dex2jar's own fallback); they are reported in
 * [Conversion.failedMethods] and logged by the caller.
 */
class DexJarConverter(private val cacheDir: File) {

    /** Result of [jarFor]. */
    data class Conversion(val jar: File, val fromCache: Boolean, val failedMethods: List<String>)

    private val locks = ConcurrentHashMap<String, Any>()

    /** The converted jar for [apk] (whose SHA-256 is [sha256]), converting it if it is not cached. */
    fun jarFor(apk: File, sha256: String): Conversion {
        require(sha256.matches(SHA256_PATTERN)) { "Not a SHA-256: $sha256" }
        val target = File(cacheDir, "$sha256.jar")
        synchronized(locks.getOrPut(sha256) { Any() }) {
            if (target.isFile && target.length() > 0) return Conversion(target, fromCache = true, failedMethods = emptyList())
            cacheDir.mkdirs()
            val temp = File.createTempFile("$sha256-", ".jar.tmp", cacheDir)
            try {
                val failed = convert(apk, temp)
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                return Conversion(target, fromCache = false, failedMethods = failed)
            } finally {
                temp.delete()
            }
        }
    }

    /** Deletes cached jars whose APK hash is not in [keep]. */
    fun prune(keep: Set<String>) {
        cacheDir.listFiles()
            ?.filter { it.isFile && (it.name.endsWith(".jar.tmp") || (it.extension == "jar" && it.nameWithoutExtension !in keep)) }
            ?.forEach { it.delete() }
    }

    companion object {
        private val SHA256_PATTERN = Regex("[0-9a-f]{64}")
        private val DEX_ENTRY = Regex("classes\\d*\\.dex")

        /**
         * Converts [input] — an APK / zip with `classes*.dex` entries, or a bare `.dex` file — to the jar
         * [output]. Returns the methods that could not be translated.
         */
        fun convert(input: File, output: File): List<String> {
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
            try {
                Dex2jar.from(dexBytes)
                    .withExceptionHandler(handler)
                    .reUseReg(false)
                    .topoLogicalSort()
                    .skipDebug(false)
                    .optimizeSynchronized(true)
                    .printIR(false)
                    .noCode(false)
                    .skipExceptions(false)
                    .to(output.toPath())
            } catch (e: DexConversionException) {
                throw e
            } catch (e: Exception) {
                throw DexConversionException("dex2jar failed for ${input.name}: ${e.message}", e)
            }
            if (!output.isFile || output.length() == 0L) throw DexConversionException("dex2jar wrote no classes for ${input.name}")
            return failed
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
