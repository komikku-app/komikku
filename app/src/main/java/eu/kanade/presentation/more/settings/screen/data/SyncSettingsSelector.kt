package eu.kanade.presentation.more.settings.screen.data

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.domain.sync.models.SyncSettings
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.sync.SyncDataJob
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.update
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.components.LazyColumnWithAction
import tachiyomi.presentation.core.components.SectionCard
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SyncSettingsSelector : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel { SyncSettingsSelectorModel() }
        val state by model.state.collectAsState()

        Scaffold(
            topBar = {
                AppBar(
                    title = stringResource(SYMR.strings.pref_choose_what_to_sync),
                    navigateUp = navigator::pop,
                    scrollBehavior = it,
                )
            },
        ) { contentPadding ->
            LazyColumnWithAction(
                contentPadding = contentPadding,
                actionLabel = stringResource(MR.strings.action_save),
                actionEnabled = true,
                onClickAction = {
                    navigator.pop()
                },
            ) {
                item {
                    SectionCard(MR.strings.label_library) {
                        Options(BackupOptions.libraryOptions, state, model)
                    }
                }

                item {
                    SectionCard(MR.strings.label_settings) {
                        Options(BackupOptions.settingsOptions, state, model)
                    }
                }
            }
        }
    }

    @Composable
    private fun Options(
        options: ImmutableList<BackupOptions.Entry>,
        state: SyncSettingsSelectorModel.State,
        model: SyncSettingsSelectorModel,
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

private class SyncSettingsSelectorModel(
    val syncPreferences: SyncPreferences = Injekt.get(),
) : StateScreenModel<SyncSettingsSelectorModel.State>(
    State(syncOptionsToBackupOptions(syncPreferences.getSyncSettings())),
) {
    fun toggle(setter: (BackupOptions, Boolean) -> BackupOptions, enabled: Boolean) {
        mutableState.update {
            val updatedOptions = setter(it.options, enabled)
            syncPreferences.setSyncSettings(backupOptionsToSyncOptions(updatedOptions))
            it.copy(options = updatedOptions)
        }
    }

    fun syncNow(context: Context) {
        SyncDataJob.startNow(context)
    }

    @Immutable
    data class State(
        val options: BackupOptions = BackupOptions(),
    ) companion object {
        private fun syncOptionsToBackupOptions(syncSettings: SyncSettings): BackupOptions {
            return BackupOptions(
                libraryEntries = syncSettings.libraryEntries,
                categories = syncSettings.categories,
                chapters = syncSettings.chapters,
                tracking = syncSettings.tracking,
                history = syncSettings.history,
                appSettings = syncSettings.appSettings,
                extensionRepoSettings = syncSettings.extensionRepoSettings,
                sourceSettings = syncSettings.sourceSettings,
                privateSettings = syncSettings.privateSettings,

                // SY -->
                customInfo = syncSettings.customInfo,
                readEntries = syncSettings.readEntries,
                savedSearchesFeeds = syncSettings.savedSearchesFeeds,
                // SY <--
            )
        }

        private fun backupOptionsToSyncOptions(backupOptions: BackupOptions): SyncSettings {
            return SyncSettings(
                libraryEntries = backupOptions.libraryEntries,
                categories = backupOptions.categories,
                chapters = backupOptions.chapters,
                tracking = backupOptions.tracking,
                history = backupOptions.history,
                appSettings = backupOptions.appSettings,
                extensionRepoSettings = backupOptions.extensionRepoSettings,
                sourceSettings = backupOptions.sourceSettings,
                privateSettings = backupOptions.privateSettings,

                // SY -->
                customInfo = backupOptions.customInfo,
                readEntries = backupOptions.readEntries,
                savedSearchesFeeds = backupOptions.savedSearchesFeeds,
                // SY <--
            )
        }
    }
}
