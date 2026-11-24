package eu.kanade.presentation.more.storage

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.hippo.unifile.UniFile
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

// KMK -->
internal const val DEFAULT_STORAGE_FOLDER_NAME = "Komikku"

/**
 * Marker that a folder is managed by Komikku. It travels with the folder when backed up, so the
 * same folder is recognised again on another device or install.
 */
internal const val STORAGE_MARKER_FILE = ".komikku"

internal fun String.toSafeFolderName(): String = trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")

/**
 * URI of the Documents folder, used as the starting point of the folder picker. The folder name is
 * encoded so reserved characters such as `#` or `%` cannot break the URI.
 */
internal fun documentsFolderUri(folderName: String? = null): Uri {
    val path = if (folderName.isNullOrEmpty()) {
        "primary:Documents"
    } else {
        "primary:Documents/${Uri.encode(folderName)}"
    }
    return "content://com.android.externalstorage.documents/document/$path".toUri()
}

/** URI tree of Documents, the only form accepted by [DocumentFile.fromTreeUri]. */
internal fun documentsTreeUri(): Uri =
    "content://com.android.externalstorage.documents/tree/primary%3ADocuments".toUri()

/** URI tree of a folder directly inside Documents, accepted by [DocumentFile.fromTreeUri]. */
internal fun documentsFolderTreeUri(folderName: String): Uri =
    "content://com.android.externalstorage.documents/tree/primary%3ADocuments%2F${Uri.encode(folderName)}".toUri()

/** URI document of a folder directly inside Documents, accepted by [DocumentFile.fromSingleUri]. */
internal fun documentsFolderDocumentUri(folderName: String): Uri =
    "content://com.android.externalstorage.documents/document/primary:Documents/${Uri.encode(folderName)}".toUri()

/**
 * Document id of a folder directly inside Documents. On external storage the id is formed from the
 * volume and the relative path, which is the only form a document URI can carry.
 */
internal fun documentIdFor(folderName: String): String =
    "primary:Documents/${Uri.encode(folderName)}"

/**
 * The folder that backs a stored URI. A persisted grant held on a renamed folder was granted before
 * the name changed, so the name in the stored URI is stale; the grant document is the reliable
 * source and it resolves to the folder wherever it moved to.
 */
internal fun Context.folderBehind(storedUri: String): DocumentFile? {
    if (storedUri.isEmpty()) return null

    // The grant document works for tree and document URIs alike
    runCatching { DocumentFile.fromSingleUri(this, storedUri.toUri()) }
        .getOrNull()
        ?.takeIf { it.isDirectory }
        ?.let { target ->
            this.logcat { "Grant document resolved: '${target.name}' at ${target.uri}" }
            // A rename keeps the document id but the folder may still be reachable under its name,
            // so the name from the grant is tried first and the old name is kept as a fallback
            target.name?.let { name ->
                documentsFolder(name)?.takeIf { it.isDirectory }?.let { return it }
            }
            return target
        }

    // Fallback for providers that only expose folders through their current path
    this.logcat(LogPriority.WARN) { "Grant document unusable for $storedUri, falling back to its name" }
    val name = UniFile.fromUri(this, storedUri.toUri())?.name ?: return null
    return documentsFolder(name)?.takeIf { it.isDirectory }
}

/**
 * True if the folder is managed by Komikku. Only the marker decides this, so a folder the user
 * picked by hand is never adopted into the automatic path just because of its name or location.
 */
internal fun UniFile.hasStorageMarker(): Boolean =
    exists() && isDirectory && findFile(STORAGE_MARKER_FILE)?.isFile == true

/** True if this folder carries the marker, meaning it belongs to the automatic path. */
internal fun UniFile.isAutoStorageFolder(): Boolean = hasStorageMarker()

/** True if the document holds the Komikku marker. */
private fun DocumentFile.hasStorageMarker(): Boolean =
    isDirectory && findFile(STORAGE_MARKER_FILE)?.isFile == true

internal fun Context.documentsTree(): DocumentFile? =
    runCatching { DocumentFile.fromTreeUri(this, documentsTreeUri()) }.getOrNull()

/**
 * Opens a folder directly inside Documents. A tree URI is preferred because it matches how the
 * picker grants access, with the single document URI as a fallback for providers that reject it.
 */
