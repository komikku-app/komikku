package app.komikku.desktop.extension.testing

import app.komikku.desktop.extension.testing.AxmlWriter.Attr
import app.komikku.desktop.extension.testing.AxmlWriter.Companion.ATTR_ICON
import app.komikku.desktop.extension.testing.AxmlWriter.Companion.ATTR_LABEL
import app.komikku.desktop.extension.testing.AxmlWriter.Companion.ATTR_NAME
import app.komikku.desktop.extension.testing.AxmlWriter.Companion.ATTR_VALUE
import app.komikku.desktop.extension.testing.AxmlWriter.Companion.ATTR_VERSION_CODE
import app.komikku.desktop.extension.testing.AxmlWriter.Companion.ATTR_VERSION_NAME
import app.komikku.desktop.extension.testing.AxmlWriter.Companion.androidFloat
import app.komikku.desktop.extension.testing.AxmlWriter.Companion.androidInt
import app.komikku.desktop.extension.testing.AxmlWriter.Companion.androidRef
import app.komikku.desktop.extension.testing.AxmlWriter.Companion.androidString
import app.komikku.desktop.extension.testing.AxmlWriter.Companion.plainString
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Builds extension APK fixtures in tests: binary manifest with the extension metadata, optional
 * `resources.arsc` (label + launcher icon), `classes.dex`, extra entries, then signing with apksig.
 */
data class FixtureApk(
    val packageName: String = "eu.kanade.tachiyomi.extension.en.fixture",
    val versionName: String? = "1.4.3",
    val versionCode: Int = 3,
    /** Literal application label; ignored when [labelFromResources] is set. */
    val label: String? = "Tachiyomi: Fixture",
    /** `@string` application label resolved through `resources.arsc`. */
    val labelFromResources: String? = null,
    /** Launcher icon bytes per density, stored as `res/mipmap-<density>/ic_launcher.png`. */
    val icons: Map<Int, ByteArray> = emptyMap(),
    val isExtension: Boolean = true,
    /** `tachiyomi.extension.class` (null = omitted). */
    val sourceClass: String? = ".FixtureSource",
    val factory: String? = null,
    /** `tachiyomix.name` (null = omitted). */
    val extensionName: String? = null,
    /** `tachiyomix.extensionLib` as a float (null = omitted). */
    val extensionLib: Float? = 1.4f,
    val nsfw: Int? = null,
    val contentWarning: Int? = null,
    val dex: ByteArray? = null,
    val extraEntries: Map<String, ByteArray> = emptyMap(),
    /** Write the manifest's string pool as UTF-8 (aapt2's default) or UTF-16. */
    val utf8: Boolean = true,
) {

    fun manifest(resources: ArscWriter? = null): ByteArray {
        val labelAttr = when {
            labelFromResources != null && resources != null ->
                androidRef("label", ATTR_LABEL, resources.add("string", "app_name", labelFromResources))
            label != null -> androidString("label", ATTR_LABEL, label)
            else -> null
        }
        val iconAttr = if (icons.isNotEmpty() && resources != null) {
            var id = 0
            icons.forEach { (density, _) ->
                id = resources.add("mipmap", "ic_launcher", iconPath(density), density)
            }
            androidRef("icon", ATTR_ICON, id)
        } else {
            null
        }
        val metaData = buildList {
            sourceClass?.let { add(meta("tachiyomi.extension.class", androidString("value", ATTR_VALUE, it))) }
            factory?.let { add(meta("tachiyomi.extension.factory", androidString("value", ATTR_VALUE, it))) }
            extensionName?.let { add(meta("tachiyomix.name", androidString("value", ATTR_VALUE, it))) }
            extensionLib?.let { add(meta("tachiyomix.extensionLib", androidFloat("value", ATTR_VALUE, it))) }
            nsfw?.let { add(meta("tachiyomi.extension.nsfw", androidInt("value", ATTR_VALUE, it))) }
            contentWarning?.let { add(meta("tachiyomix.contentWarning", androidInt("value", ATTR_VALUE, it))) }
        }
        val manifestAttributes = buildList {
            add(androidInt("versionCode", ATTR_VERSION_CODE, versionCode))
            versionName?.let { add(androidString("versionName", ATTR_VERSION_NAME, it)) }
            add(plainString("package", packageName))
        }
        val children = buildList {
            add(AxmlWriter.Node("uses-sdk", listOf(androidInt("minSdkVersion", 0x0101020c, 21))))
            if (isExtension) {
                add(AxmlWriter.Node("uses-feature", listOf(androidString("name", ATTR_NAME, "tachiyomi.extension"))))
            }
            add(AxmlWriter.Node("application", listOfNotNull(labelAttr, iconAttr), metaData))
        }
        return AxmlWriter(utf8).write(AxmlWriter.Node("manifest", manifestAttributes, children))
    }

    /** Writes the unsigned APK. */
    fun writeUnsigned(file: File) {
        val resources = if (labelFromResources != null || icons.isNotEmpty()) ArscWriter() else null
        val manifest = manifest(resources)
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putStored("AndroidManifest.xml", manifest)
            resources?.let { zip.putStored("resources.arsc", it.write()) }
            icons.forEach { (density, bytes) -> zip.putStored(iconPath(density), bytes) }
            dex?.let { zip.putStored("classes.dex", it) }
            extraEntries.forEach { (name, bytes) -> zip.putStored(name, bytes) }
        }
    }

    /** Writes the APK signed by [key] with [schemes]. */
    fun writeSigned(file: File, key: TestSigningKey, schemes: TestApkSigner.Schemes = TestApkSigner.Schemes()) {
        val unsigned = File(file.parentFile, file.name + ".unsigned")
        writeUnsigned(unsigned)
        TestApkSigner.sign(unsigned, file, key, schemes)
        unsigned.delete()
    }

    private fun meta(name: String, value: Attr) =
        AxmlWriter.Node("meta-data", listOf(androidString("name", ATTR_NAME, name), value))

    companion object {
        fun iconPath(density: Int) = "res/mipmap-${density}dpi-v4/ic_launcher.png"

        /** Stored (uncompressed) entries keep fixture bytes findable in the APK for tamper tests. */
        private fun ZipOutputStream.putStored(name: String, bytes: ByteArray) {
            val entry = ZipEntry(name).apply {
                method = ZipEntry.STORED
                size = bytes.size.toLong()
                compressedSize = bytes.size.toLong()
                crc = CRC32().apply { update(bytes) }.value
            }
            putNextEntry(entry)
            write(bytes)
            closeEntry()
        }
    }
}
