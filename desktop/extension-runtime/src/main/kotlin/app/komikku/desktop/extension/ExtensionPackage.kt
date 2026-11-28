package app.komikku.desktop.extension

import app.komikku.desktop.extension.apk.ApkInfo
import app.komikku.desktop.extension.apk.ApkSignatureResult

/**
 * The extension contract of the APK manifest, as read by the Android app's `ExtensionLoader`
 * (`app/src/main/java/eu/kanade/tachiyomi/extension/util/ExtensionLoader.kt`). The constants are
 * private there, so they are duplicated here: **keep both in sync** when the supported extension lib
 * versions or metadata keys change.
 */
object ExtensionContract {
    const val EXTENSION_FEATURE = "tachiyomi.extension"
    const val METADATA_SOURCE_CLASS = "tachiyomi.extension.class"
    const val METADATA_SOURCE_FACTORY = "tachiyomi.extension.factory"
    const val METADATA_NSFW = "tachiyomi.extension.nsfw"
    const val METADATA_NAME = "tachiyomix.name"
    const val METADATA_EXTENSION_LIB = "tachiyomix.extensionLib"
    const val METADATA_CONTENT_WARNING = "tachiyomix.contentWarning"

    val SUPPORTED_LIB_VERSIONS = listOf(1.4, 1.6)

    /** Label prefix the extension build adds to the application label. */
    const val LABEL_PREFIX = "Tachiyomi: "
}

/**
 * An APK that passed the same checks as `ExtensionLoader.loadExtension` up to the trust decision:
 * extension feature, version name, supported lib version, signed. Trust, NSFW policy and class
 * loading are the caller's (see [ExtensionRuntime]).
 */
data class ExtensionPackage(
    val apk: ApkInfo,
    val name: String,
    val pkgName: String,
    val versionName: String,
    val versionCode: Long,
    val libVersion: Double,
    val isNsfw: Boolean,
    /** Fully qualified `Source` / `SourceFactory` class names (`tachiyomi.extension.class`). */
    val sourceClassNames: List<String>,
    val pkgFactory: String?,
    /** SHA-256 of the signing certificates; the last one is the extension's `signatureHash`. */
    val signatures: List<String>,
) {
    val signatureHash: String get() = signatures.last()

    companion object {
        /** Validates [apk] like `ExtensionLoader`; see [ExtensionPackageResult]. */
        fun from(apk: ApkInfo): ExtensionPackageResult {
            val manifest = apk.manifest
            val metaData = manifest.metaData
            val pkgName = manifest.packageName

            // Android's package manager would refuse it; on desktop it names the extension's files
            if (!ExtensionPaths.isValidPackageName(pkgName)) {
                return ExtensionPackageResult.Rejected("'$pkgName' is not a valid package name")
            }

            if (ExtensionContract.EXTENSION_FEATURE !in manifest.usesFeatures) {
                return ExtensionPackageResult.Rejected("$pkgName is not an extension (no ${ExtensionContract.EXTENSION_FEATURE} feature)")
            }

            val name = metaData.getString(ExtensionContract.METADATA_NAME)
                ?: (apk.label ?: pkgName).substringAfter(ExtensionContract.LABEL_PREFIX)
            val versionName = manifest.versionName
            if (versionName.isNullOrEmpty()) {
                return ExtensionPackageResult.Rejected("Missing versionName for extension $name")
            }

            // Same derivation as ExtensionLoader: the float meta-data value, else "<lib>.<n>" version names
            val libVersion = metaData.getFloat(ExtensionContract.METADATA_EXTENSION_LIB)
                .takeUnless { it == 0.0f }
                ?.toString()
                ?.toDouble()
                ?: versionName.substringBeforeLast('.').toDoubleOrNull()
            if (libVersion == null || libVersion !in ExtensionContract.SUPPORTED_LIB_VERSIONS) {
                return ExtensionPackageResult.Rejected(
                    "Lib version is $libVersion, while only version(s) " +
                        "${ExtensionContract.SUPPORTED_LIB_VERSIONS.joinToString()} are supported",
                )
            }

            val signatures = when (val result = apk.signatures) {
                is ApkSignatureResult.Signed -> result.sha256
                ApkSignatureResult.Unsigned -> return ExtensionPackageResult.Rejected("Package $pkgName isn't signed")
                is ApkSignatureResult.Invalid ->
                    return ExtensionPackageResult.Rejected("Package $pkgName has an invalid signature: ${result.reason}")
            }

            val sourceClasses = metaData.getString(ExtensionContract.METADATA_SOURCE_CLASS)
                ?: return ExtensionPackageResult.Rejected("Package $pkgName declares no source class")

            return ExtensionPackageResult.Valid(
                ExtensionPackage(
                    apk = apk,
                    name = name,
                    pkgName = pkgName,
                    versionName = versionName,
                    versionCode = manifest.versionCode,
                    libVersion = libVersion,
                    isNsfw = metaData.getInt(ExtensionContract.METADATA_CONTENT_WARNING) > 0 ||
                        metaData.getInt(ExtensionContract.METADATA_NSFW) == 1,
                    sourceClassNames = sourceClassNames(pkgName, sourceClasses),
                    pkgFactory = metaData.getString(ExtensionContract.METADATA_SOURCE_FACTORY),
                    signatures = signatures,
                ),
            )
        }

        /** Splits `tachiyomi.extension.class` on `;` and expands names starting with `.` with the package. */
        fun sourceClassNames(pkgName: String, value: String): List<String> =
            value.split(";")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { if (it.startsWith(".")) pkgName + it else it }
    }
}

sealed interface ExtensionPackageResult {
    data class Valid(val extension: ExtensionPackage) : ExtensionPackageResult
    data class Rejected(val reason: String) : ExtensionPackageResult
}
