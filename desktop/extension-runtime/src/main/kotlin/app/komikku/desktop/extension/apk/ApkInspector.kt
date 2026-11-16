package app.komikku.desktop.extension.apk

import java.io.File
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

/** What [ApkInspector] found in an APK file. */
class ApkInfo(
    val file: File,
    /** Lowercase hex SHA-256 of the whole APK file (cache key of the converted jar). */
    val sha256: String,
    val manifest: ApkManifest,
    /** Application label: the literal label, or the `@string` it references (default configuration). */
    val label: String?,
    val signatures: ApkSignatureResult,
    /** Bytes of the highest-density bitmap launcher icon (PNG / WebP / JPEG), if any. */
    val icon: ByteArray?,
)

/** Reads an APK file: manifest, resources (label, icon), signatures and content hash. */
object ApkInspector {

    private const val MANIFEST = "AndroidManifest.xml"
    private const val RESOURCES = "resources.arsc"
    private val BITMAP_EXTENSIONS = listOf(".png", ".webp", ".jpg", ".jpeg")

    /** @throws BinaryResourceException / [java.io.IOException] for files that are not readable APKs */
    fun inspect(file: File): ApkInfo {
        val (manifest, label, icon) = ZipFile(file).use { zip ->
            val manifestEntry = zip.getEntry(MANIFEST) ?: throw BinaryResourceException("APK without $MANIFEST")
            val manifest = ApkManifest.parse(zip.getInputStream(manifestEntry).use { it.readBytes() })
            val resources = zip.getEntry(RESOURCES)?.let { entry ->
                runCatching { ResourceTable.parse(zip.getInputStream(entry).use { it.readBytes() }) }.getOrNull()
            }
            val label = manifest.label ?: manifest.labelResId?.let { resources?.getString(it) }
            val icon = manifest.iconResId?.let { id ->
                resources?.getFilePaths(id)
                    ?.map { (path, _) -> path }
                    ?.firstOrNull { path -> BITMAP_EXTENSIONS.any { path.endsWith(it, ignoreCase = true) } }
                    ?.let { path -> zip.getEntry(path)?.let { zip.getInputStream(it).use { input -> input.readBytes() } } }
            }
            Triple(manifest, label, icon)
        }
        return ApkInfo(
            file = file,
            sha256 = sha256(file),
            manifest = manifest,
            label = label,
            signatures = ApkSignatures.read(file),
            icon = icon,
        )
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        DigestInputStream(file.inputStream().buffered(), digest).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (input.read(buffer) != -1) Unit
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
