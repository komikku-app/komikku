package eu.kanade.presentation.more.onboarding

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.hippo.unifile.UniFile
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.more.settings.screen.SettingsDataScreen
import eu.kanade.presentation.more.storage.DEFAULT_STORAGE_FOLDER_NAME
import eu.kanade.presentation.more.storage.FolderPickerHint
import eu.kanade.presentation.more.storage.NOTICE_DURATION_MS
import eu.kanade.presentation.more.storage.NoticeKind
import eu.kanade.presentation.more.storage.OpenDocumentTreeWithHint
import eu.kanade.presentation.more.storage.StorageActionRow
import eu.kanade.presentation.more.storage.StorageFolderCard
import eu.kanade.presentation.more.storage.StorageNoticePopup
import eu.kanade.presentation.more.storage.adoptPickedFolder
import eu.kanade.presentation.more.storage.createFolderInDocuments
import eu.kanade.presentation.more.storage.createFolderWithoutMarker
import eu.kanade.presentation.more.storage.documentsTree
import eu.kanade.presentation.more.storage.ensureAutoFolder
import eu.kanade.presentation.more.storage.ensureStorageMarker
import eu.kanade.presentation.more.storage.findExistingAutoFolder
import eu.kanade.presentation.more.storage.hasStorageMarker
import eu.kanade.presentation.more.storage.isAutoStorageFolder
import eu.kanade.presentation.more.storage.toSafeFolderName
import eu.kanade.presentation.more.storage.toShortStoragePath
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.delay
import logcat.LogPriority
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.storage.displayablePath
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.storage.service.StorageManager.Companion.isAccessibleDirectory
import tachiyomi.domain.storage.service.StoragePreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

internal class StorageStep : OnboardingStep {

    private val storagePref = Injekt.get<StoragePreferences>().baseStorageDirectory()

    private var _isComplete by mutableStateOf(false)

    override val isComplete: Boolean
        get() = _isComplete

    @Composable
    override fun Content() {
        // KMK -->
        OnboardingStorageContent(
            storagePref = storagePref,
            onLocationValid = { _isComplete = it },
        )
        // KMK <--
    }
}

// KMK -->

/** Short notice about a folder action, shown as a popup in the center of the screen. */
private data class OnboardingNotice(
    val title: StringResource,
    val message: StringResource,
    // Arguments for the message, usually the folder name in use
    val args: List<Any> = emptyList(),
    val kind: NoticeKind,
)

