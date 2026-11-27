package eu.kanade.presentation.more.settings.screen.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.WarningBanner
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.backup.create.BackupCreateJob
import eu.kanade.tachiyomi.data.backup.create.BackupCreator
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.update
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.components.LazyColumnWithAction
import tachiyomi.presentation.core.components.SectionCard
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource

class CreateBackupScreen(
    // KMK -->
    private val isVault: Boolean = false,
    // KMK <--
) : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel { CreateBackupScreenModel(isVault) }
        val state by model.state.collectAsState()

        val chooseBackupDir = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/*"),
        ) {
            if (it != null) {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                model.createBackup(context, it, isVault)
                navigator.pop()
            }
        }

        Scaffold(
            topBar = {
                AppBar(
                    title = stringResource(
                        // KMK -->
                        if (isVault) KMR.strings.private_vault_backup else MR.strings.pref_create_backup,
                        // KMK <--
                    ),
                    navigateUp = navigator::pop,
                    scrollBehavior = it,
                )
            },
        ) { contentPadding ->
            LazyColumnWithAction(
                contentPadding = contentPadding,
                actionLabel = stringResource(MR.strings.action_create),
                actionEnabled = state.options.canCreate(),
                onClickAction = {
                    if (!BackupCreateJob.isManualJobRunning(context)) {
                        try {
                            chooseBackupDir.launch(BackupCreator.getFilename(isVault))
                        } catch (e: ActivityNotFoundException) {
                            context.toast(MR.strings.file_picker_error)
                        }
                    } else {
                        context.toast(MR.strings.backup_in_progress)
                    }
                },
            ) {
                if (DeviceUtil.isMiui && DeviceUtil.isMiuiOptimizationDisabled()) {
                    item {
                        WarningBanner(MR.strings.restore_miui_warning)
                    }
                }

                item {
                    SectionCard(MR.strings.label_library) {
                        Options(BackupOptions.libraryOptions, state, model)
                    }
                }

                // KMK -->
                if (!isVault) {
                    item {
                        SectionCard(MR.strings.label_settings) {
                            Options(BackupOptions.settingsOptions, state, model)
                        }
                    }
                }
                // KMK <--
            }
        }
    }

    @Composable
    private fun Options(
        options: ImmutableList<BackupOptions.Entry>,
        state: CreateBackupScreenModel.State,
        model: CreateBackupScreenModel,
    ) {
        options.forEach { option ->
            LabeledCheckbox(
                label = stringResource(option.label),
                checked = option.getter(state.options),
                onCheckedChange = {
                    model.toggle(option.setter, it)
                },
                enabled = option.enabled(state.options),
            )
        }
    }
}

private class CreateBackupScreenModel(
    // KMK -->
    isVault: Boolean = false,
    // KMK <--
) : StateScreenModel<CreateBackupScreenModel.State>(
    // KMK -->
    State(
        options = if (isVault) BackupOptions.vaultDefaults else BackupOptions(),
    ),
    // KMK <--
) {

    fun toggle(setter: (BackupOptions, Boolean) -> BackupOptions, enabled: Boolean) {
        mutableState.update {
            it.copy(
                options = setter(it.options, enabled),
            )
        }
    }

    // KMK -->
    fun createBackup(context: Context, uri: Uri, vault: Boolean = false) {
        BackupCreateJob.startNow(context, uri, state.value.options, vault)
    }
    // KMK <--

    @Immutable
    data class State(
        val options: BackupOptions = BackupOptions(),
    )
}
