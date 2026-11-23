package app.komikku.desktop.ui.more

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.komikku.desktop.backup.DesktopBackupJobs
import app.komikku.desktop.backup.DesktopBackupService
import app.komikku.desktop.platform.DesktopFileDialogs
import app.komikku.desktop.ui.browse.BrowseScaffold
import app.komikku.desktop.ui.relativeTimeSpanString
import app.komikku.shared.settings.PreferenceRow
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.backup.BackupFileValidator
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.restore.RestoreOptions
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.backup.service.BackupPreferences
import tachiyomi.domain.storage.service.StoragePreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

/**
 * Desktop "Data and storage" (Android `SettingsDataScreen`, backup part): storage location, create
 * backup (options → save dialog → borrowed `BackupCreator`), restore backup (open dialog → validation:
 * missing sources / trackers → options → borrowed `BackupRestorer` with progress and the error log), automatic backup
 * interval and the last automatic backup (P3, run by `app.komikku.desktop.scheduler.DesktopScheduler`).
 */
class DataStorageScreenModel(
    private val backupService: DesktopBackupService = Injekt.get(),
    /** Create / restore run here (app lifetime), not in this model's scope: see [DesktopBackupJobs]. */
    val backupJobs: DesktopBackupJobs = Injekt.get(),
    private val fileDialogs: DesktopFileDialogs = Injekt.get(),
    val storagePreferences: StoragePreferences = Injekt.get(),
    val backupPreferences: BackupPreferences = Injekt.get(),
) : StateScreenModel<DataStorageScreenModel.State>(State()) {

    @Immutable
    data class State(
        val dialog: Dialog? = null,
    )

    sealed interface Dialog {
        data class CreateOptions(val options: BackupOptions) : Dialog
        data class RestoreOptionsDialog(
            val file: File,
            val results: BackupFileValidator.Results,
            val options: RestoreOptions,
        ) : Dialog
        data class Failure(val title: StringResource, val message: String?) : Dialog
    }

    fun chooseStorageLocation(title: String) {
        val current = Uri.parse(storagePreferences.baseStorageDirectory().get()).path?.let(::File)
        val dir = fileDialogs.chooseDirectory(title, current) ?: return
        dir.mkdirs()
        storagePreferences.baseStorageDirectory().set(Uri.fromFile(dir).toString())
    }

    fun showCreateDialog() = mutableState.update { it.copy(dialog = Dialog.CreateOptions(BackupOptions())) }

    fun updateCreateOptions(options: BackupOptions) = mutableState.update { it.copy(dialog = Dialog.CreateOptions(options)) }

    fun createBackup(options: BackupOptions, dialogTitle: String) {
        dismissDialog()
        val suggested = backupService.defaultBackupFile()
        val target = fileDialogs.saveFile(dialogTitle, suggested.parentFile?.apply { mkdirs() }, suggested.name) ?: return
        backupJobs.create(target, options)
    }

    fun pickRestoreFile(dialogTitle: String) {
        val file = fileDialogs.openFile(dialogTitle, backupService.defaultBackupFile().parentFile, setOf("tachibk", "proto.gz")) ?: return
        screenModelScope.launch {
            try {
                val results = backupService.validate(file)
                mutableState.update { it.copy(dialog = Dialog.RestoreOptionsDialog(file, results, RestoreOptions())) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableState.update {
                    it.copy(dialog = Dialog.Failure(MR.strings.invalid_backup_file, e.cause?.message ?: e.message))
                }
            }
        }
    }

    fun updateRestoreOptions(options: RestoreOptions) = mutableState.update { state ->
        val dialog = state.dialog as? Dialog.RestoreOptionsDialog ?: return@update state
        state.copy(dialog = dialog.copy(options = options))
    }

    fun restore(file: File, options: RestoreOptions) {
        dismissDialog()
        backupJobs.restore(file, options)
    }

    fun cancelJob() = backupJobs.cancel()

    fun dismissDialog() = mutableState.update { it.copy(dialog = null) }

    /** Dismisses the finished job's result (created path, restore summary, failure, cancellation). */
    fun dismissJobResult() = backupJobs.acknowledge()
}

class DataStorageScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel { DataStorageScreenModel() }
        val state by model.state.collectAsState()
        val job by model.backupJobs.status.collectAsState()
        val running = job is DesktopBackupJobs.Status.Running
        val storageDir by model.storagePreferences.baseStorageDirectory().collectAsState()
        val lastAutoBackup by model.backupPreferences.lastAutoBackupTimestamp().collectAsState()
        val storageTitle = stringResource(MR.strings.pref_storage_location)
        val createTitle = stringResource(MR.strings.pref_create_backup)
        val restoreTitle = stringResource(MR.strings.pref_restore_backup)

        BrowseScaffold(title = stringResource(MR.strings.label_data_storage), onBack = navigator::pop) {
            LazyColumn(Modifier.fillMaxSize().testTag("data-storage"), contentPadding = PaddingValues(vertical = 8.dp)) {
                item {
                    LinkRow(
                        icon = Icons.Outlined.Folder,
                        title = storageTitle,
                        subtitle = (Uri.parse(storageDir).path ?: storageDir) + "\n" + stringResource(MR.strings.pref_storage_location_info),
                        tag = "storage-location",
                        onClick = { model.chooseStorageLocation(storageTitle) },
                    )
                }
                item {
                    Text(
                        stringResource(MR.strings.label_backup),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                item {
                    LinkRow(Icons.Outlined.Save, createTitle, null, "backup-create", enabled = !running) { model.showCreateDialog() }
                }
                item {
                    LinkRow(Icons.Outlined.Restore, restoreTitle, null, "backup-restore", enabled = !running) {
                        model.pickRestoreFile(restoreTitle)
                    }
                }
                // P3: Android's automatic backups (desktop scheduler, while the app is open)
                item {
                    PreferenceRow(
                        Preference.PreferenceItem.ListPreference(
                            preference = model.backupPreferences.backupInterval(),
                            entries = persistentMapOf(
                                0 to stringResource(MR.strings.off),
                                1 to stringResource(MR.strings.update_1hour),
                                3 to stringResource(MR.strings.update_3hour),
                                6 to stringResource(MR.strings.update_6hour),
                                12 to stringResource(MR.strings.update_12hour),
                                24 to stringResource(MR.strings.update_24hour),
                                48 to stringResource(MR.strings.update_48hour),
                                168 to stringResource(MR.strings.update_weekly),
                            ),
                            title = stringResource(MR.strings.pref_backup_interval),
                        ),
                    )
                }
                item {
                    PreferenceRow(
                        Preference.PreferenceItem.InfoPreference(
                            stringResource(MR.strings.backup_info) + "\n\n" +
                                stringResource(MR.strings.last_auto_backup_info, relativeTimeSpanString(lastAutoBackup)),
                        ),
                    )
                }
                when (val status = job) {
                    is DesktopBackupJobs.Status.Running -> item { WorkProgress(status, model::cancelJob) }
                    is DesktopBackupJobs.Status.Created -> item {
                        Text(
                            stringResource(MR.strings.backup_created) + ": " + status.file.absolutePath,
                            modifier = Modifier.padding(16.dp).clickable(onClick = model::dismissJobResult).testTag("backup-created"),
                        )
                    }
                    is DesktopBackupJobs.Status.Cancelled -> item {
                        val text = when (status.kind) {
                            DesktopBackupJobs.Kind.Create -> stringResource(MR.strings.cancelled)
                            DesktopBackupJobs.Kind.Restore -> stringResource(MR.strings.restoring_backup_canceled)
                        }
                        Text(
                            text,
                            modifier = Modifier.padding(16.dp).clickable(onClick = model::dismissJobResult).testTag("backup-cancelled"),
                        )
                    }
                    else -> {}
                }
            }
        }

        when (val dialog = state.dialog) {
            is DataStorageScreenModel.Dialog.CreateOptions -> CreateBackupDialog(dialog, model, createTitle)
            is DataStorageScreenModel.Dialog.RestoreOptionsDialog -> RestoreBackupDialog(dialog, model)
            is DataStorageScreenModel.Dialog.Failure -> AlertDialog(
                onDismissRequest = model::dismissDialog,
                title = { Text(stringResource(dialog.title)) },
                text = { Text(dialog.message.orEmpty()) },
                confirmButton = { TextButton(onClick = model::dismissDialog) { Text(stringResource(MR.strings.action_ok)) } },
                modifier = Modifier.testTag("backup-failure"),
            )
            null -> {}
        }

        // Results of the app-lifetime backup job (also shown when the screen is reopened later)
        when (val status = job) {
            is DesktopBackupJobs.Status.Restored -> RestoreDoneDialog(status, model::dismissJobResult)
            is DesktopBackupJobs.Status.Failed -> AlertDialog(
                onDismissRequest = model::dismissJobResult,
                title = {
                    val title = when (status.kind) {
                        DesktopBackupJobs.Kind.Create -> MR.strings.creating_backup_error
                        DesktopBackupJobs.Kind.Restore -> MR.strings.restoring_backup_error
                    }
                    Text(stringResource(title))
                },
                text = { Text(status.message.orEmpty()) },
                confirmButton = { TextButton(onClick = model::dismissJobResult) { Text(stringResource(MR.strings.action_ok)) } },
                modifier = Modifier.testTag("backup-failure"),
            )
            else -> {}
        }
    }
}

@Composable
private fun WorkProgress(work: DesktopBackupJobs.Status.Running, onCancel: () -> Unit) {
    val title = when (work.kind) {
        DesktopBackupJobs.Kind.Create -> MR.strings.creating_backup
        DesktopBackupJobs.Kind.Restore -> MR.strings.restoring_backup
    }
    Column(Modifier.fillMaxWidth().padding(16.dp).testTag("backup-working"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onCancel, modifier = Modifier.testTag("backup-cancel")) { Text(stringResource(MR.strings.action_cancel)) }
        }
        if (work.content.isNotEmpty()) Text(work.content, style = MaterialTheme.typography.bodySmall)
        if (work.max > 0) {
            LinearProgressIndicator(progress = { work.progress.toFloat() / work.max }, modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { onChange(!checked) },
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun CreateBackupDialog(dialog: DataStorageScreenModel.Dialog.CreateOptions, model: DataStorageScreenModel, title: String) {
    val options = dialog.options
    AlertDialog(
        onDismissRequest = model::dismissDialog,
        title = { Text(stringResource(MR.strings.backup_choice)) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                (BackupOptions.libraryOptions + BackupOptions.settingsOptions).forEach { entry ->
                    CheckRow(stringResource(entry.label), entry.getter(options), entry.enabled(options)) {
                        model.updateCreateOptions(entry.setter(options, it))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { model.createBackup(options, title) }, enabled = options.canCreate(), modifier = Modifier.testTag("backup-create-confirm")) {
                Text(stringResource(MR.strings.action_create))
            }
        },
        dismissButton = { TextButton(onClick = model::dismissDialog) { Text(stringResource(MR.strings.action_cancel)) } },
    )
}

@Composable
private fun RestoreBackupDialog(dialog: DataStorageScreenModel.Dialog.RestoreOptionsDialog, model: DataStorageScreenModel) {
    val options = dialog.options
    AlertDialog(
        onDismissRequest = model::dismissDialog,
        title = { Text(stringResource(MR.strings.pref_restore_backup)) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(dialog.file.name, style = MaterialTheme.typography.bodySmall)
                if (dialog.results.missingSources.isNotEmpty()) {
                    Text(stringResource(MR.strings.backup_restore_missing_sources), color = MaterialTheme.colorScheme.error)
                    dialog.results.missingSources.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                }
                if (dialog.results.missingTrackers.isNotEmpty()) {
                    Text(stringResource(MR.strings.backup_restore_missing_trackers), color = MaterialTheme.colorScheme.error)
                    dialog.results.missingTrackers.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                }
                if (dialog.results.missingSources.isNotEmpty() || dialog.results.missingTrackers.isNotEmpty()) {
                    Text(stringResource(MR.strings.backup_restore_content_full), style = MaterialTheme.typography.bodySmall)
                }
                RestoreOptions.options.forEach { entry ->
                    CheckRow(stringResource(entry.label), entry.getter(options)) { model.updateRestoreOptions(entry.setter(options, it)) }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { model.restore(dialog.file, options) },
                enabled = options.canRestore(),
                modifier = Modifier.testTag("backup-restore-confirm"),
            ) { Text(stringResource(MR.strings.action_restore)) }
        },
        dismissButton = { TextButton(onClick = model::dismissDialog) { Text(stringResource(MR.strings.action_cancel)) } },
    )
}

@Composable
private fun RestoreDoneDialog(dialog: DesktopBackupJobs.Status.Restored, onDismiss: () -> Unit) {
    val duration = dialog.timeMillis.milliseconds
    val time = stringResource(
        MR.strings.restore_duration,
        duration.inWholeMinutes,
        duration.inWholeSeconds % 60,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(MR.strings.restore_completed)) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                Text(pluralStringResource(MR.plurals.restore_completed_message, dialog.errors.size, time, dialog.errors.size))
                dialog.log?.takeIf { dialog.errors.isNotEmpty() }?.let { Text(it.absolutePath, style = MaterialTheme.typography.bodySmall) }
                dialog.errors.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(MR.strings.action_ok)) } },
        modifier = Modifier.testTag("restore-done"),
    )
}