@Composable
private fun OnboardingStorageContent(
    storagePref: Preference<String>,
    // Decides whether the next button is enabled, based on the folder currently in use
    onLocationValid: (Boolean) -> Unit,
) {
    val context = LocalContext.current

    var folderName by rememberSaveable { mutableStateOf(DEFAULT_STORAGE_FOLDER_NAME) }
    var isEditing by rememberSaveable { mutableStateOf(false) }
    var isLocked by rememberSaveable { mutableStateOf(false) }
    var notice by remember { mutableStateOf<OnboardingNotice?>(null) }
    // Bumped whenever the locked state must be released, including after a recreation
    var unlockToken by remember { mutableIntStateOf(0) }
    // Folder that the picker will open at, so a recreation still opens the right one
    var pickerFolderName by rememberSaveable { mutableStateOf(DEFAULT_STORAGE_FOLDER_NAME) }

    // The folder name is sanitized once so the created folder and the picker hint always match
    val cleanName = folderName.toSafeFolderName()

    val storageDir by storagePref.collectAsState()
    val selectedFolder = remember(storageDir) {
        UniFile.fromUri(context, storageDir.toUri())?.takeIf { it.isAccessibleDirectory }
    }
    // Only one path may be in use; changing the folder reopens both
    val folderChosen = selectedFolder != null

    // The next button follows the folder actually in use, since only this result
    // picks up changes while the screen is open
    LaunchedEffect(folderChosen) {
        onLocationValid(folderChosen)
    }

    // Older automatic folders have no marker yet, so one is written once onboarding recognises it
    LaunchedEffect(folderChosen, storageDir) {
        val folder = selectedFolder
        if (folder != null && folder.hasStorageMarker()) {
            ensureStorageMarker(context, folder)
        }
    }

    val pickFolderLauncher = SettingsDataScreen.storageLocationPicker(storagePref)
    val createFolderLauncher = rememberCreateFolderLauncher(
        storagePref = storagePref,
        onPickedNothing = { unlockToken += 1 },
        onFailed = {
            unlockToken += 1
            context.toast(MR.strings.file_picker_error)
        },
    )

    // Releasing the lock through a token keeps it working after the activity is recreated, where
    // the pending picker callback is gone and a saved `isLocked` would freeze the screen
    val initialLockToken = remember { unlockToken }
    LaunchedEffect(unlockToken) {
        if (unlockToken > initialLockToken) isLocked = false
    }

    // The notice reports the result first, then the folder picker opens automatically
    val openPicker = {
        launchPicker(
            launcher = createFolderLauncher,
            uri = FolderPickerHint.uriFor(pickerFolderName),
            onFailure = {
                context.logcat(LogPriority.ERROR, it) { "Failed to launch folder picker" }
                context.toast(MR.strings.file_picker_error)
                unlockToken += 1
            },
        )
    }

    // The countdown is cancelled once OK is pressed, so the folder picker never opens twice
    LaunchedEffect(notice) {
        if (notice == null) return@LaunchedEffect
        delay(NOTICE_DURATION_MS)
        // The action runs before the state is cleared, so a cancellation cannot cut it short
        openPicker()
        notice = null
    }

    // Closes the notice early through OK, without waiting for the countdown to end
    val dismissNotice: () -> Unit = {
        // Guard: a second press (double tap or double TalkBack activation) is ignored
        if (notice != null) {
            notice = null
            openPicker()
        }
    }

    // The popup lives above onboarding instead of inside a dialog, so it can stand on its own
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        Text(
            text = stringResource(KMR.strings.onboarding_storage_folder_info, stringResource(MR.strings.app_name)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (folderChosen) {
            SelectedFolderSection(
                name = selectedFolder?.name.orEmpty(),
                path = remember(selectedFolder) { selectedFolder?.displayablePath.orEmpty().toShortStoragePath() },
                onChangeFolder = {
                    // Clearing the preference reopens both paths; the folder on storage is left untouched
                    storagePref.set("")
                    isLocked = false
                    isEditing = false
                },
            )
        } else {
            NameFolderSection(
                folderName = folderName,
                cleanName = cleanName,
                isEditing = isEditing,
                isLocked = isLocked,
                onFolderNameChange = { folderName = it },
                onStartEditing = { isEditing = true },
                onEditingChange = { isEditing = it },
                onCreate = {
                    isLocked = true
                    // Read before creating, because afterwards the folder always exists
                    val existed = context.storageFolderExists(cleanName)
                    val folderName = context.createStorageFolder(cleanName)
                    if (folderName == null) {
                        // The picker still opens at Documents, so the user is not stuck here
                        unlockToken += 1
                        pickerFolderName = ""
                        notice = OnboardingNotice(
                            title = KMR.strings.settings_storage_create_failed_title,
                            message = KMR.strings.settings_storage_notice_create_failed_desc,
                            kind = NoticeKind.Warning,
                        )
                        return@NameFolderSection
                    }
                    // The picker opens at the folder that is actually used, which may have been reused
                    pickerFolderName = folderName
                    // The notice appears as soon as the folder is created or found, without another tap
                    notice = OnboardingNotice(
                        title = if (existed) {
                            KMR.strings.storage_notice_folder_exists_title
                        } else {
                            KMR.strings.storage_notice_folder_created_title
                        },
                        message = if (existed) {
                            KMR.strings.storage_notice_folder_exists_desc
                        } else {
                            KMR.strings.storage_notice_folder_created_desc
                        },
                        args = listOf(folderName),
                        kind = NoticeKind.Success,
                    )
                },
            )

            StorageActionRow(
                icon = Icons.Rounded.FolderOpen,
                title = stringResource(KMR.strings.onboarding_storage_pick_existing_title),
                supporting = stringResource(KMR.strings.onboarding_storage_pick_info),
                onClick = {
                    launchPicker(
                        launcher = pickFolderLauncher,
                        uri = null,
                        onFailure = {
                            context.logcat(LogPriority.ERROR, it) { "Failed to launch folder picker" }
                            context.toast(MR.strings.file_picker_error)
                        },
                    )
                },
            )
        }

        StorageHelpSection()
    }
}

/** The folder currently in use, together with the way to change it. */
@Composable
private fun SelectedFolderSection(
    name: String,
    path: String,
    onChangeFolder: () -> Unit,
) {
    StorageFolderCard(
        label = stringResource(KMR.strings.settings_storage_current_folder),
        name = name,
        path = path,
        mode = null,
        onStartEditing = null,
        isEditing = false,
    )

    OutlinedButton(
        onClick = onChangeFolder,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(KMR.strings.onboarding_storage_reset_selection))
    }
}

