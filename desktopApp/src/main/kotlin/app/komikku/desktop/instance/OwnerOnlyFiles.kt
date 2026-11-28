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
 *   gives the current user (looked up by `user.name`, else the file's owner) full access; the entries it inherited from
 *   the folder are dropped. The list is not marked "protected", so an administrator re-applying inheritance from a
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

    private fun currentUser(path: Path): UserPrincipal = try {
        path.fileSystem.userPrincipalLookupService.lookupPrincipalByName(System.getProperty("user.name"))
    } catch (_: Exception) {
        Files.getOwner(path)
    }

    private val OWNER_READ_WRITE = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
}