internal fun Context.documentsFolder(folderName: String): DocumentFile? {
    val tree = runCatching { DocumentFile.fromTreeUri(this, documentsFolderTreeUri(folderName)) }
        .getOrNull()
        ?.takeIf { it.isDirectory }
    if (tree != null) return tree
    return runCatching { DocumentFile.fromSingleUri(this, documentsFolderDocumentUri(folderName)) }
        .getOrNull()
        ?.takeIf { it.isDirectory }
}

/** True if the automatic folder exists and carries the marker, so it can be reused as is. */
internal fun Context.hasAutoFolderMarker(): Boolean =
    documentsFolder(DEFAULT_STORAGE_FOLDER_NAME)?.hasStorageMarker() == true

/**
 * Called right after the picker returned a folder. The grant is fresh at this point, so it is the
 * only reliable moment to write the marker into a folder the user just allowed.
 */
internal fun adoptPickedFolder(context: Context, uri: Uri) {
    val folder = UniFile.fromUri(context, uri) ?: return
    val name = folder.name ?: return
    if (name != DEFAULT_STORAGE_FOLDER_NAME) return
    context.documentsFolder(name)?.writeStorageMarker()
}

/**
 * Writes the marker into the folder in use. It is written through the folder itself, because the
 * granted permission usually covers that folder only, not the whole Documents tree.
 */
internal fun ensureStorageMarker(context: Context, folder: UniFile) {
    if (!folder.exists() || !folder.isDirectory) return
    if (folder.findFile(STORAGE_MARKER_FILE)?.isFile == true) return

    // Tree and document URIs must be opened with different APIs; the wrong one throws
    val target = runCatching {
        val uri = folder.uri
        if (DocumentsContract.isTreeUri(uri)) {
            DocumentFile.fromTreeUri(context, uri)
        } else {
            DocumentFile.fromSingleUri(context, uri)
        }
    }.getOrNull()

    if (target != null) {
        target.writeStorageMarker()
        if (target.findFile(STORAGE_MARKER_FILE)?.isFile == true) return
    }

    // Fallback through the Documents tree, only when its contents are accessible
    val name = folder.name ?: return
    documentFileInDocuments(context, name)?.writeStorageMarker()
}

/**
 * Writes the hidden `.komikku` marker file. Some providers append their own extension, so it is
 * created with an empty MIME type to keep the name unchanged.
 */
private fun DocumentFile.writeStorageMarker(): Boolean = runCatching {
    if (findFile(STORAGE_MARKER_FILE)?.isFile == true) return@runCatching true
    val created = createFile("application/octet-stream", STORAGE_MARKER_FILE)
    if (created == null) false else hasMarkerAfterWrite(created)
}.onFailure { logcat(LogPriority.WARN, it) { "Failed to write storage marker" } }
    .getOrDefault(false)

/**
 * Confirms the marker is there after writing. Some providers do not list a freshly created file
 * right away, so a missing entry is not treated as a failed write.
 */
private fun DocumentFile.hasMarkerAfterWrite(created: DocumentFile): Boolean =
    created.exists() || findFile(STORAGE_MARKER_FILE)?.isFile == true

/**
 * Finds a folder directly inside Documents, either by listing the Documents tree when it is
 * readable or by opening the folder URI directly. Null means the folder is missing or unavailable.
 */
private fun documentFileInDocuments(context: Context, folderName: String): DocumentFile? =
    context.documentsTree()?.findFile(folderName) ?: context.documentsFolder(folderName)

/**
 * Finds the automatic folder that already exists. The default-name folder wins when it carries
 * the marker, then any other marked folder that is reachable. Null means no managed folder was
 * found, so the caller may create one.
 */
internal fun findExistingAutoFolder(context: Context): DocumentFile? {
    val tree = context.documentsTree()
    // The provider can throw when the contents cannot be read
    val children = tree?.let { documents ->
        runCatching { documents.listFiles() }
            .onFailure { context.logcat(LogPriority.WARN, it) { "Failed to list Documents" } }
            .getOrNull()
            ?.filter { it.isDirectory }
    }.orEmpty()

    // The default-name folder wins, then any other marked folder
    val named = children.firstOrNull { it.name == DEFAULT_STORAGE_FOLDER_NAME && it.hasStorageMarker() }
    if (named != null) return named
    children.firstOrNull { it.hasStorageMarker() }?.let { return it }

    // Renamed folders are unreachable by listing, so the default name is opened directly
    val fallback = context.documentsFolder(DEFAULT_STORAGE_FOLDER_NAME)
    return fallback?.takeIf { it.hasStorageMarker() }
}

