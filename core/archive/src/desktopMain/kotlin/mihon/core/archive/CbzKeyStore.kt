package mihon.core.archive

import tachiyomi.core.common.desktop.DesktopDirs
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.AclEntry
import java.nio.file.attribute.AclEntryPermission
import java.nio.file.attribute.AclEntryType
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermissions
import java.security.SecureRandom
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * KMK: desktop [CbzKeyStore]. Each alias is a random 256-bit AES key in `<dataDir>/keys/<alias>.key`.
 *
 * The directory and files are restricted to the current user: POSIX permissions `rwx------` /
 * `rw-------` (Linux, macOS) or an owner-only ACL (Windows), where the file system supports it.
 * This is weaker than the Android Keystore, where the key cannot be exported: any process running
 * as the user (or anyone with access to the user's files or backups) can read the key and decrypt
 * the stored CBZ / database passwords.
 */
internal actual object CbzKeyStore {

    private const val KEY_SIZE_BYTES = 32
    private const val ALGORITHM = "AES"

    private val directory: File by lazy { DesktopDirs.dataDir.resolve("keys") }

    private val random by lazy { SecureRandom() }

    @Synchronized
    actual fun getKey(alias: String): SecretKey {
        val file = keyFile(alias)
        val bytes = file.takeIf { it.isFile }?.readBytes()
        return if (bytes != null && bytes.size == KEY_SIZE_BYTES) {
            SecretKeySpec(bytes, ALGORITHM).also { bytes.fill(0) }
        } else {
            generateKey(alias)
        }
    }

    @Synchronized
    actual fun generateKey(alias: String): SecretKey {
        val bytes = ByteArray(KEY_SIZE_BYTES).also(random::nextBytes)
        try {
            writePrivate(keyFile(alias).toPath(), bytes)
            return SecretKeySpec(bytes, ALGORITHM)
        } finally {
            bytes.fill(0)
        }
    }

    @Synchronized
    actual fun deleteKey(alias: String) {
        keyFile(alias).delete()
    }

    private fun keyFile(alias: String): File {
        require(alias.isNotEmpty() && alias.all { it.isLetterOrDigit() || it == '_' || it == '-' }) {
            "Invalid key alias: $alias"
        }
        return directory.resolve("$alias.key")
    }

    /** Writes [bytes] to [target] through an owner-only temp file and an atomic rename. */
    private fun writePrivate(target: Path, bytes: ByteArray) {
        val dir = target.parent
        if (!Files.isDirectory(dir)) {
            Files.createDirectories(dir)
            restrictToOwner(dir, directory = true)
        }
        val temp = Files.createTempFile(dir, target.fileName.toString(), ".tmp")
        try {
            restrictToOwner(temp, directory = false)
            Files.newOutputStream(temp).use { out ->
                out.write(bytes)
                out.flush()
                (out as? java.io.FileOutputStream)?.fd?.sync()
            }
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    private fun restrictToOwner(path: Path, directory: Boolean) {
        val posix = Files.getFileAttributeView(path, PosixFileAttributeView::class.java)
        if (posix != null) {
            posix.setPermissions(PosixFilePermissions.fromString(if (directory) "rwx------" else "rw-------"))
            return
        }
        val acl = Files.getFileAttributeView(path, AclFileAttributeView::class.java)
        if (acl != null) {
            val ownerOnly = AclEntry.newBuilder()
                .setType(AclEntryType.ALLOW)
                .setPrincipal(acl.owner)
                .setPermissions(AclEntryPermission.entries.toSet())
                .build()
            acl.acl = listOf(ownerOnly)
            return
        }
        // Neither POSIX nor ACL views (e.g. FAT): best effort through java.io.File.
        val file = path.toFile()
        file.setReadable(false, false)
        file.setWritable(false, false)
        file.setReadable(true, true)
        file.setWritable(true, true)
        if (directory) {
            file.setExecutable(false, false)
            file.setExecutable(true, true)
        }
    }
}