/** Folder name and its create button, grouped because they depend on each other. */
@Composable
private fun NameFolderSection(
    folderName: String,
    cleanName: String,
    isEditing: Boolean,
    isLocked: Boolean,
    onFolderNameChange: (String) -> Unit,
    onStartEditing: () -> Unit,
    onEditingChange: (Boolean) -> Unit,
    onCreate: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
        StorageFolderCard(
            label = stringResource(KMR.strings.onboarding_storage_folder_name_label),
            // While editing, the field shows the raw text so typing is not sanitized
            // mid-word
            name = if (isEditing) folderName else cleanName,
            path = "Documents/$cleanName",
            mode = null,
            onStartEditing = if (isLocked) null else onStartEditing,
            isEditing = isEditing,
            onFolderNameChange = onFolderNameChange,
            onFinishEditing = { onEditingChange(false) },
        )

        Button(
            onClick = onCreate,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLocked && cleanName.isNotEmpty(),
        ) {
            Text(stringResource(KMR.strings.onboarding_storage_folder_create))
        }
    }
}

/**
 * True if the `Documents/<name>` folder already existed before the create button was pressed,
 * so the notice can tell a new folder from one that is reused.
 */
private fun Context.storageFolderExists(cleanName: String): Boolean = if (cleanName == DEFAULT_STORAGE_FOLDER_NAME) {
    findExistingAutoFolder(this) != null
} else {
    runCatching { documentsTree()?.findFile(cleanName)?.isDirectory }.getOrNull() == true
}

/**
 * Creates or reuses the `Documents/<name>` folder. Returns the folder name in use, or null on
 * failure. A custom name gets no marker because it is not part of the automatic path.
 */
private fun Context.createStorageFolder(cleanName: String): String? {
    if (cleanName != DEFAULT_STORAGE_FOLDER_NAME) {
        return cleanName.takeIf { createFolderWithoutMarker(this, cleanName) }
    }

    // A marked folder is reused, so it does not need to be created again. Otherwise the folder is
    // created together with its marker, which also covers a folder from an older version
    ensureAutoFolder(this)?.name?.let { return it }
    return cleanName.takeIf { createFolderInDocuments(this, cleanName) }
}

@Composable
private fun StorageHelpSection() {
    val handler = LocalUriHandler.current

    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Text(
            text = stringResource(MR.strings.onboarding_storage_help_info, stringResource(MR.strings.app_name)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Help has the lowest priority, so it is a link rather than a full-width button
        TextButton(
            onClick = { handler.openUri(SettingsDataScreen.HELP_URL) },
            modifier = Modifier.align(Alignment.Start),
        ) {
            Text(stringResource(MR.strings.onboarding_storage_help_action))
        }
    }
}

@Composable
private fun rememberCreateFolderLauncher(
    storagePref: Preference<String>,
    onPickedNothing: () -> Unit,
    onFailed: () -> Unit,
): ManagedActivityResultLauncher<Uri?, Uri?> {
    val context = LocalContext.current

    return rememberLauncherForActivityResult(
        contract = OpenDocumentTreeWithHint(),
    ) { uri ->
        if (uri == null) {
            onPickedNothing()
            return@rememberLauncherForActivityResult
        }
        if (!savePickedFolder(context, storagePref, uri)) {
            context.logcat(LogPriority.WARN) { "Picked folder could not be saved: $uri" }
            onFailed()
            return@rememberLauncherForActivityResult
        }
        context.logcat { "Onboarding picked folder $uri" }

        // Permission was just granted, so this is the most reliable point to write the marker
        adoptPickedFolder(context, uri)
    }
}

private fun launchPicker(
    launcher: ManagedActivityResultLauncher<Uri?, Uri?>,
    uri: Uri?,
    onFailure: (Exception) -> Unit,
) {
    try {
        launcher.launch(uri)
    } catch (e: Exception) {
        onFailure(e)
    }
}

private fun savePickedFolder(
    context: Context,
    storagePref: Preference<String>,
    uri: Uri,
): Boolean {
    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
        Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    // Some devices refuse a persistable URI grant; access through the URI still works
    try {
        context.contentResolver.takePersistableUriPermission(uri, flags)
    } catch (e: SecurityException) {
        context.logcat(LogPriority.ERROR, e) { "Failed to acquire persistent folder access" }
        context.toast(MR.strings.file_picker_uri_permission_unsupported)
    }

    val picked = UniFile.fromUri(context, uri) ?: return false
    // Cleared first so picking the same folder still triggers a change
    storagePref.set("")
    storagePref.set(picked.uri.toString())
    return true
}

// KMK <--
