package app.komikku.desktop.instance

import exh.log.xLogW
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.AclEntry
import java.nio.file.attribute.AclEntryPermission
import java.nio.file.attribute.AclEntryType
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.attribute.UserPrincipal
import java.util.EnumSet

/**
 * KMK: creates files only the current user can read, without extra libraries.
 *
 * - **macOS, Linux** (POSIX file systems): the file is created with mode `rw-------` in one step (the umask can only
 *   take bits away), so it is never readable by others.
 * - **Windows** (ACL file systems): the file is created empty, then its access list is replaced by a single entry that
 *   gives the current user full access; the entries it inherited from the folder are dropped. The user is looked up
 *   qualified (`%USERDOMAIN%\user.name`: a bare name finds a local account of the same name before a domain one), then
 *   by the bare name, else it is the file's owner (for an elevated administrator that is the Administrators group). The list is not marked "protected", so an administrator re-applying inheritance from a
 *   parent folder could add entries back; the default data folder (`%APPDATA%\Komikku`) is per-user anyway. Nothing
 *   is written to the file before its access list is set.
 * - **Anything else**: a plain file; the data folder's own permissions apply (logged).
 */
internal object OwnerOnlyFiles {

    fun create(path: Path) {
        val views = path.fileSystem.supportedFileAttributeViews()
        when {
            "posix" in views -> Files.createFile(path, PosixFilePermissions.asFileAttribute(OWNER_READ_WRITE))
            "acl" in views -> {
                Files.createFile(path)
                restrictAcl(path)
            }
            else -> {
                Files.createFile(path)
                xLogW("Single instance: no owner-only permissions on this file system, relying on the folder's")
            }
        }
    }

    private fun restrictAcl(path: Path) {
        try {
            val view = Files.getFileAttributeView(path, AclFileAttributeView::class.java) ?: return
            val entry = AclEntry.newBuilder()
                .setType(AclEntryType.ALLOW)
                .setPrincipal(currentUser(path))
                .setPermissions(EnumSet.allOf(AclEntryPermission::class.java))
                .build()
            view.acl = listOf(entry)
        } catch (e: Exception) {
            xLogW("Single instance: could not restrict the access list of ${path.fileName}, relying on the folder's", e)
        }
    }

    private fun currentUser(path: Path): UserPrincipal {
        val lookup = path.fileSystem.userPrincipalLookupService
        val name = System.getProperty("user.name")
        val domain = System.getenv("USERDOMAIN")?.takeIf { it.isNotBlank() }
        for (candidate in listOfNotNull(domain?.let { "$it\\$name" }, name)) {
            try {
                return lookup.lookupPrincipalByName(candidate)
            } catch (_: Exception) {
            }
        }
        return Files.getOwner(path)
    }

    private val OWNER_READ_WRITE = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
}
