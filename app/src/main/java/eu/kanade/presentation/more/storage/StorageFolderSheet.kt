package eu.kanade.presentation.more.storage

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.hippo.unifile.UniFile
import dev.icerock.moko.resources.StringResource
import kotlinx.coroutines.delay
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.storage.displayablePath
import tachiyomi.domain.storage.service.StorageManager.Companion.isAccessibleDirectory
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

// KMK -->

// Marker values for the path waiting for confirmation in the storage location dialog
private const val SWITCH_MANUAL = "manual"
private const val SWITCH_AUTOMATIC = "automatic"

/** How long the short notice stays before the folder picker opens. */
internal const val NOTICE_DURATION_MS = 5_000L

/**
 * Stores the folder a successful rename moved to. The provider changes the document URI on rename,
 * so keeping the old one would leave the storage location pointing at a folder that no longer
 * exists once the follow-up picker is dismissed.
 */
internal fun storeRenamedFolderUri(storagePref: Preference<String>, folder: DocumentFile) {
    storagePref.set("")
    storagePref.set(folder.uri.toString())
}

// Where the folder picker starts: root, Documents, or straight inside one folder
internal sealed interface PickStart {
    data object Root : PickStart
    data object Documents : PickStart
    data class InFolder(val folderName: String) : PickStart
    /**
     * A folder reached by its own URI. Used after a rename, because the provider decides the new
     * document URI and rebuilding the hint from the name can point at the wrong place.
     */
    data class AtUri(val uri: Uri) : PickStart
}

/**
 * Short notice shown before the folder picker opens. A null [next] means nothing follows,
 * for example after a failure.
 */