/**
 * Finds the managed folder to reuse when switching back to the automatic path, checking the stored
 * folder first because its grant is always available. Returns null when a new folder has to be
 * created through the picker.
 */
internal fun findReusableAutoFolder(context: Context, storedUri: String): DocumentFile? {
    findExistingAutoFolder(context)?.let { return it }

    // The stored folder may already carry the marker while the Documents tree cannot be listed,
    // so it is inspected through the grant the app holds
    val stored = storedFolder(context, storedUri)
    return stored?.takeIf { it.name == DEFAULT_STORAGE_FOLDER_NAME || it.hasStorageMarker() }
}

/** The stored folder itself, when the URI still resolves to a readable folder. */
internal fun storedFolder(context: Context, storedUri: String): DocumentFile? {
    if (storedUri.isEmpty()) return null
    return context.folderBehind(storedUri)
}

/**
 * Returns the automatic folder in use: a marked folder when one exists, or a newly created
 * `Documents/Komikku` that gets its marker right away. Null means creation failed.
 */
internal fun ensureAutoFolder(context: Context): DocumentFile? {
    findExistingAutoFolder(context)?.let { return it }

    // The Documents tree is usually not writable without permission, so the result is checked
    val created = runCatching { context.documentsTree()?.createDirectory(DEFAULT_STORAGE_FOLDER_NAME) }
        .onFailure { context.logcat(LogPriority.WARN, it) { "Failed to create auto folder" } }
        .getOrNull()
        ?.takeIf { it.isDirectory }
        ?: openCreatedDocumentsFolder(context)
        ?: run {
            context.logcat(LogPriority.WARN) { "Could not create $DEFAULT_STORAGE_FOLDER_NAME" }
            return null
        }
    context.logcat { "Created $DEFAULT_STORAGE_FOLDER_NAME at ${created.uri}" }

    // The folder is opened again through its own URI, because the Documents tree may have been the
    // read-only form and only the folder itself can receive the marker file
    val target = context.documentsFolder(DEFAULT_STORAGE_FOLDER_NAME)
        ?.takeIf { it.isDirectory }
        ?: created
    target.writeStorageMarker()
    if (!target.hasStorageMarker()) {
        context.logcat(LogPriority.WARN) { "Automatic folder was created without its marker" }
    }
    return target
}

/**
 * True if the folder at the URI is the automatic folder but has no marker yet. Such a folder is
 * offered for adoption when the user grants access to it through the picker, because that is the
 * only moment the marker can be written on Android 11 and newer.
 */
internal fun Context.autoFolderNeedsMarker(uri: Uri): Boolean {
    val folder = UniFile.fromUri(this, uri) ?: return false
    val name = folder.name ?: return false
    return name == DEFAULT_STORAGE_FOLDER_NAME && !folder.hasStorageMarker()
}

/** Opens `Documents/Komikku` after it was created through MediaStore. */
private fun openCreatedDocumentsFolder(context: Context): DocumentFile? =
    if (createFolderViaMediaStore(context, DEFAULT_STORAGE_FOLDER_NAME)) {
        context.documentsFolder(DEFAULT_STORAGE_FOLDER_NAME)
    } else {
        null
    }

/**
 * Renames the folder at the URI in use. Returns the folder at its new URI when the rename
 * succeeded, so the caller can store that URI, or null so it can create a new folder instead.
 */
