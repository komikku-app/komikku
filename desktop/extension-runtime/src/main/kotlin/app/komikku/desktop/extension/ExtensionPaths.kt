package app.komikku.desktop.extension

import java.io.File

/**
 * Package names of extensions and the files named after them. A package name comes from the APK's manifest (or a
 * store's index), i.e. from a third party: it is accepted only with the grammar Android's package manager enforces,
 * and every file built from it is resolved and must lie directly in its folder (review M3, finding 3: a manifest
 * package `../../x` made install write and uninstall delete outside the extensions folder).
 */
object ExtensionPaths {

    /** Android's package name grammar (`PackageParser.validateName`): two or more dotted ASCII Java identifiers. */
    private val PACKAGE_NAME = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")

    fun isValidPackageName(name: String): Boolean = PACKAGE_NAME.matches(name)

    /**
     * `<dir>/<pkgName><suffix>`, resolved (symbolic links too). Throws [IllegalArgumentException] when [pkgName] is not
     * a package name or the resolved file is not directly in [dir].
     */
    fun packageFile(dir: File, pkgName: String, suffix: String = ""): File {
        require(isValidPackageName(pkgName)) { "'$pkgName' is not a valid package name" }
        val folder = dir.canonicalFile
        val file = File(folder, pkgName + suffix).canonicalFile
        require(file.parentFile == folder) { "$pkgName$suffix resolves outside $folder" }
        return file
    }

    /** [packageFile], or `null` where it throws. */
    fun packageFileOrNull(dir: File, pkgName: String, suffix: String = ""): File? = try {
        packageFile(dir, pkgName, suffix)
    } catch (_: IllegalArgumentException) {
        null
    }
}