private data class SheetNotice(
    val title: StringResource,
    val message: StringResource,
    // Arguments for the message, usually the folder name in use
    val args: List<Any> = emptyList(),
    val kind: NoticeKind,
    val next: PickStart? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StorageFolderSheet(
    storagePref: Preference<String>,
    onDismiss: () -> Unit,
    onPickManually: (startAt: PickStart) -> Unit,
) {
    val context = LocalContext.current
    val storageDir by storagePref.collectAsState()

    // Only a readable and writable folder counts as present. A stored URI whose access was revoked
    // still resolves, so it must not be treated as a manual folder the user can keep using
    val currentFolder = remember(storageDir) {
        UniFile.fromUri(context, storageDir.toUri())?.takeIf { it.isAccessibleDirectory }
    }
    val currentName = currentFolder?.name.orEmpty()
    val currentPath = remember(currentFolder) {
        currentFolder?.displayablePath.orEmpty().toShortStoragePath()
    }

    var folderName by remember(currentName) { mutableStateOf(currentName) }
    var isEditing by rememberSaveable { mutableStateOf(false) }
    // Path waiting for confirmation; null means no dialog is shown
    var pendingSwitch by rememberSaveable { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<SheetNotice?>(null) }

    val cleanName = folderName.toSafeFolderName()
    // A folder that is not accessible is treated as no folder at all, so the sheet offers the
    // automatic path instead of a manual action that would reopen the broken location
    val folderAvailable = currentFolder != null
    val automatic = remember(currentFolder) { currentFolder?.hasStorageMarker() == true }
    val canRename = folderAvailable && cleanName.isNotEmpty() && cleanName != currentName

    // Older automatic folders have no marker yet, so one is written once detected
    LaunchedEffect(automatic, storageDir) {
        if (automatic) {
            currentFolder?.let { ensureStorageMarker(context, it) }
        }
    }

    // The countdown is cancelled once OK is pressed, so the folder picker never opens twice
    LaunchedEffect(notice) {
        val current = notice ?: return@LaunchedEffect
        delay(NOTICE_DURATION_MS)
        // The action runs before the state is cleared, so a cancellation cannot cut it short
        current.next?.let(onPickManually)
        notice = null
    }

    // Closes the notice early through OK, without waiting for the countdown to end
    val dismissNotice: () -> Unit = {
        val current = notice
        // Guard: a second press (double tap or double TalkBack activation) is ignored
        if (current != null) {
            notice = null
            current.next?.let(onPickManually)
        }
    }

    // The new name is applied as soon as the input is confirmed, without another button
    val applyRename: () -> Unit = {
        isEditing = false
        if (canRename) {
            val result = context.renameStorageFolder(storageDir, cleanName)
            if (result.renamed) {
                // The renamed folder has a new URI, so the stored location has to follow it
                result.folder?.let { storeRenamedFolderUri(storagePref, it) }
                notice = result.notice
            } else {
                // The old name is restored because the folder on storage did not change
                folderName = currentName
                notice = result.notice
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        SheetContent(
            currentName = folderName,
            currentPath = currentPath,
            automatic = automatic,
            folderAvailable = folderAvailable,
            isEditing = isEditing,
            onFolderNameChange = { folderName = it },
            onStartEditing = { isEditing = true },
            onFinishEditing = applyRename,
            canRename = canRename,
            onPendingSwitch = { pendingSwitch = it },
            // Without a working folder there is nothing to confirm, so both paths run directly
            onUseAutomatic = {
                notice = context.switchToAutomaticFolder(storageDir)
            },
            onPickManually = { onPickManually(PickStart.Root) },
        )
    }

    // Its own window in the center of the screen, not stacked inside the bottom sheet
    notice?.let {
        StorageNoticePopup(
            title = it.title,
            message = it.message,
            formatArgs = it.args,
            kind = it.kind,
            durationMs = NOTICE_DURATION_MS,
            onConfirm = dismissNotice,
        )
    }

    // Switching paths is confirmed first so it cannot be triggered by accident
    if (pendingSwitch != null) {
        StorageSwitchDialog(
            switchToAutomatic = pendingSwitch == SWITCH_AUTOMATIC,
            onDismiss = { pendingSwitch = null },
            onConfirm = {
                val target = pendingSwitch
                pendingSwitch = null
                // Manual opens the picker directly; automatic creates the folder first so it can be hinted
                notice = if (target == SWITCH_MANUAL) {
                    onPickManually(PickStart.Root)
                    null
                } else {
                    context.switchToAutomaticFolder(storageDir)
                }
            },
        )
    }
}

/** Bottom sheet content: title, folder summary, and one alternative path. */
@Composable
private fun SheetContent(
    currentName: String,
    currentPath: String,
    automatic: Boolean,
    folderAvailable: Boolean,
    isEditing: Boolean,
    onFolderNameChange: (String) -> Unit,
    onStartEditing: () -> Unit,
    onFinishEditing: () -> Unit,
    canRename: Boolean,
    onPendingSwitch: (String) -> Unit,
    onUseAutomatic: () -> Unit,
    onPickManually: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
    ) {
        Text(
            text = stringResource(MR.strings.pref_storage_location),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(
                when {
                    !folderAvailable -> KMR.strings.settings_storage_folder_info_missing
                    automatic -> KMR.strings.settings_storage_folder_info
                    else -> KMR.strings.settings_storage_folder_info_manual
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(16.dp))
        StorageFolderCard(
            label = stringResource(KMR.strings.settings_storage_current_folder),
            name = currentName,
            path = currentPath,
            mode = stringResource(
                if (automatic) {
                    KMR.strings.settings_storage_mode_automatic
                } else {
                    KMR.strings.settings_storage_mode_manual
                },
            ),
            onStartEditing = if (automatic) onStartEditing else null,
            isEditing = isEditing,
            onFolderNameChange = onFolderNameChange,
            onFinishEditing = onFinishEditing,
            canApply = canRename,
        )

        Spacer(modifier = Modifier.height(8.dp))
        // Exactly one alternative is offered, so the same action never appears twice
        when {
            // A managed folder is in use: the way out is picking a location by hand
            automatic -> StorageActionRow(
                icon = Icons.Rounded.FolderOpen,
                title = stringResource(KMR.strings.settings_storage_pick_manually),
                supporting = stringResource(KMR.strings.settings_storage_pick_manually_info),
                onClick = { onPendingSwitch(SWITCH_MANUAL) },
            )
            // A manual folder is in use: the way out is returning to the managed folder
            folderAvailable -> StorageActionRow(
                icon = Icons.Rounded.CreateNewFolder,
                title = stringResource(KMR.strings.settings_storage_use_automatic),
                supporting = stringResource(KMR.strings.settings_storage_use_automatic_info),
                onClick = { onPendingSwitch(SWITCH_AUTOMATIC) },
            )
            // Nothing is in use: both paths are offered, because no folder would be replaced
            else -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                StorageActionRow(
                    icon = Icons.Rounded.CreateNewFolder,
                    title = stringResource(KMR.strings.settings_storage_use_automatic),
                    supporting = stringResource(KMR.strings.settings_storage_use_automatic_info),
                    onClick = onUseAutomatic,
                )
                StorageActionRow(
                    icon = Icons.Rounded.FolderOpen,
                    title = stringResource(KMR.strings.settings_storage_pick_manually),
                    supporting = stringResource(KMR.strings.settings_storage_pick_manually_info),
                    onClick = onPickManually,
                )
            }
        }
    }
}

/**
 * Renames the folder in use, including the case where the provider rejects the rename and a new
 * folder is created instead. A false [renamed] means the folder on storage did not change.
 */
private data class RenameResult(
    val renamed: Boolean,
    val notice: SheetNotice,
    // Folder at its new URI, null when the rename failed
    val folder: DocumentFile? = null,
)

private fun Context.renameStorageFolder(storageDir: String, cleanName: String): RenameResult {
    renameStorageFolder(this, storageDir, cleanName)?.let { renamed ->
        return RenameResult(
            renamed = true,
            folder = renamed,
            notice = SheetNotice(
                title = KMR.strings.settings_storage_renamed,
                message = KMR.strings.settings_storage_renamed_desc,
                args = listOf(cleanName),
                kind = NoticeKind.Success,
                // The picker opens at the new name directly: the document URI of a renamed folder is
                // not a tree URI, so hinting it would send the picker elsewhere
                next = PickStart.InFolder(cleanName),
            ),
        )
    }

    // The provider rejected the rename, so a new folder is used instead. It is opened through its
    // own URI, which is also the hint the picker gets, so an empty folder cannot be mistaken for
    // the renamed one
    val created = createFolderInDocuments(this, cleanName)
    val createdFolder = if (created) documentsFolder(cleanName) else null
    return if (created) {
        RenameResult(
            renamed = true,
            folder = createdFolder,
            notice = SheetNotice(
                title = KMR.strings.settings_storage_notice_recreated_title,
                message = KMR.strings.settings_storage_notice_recreated_desc,
                args = listOf(cleanName),
                kind = NoticeKind.Warning,
                next = createdFolder?.let { PickStart.AtUri(it.uri) } ?: PickStart.InFolder(cleanName),
            ),
        )
    } else {
        RenameResult(
            renamed = false,
            notice = SheetNotice(
                title = KMR.strings.settings_storage_rename_failed_title,
                message = KMR.strings.settings_storage_rename_failed,
                kind = NoticeKind.Warning,
            ),
        )
    }
}

/**
 * Prepares the automatic folder for reuse. If creation fails, the picker still opens at
 * Documents so a location can be picked by hand.
 */
private fun Context.switchToAutomaticFolder(storageDir: String): SheetNotice {
    // A reused folder is told apart from a newly created one. The stored folder is checked first,
    // because the Documents tree may not be listable while the folder itself is still readable
    val reusable = findReusableAutoFolder(this, storageDir)
    val folder = reusable ?: ensureAutoFolder(this) ?: return SheetNotice(
        title = KMR.strings.settings_storage_create_failed_title,
        message = KMR.strings.settings_storage_create_failed_picking,
        kind = NoticeKind.Warning,
        next = PickStart.Documents,
    )

    val targetName = folder.name ?: DEFAULT_STORAGE_FOLDER_NAME
    return if (reusable != null) {
        SheetNotice(
            title = KMR.strings.storage_notice_folder_exists_title,
            message = KMR.strings.storage_notice_folder_exists_desc,
            args = listOf(targetName),
            kind = NoticeKind.Success,
            next = PickStart.InFolder(targetName),
        )
    } else {
        SheetNotice(
            title = KMR.strings.storage_notice_folder_created_title,
            message = KMR.strings.storage_notice_folder_created_desc,
            args = listOf(targetName),
            kind = NoticeKind.Success,
            next = PickStart.InFolder(targetName),
        )
    }
}

// KMK <--