internal fun renameStorageFolder(context: Context, currentUri: String, newName: String): DocumentFile? {
    val cleanNew = newName.toSafeFolderName()
    if (cleanNew.isEmpty()) {
        context.logcat(LogPriority.WARN) { "Rename skipped: empty name after sanitizing '$newName'" }
        return null
    }

    val current = UniFile.fromUri(context, currentUri.toUri())
    if (current == null) {
        context.logcat(LogPriority.WARN) { "Rename skipped: stored URI does not resolve ($currentUri)" }
        return null
    }
    context.logcat { "Renaming '${current.name}' to '$cleanNew' at $currentUri" }

    if (current.renameTo(cleanNew) != true) {
        context.logcat(LogPriority.WARN) { "Provider rejected renaming '${current.name}' to '$cleanNew'" }
        return null
    }

    // The grant the app already holds still points at the same document, which is now the folder
    // under its new name. Reading it back is the only way to learn the URI the provider assigned
    val renamed = context.folderBehind(currentUri)
    if (renamed == null) {
        context.logcat(LogPriority.WARN) { "Renamed to '$cleanNew' but the folder could not be reopened" }
        return null
    }
    context.logcat { "Rename succeeded: '$cleanNew' now at ${renamed.uri}" }

    context.persistFolderAccess(renamed.uri)
    val marked = renamed.writeStorageMarker()
    context.logcat { "Marker after rename: $marked" }
    return renamed
}

/** Persists read and write access to a folder, ignoring providers that refuse it. */
private fun Context.persistFolderAccess(uri: Uri) {
    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    runCatching { contentResolver.takePersistableUriPermission(uri, flags) }
        .onFailure { logcat(LogPriority.WARN, it) { "Could not persist access after rename" } }
}

/** Creates a folder without the marker, for folders the user manages themselves. */
internal fun createFolderWithoutMarker(context: Context, folderName: String): Boolean =
    createFolderInDocuments(context, folderName, writeMarker = false)

/**
 * Creates `<Documents>/<name>` together with its marker when missing. Returns true when the
 * folder exists at the end, whether newly created or already present.
 */
internal fun createFolderInDocuments(
    context: Context,
    folderName: String,
    // The marker is only for folders Komikku manages, not for user-picked folders
    writeMarker: Boolean = true,
): Boolean {
    val cleanName = folderName.toSafeFolderName()
    if (cleanName.isEmpty()) return false

    // Every provider call is wrapped, because permission can be revoked at any time
    val documents = context.documentsTree()
    if (documents != null) {
        val existing = runCatching { documents.findFile(cleanName) }
            .onFailure { context.logcat(LogPriority.WARN, it) { "Failed to look up $cleanName" } }
            .getOrNull()
        if (existing?.isDirectory == true) {
            // An existing folder is reused and its marker is added when missing
            context.logcat { "Folder $cleanName already exists, reusing it" }
            if (writeMarker) existing.writeStorageMarker()
            return true
        }

        val created = runCatching { documents.createDirectory(cleanName) }
            .onFailure { context.logcat(LogPriority.WARN, it) { "Failed to create $cleanName" } }
            .getOrNull()
        if (created?.isDirectory == true) {
            // An object returned by the tree can exist without the folder being reachable, so the
            // result is confirmed through the folder's own URI before it is reported as created
            val folder = context.documentsFolder(cleanName) ?: created
            context.logcat { "Created folder $cleanName at ${folder.uri}" }
            if (writeMarker) folder.writeStorageMarker()
            return true
        }
    }

    // Some devices reject createDirectory, so MediaStore is tried instead
    return createFolderViaMediaStore(context, cleanName)
}

private fun createFolderViaMediaStore(context: Context, cleanName: String): Boolean {
    // Older Android versions have no RELATIVE_PATH, so that fallback cannot work there
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false

    val contentUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
    val relativePath = "${Environment.DIRECTORY_DOCUMENTS}/$cleanName/"

    // The name is escaped so a literal % or _ cannot match a different folder
    val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? ESCAPE '\\'"
    val selectionArgs = arrayOf("${relativePath.escapeLikeWildcards()}%")

    try {
        context.contentResolver.query(
            contentUri,
            arrayOf(MediaStore.MediaColumns._ID),
            selection,
            selectionArgs,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) return true
        }
    } catch (e: Exception) {
        context.logcat(LogPriority.WARN, e) { "Failed to check existing folder" }
    }

    return try {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, ".nomedia")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val created = context.contentResolver.insert(contentUri, values)
        if (created != null) {
            context.contentResolver.delete(created, null, null)
            true
        } else {
            false
        }
    } catch (e: Exception) {
        context.logcat(LogPriority.WARN, e) { "Failed to prepare folder" }
        false
    }
}

/** Escapes the characters a SQL `LIKE` pattern treats as wildcards. */
private fun String.escapeLikeWildcards(): String =
    replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

// KMK <--
