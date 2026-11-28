package app.komikku.desktop.smoke

import tachiyomi.core.common.desktop.DesktopDirs
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.util.EnumSet

/**
 * The UI smoke mode's data folder: always a new folder in the system's temporary folder, never one given on the
 * command line, and never the user's data. [refusal] says why a folder must not be used; the smoke mode then exits
 * with [UiSmoke.EXIT_REFUSED] before anything opens the folder.
 */
internal object SmokeDataFolder {

    const val PREFIX = "komikku-ui-smoke-"

    /** A new, empty, owner-only folder in [tempRoot]. */
    fun create(tempRoot: Path = Path.of(System.getProperty("java.io.tmpdir"))): Path {
        val posix = runCatching { Files.getFileStore(tempRoot).supportsFileAttributeView("posix") }.getOrDefault(false)
        return if (posix) {
            val ownerOnly = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE)
            Files.createTempDirectory(tempRoot, PREFIX, PosixFilePermissions.asFileAttribute(ownerOnly))
        } else {
            Files.createTempDirectory(tempRoot, PREFIX)
        }
    }

    /**
     * The folders the smoke mode must never touch: the system's default data folder and a `komikku.dataDir` given on
     * the command line ([givenDataDir]).
     */
    fun protectedFolders(givenDataDir: String? = System.getProperty(DesktopDirs.DATA_DIR_PROPERTY)): List<Path> {
        val default = DesktopDirs.resolveDataDir(
            osName = System.getProperty("os.name").orEmpty(),
            userHome = System.getProperty("user.home").orEmpty(),
            override = null,
            env = System::getenv,
        )
        return listOfNotNull(default, givenDataDir?.takeIf { it.isNotBlank() }?.let(::File)).map(File::toPath)
    }

    /** Why [candidate] must not be the smoke mode's data folder, or `null` when it may. */
    fun refusal(candidate: Path, tempRoot: Path, protectedFolders: List<Path>): String? {
        val folder = candidate.canonical()
        val temp = tempRoot.canonical()
        if (folder == temp || !folder.startsWith(temp)) return "$candidate is not a new folder inside the temporary folder $tempRoot"
        for (protected in protectedFolders) {
            val other = protected.canonical()
            when {
                folder == other -> return "$candidate is the data folder $protected"
                folder.startsWith(other) -> return "$candidate lies inside the data folder $protected"
                other.startsWith(folder) -> return "$candidate contains the data folder $protected"
            }
        }
        if (!Files.isDirectory(folder)) return "$candidate is not a folder"
        val empty = Files.list(folder).use { it.findFirst().isEmpty }
        return if (empty) null else "$candidate is not empty"
    }

    /** The real path of an existing folder (symbolic links resolved, e.g. macOS's `/var`), else the absolute one. */
    private fun Path.canonical(): Path = runCatching { toRealPath() }.getOrElse { toAbsolutePath() }.normalize()
}
