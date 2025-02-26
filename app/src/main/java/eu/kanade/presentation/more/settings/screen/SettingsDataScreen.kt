package eu.kanade.presentation.more.settings.screen

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.core.net.toUri
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.hippo.unifile.UniFile
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.data.CreateBackupScreen
import eu.kanade.presentation.more.settings.screen.data.RestoreBackupScreen
import eu.kanade.presentation.more.settings.screen.data.StorageInfo
import eu.kanade.presentation.more.settings.screen.data.SyncSettingsSelector
import eu.kanade.presentation.more.settings.screen.data.SyncTriggerOptionsScreen
import eu.kanade.presentation.more.settings.widget.BasePreferenceWidget
import eu.kanade.presentation.more.settings.widget.PrefsHorizontalPadding
import eu.kanade.presentation.util.relativeTimeSpanString
import eu.kanade.tachiyomi.data.backup.create.BackupCreateJob
import eu.kanade.tachiyomi.data.backup.restore.BackupRestoreJob
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.cache.PagePreviewCache
import eu.kanade.tachiyomi.data.export.LibraryExporter
import eu.kanade.tachiyomi.data.export.LibraryExporter.ExportOptions
import eu.kanade.tachiyomi.data.sync.SyncDataJob
import eu.kanade.tachiyomi.data.sync.SyncManager
import eu.kanade.tachiyomi.data.sync.service.GoogleDriveService
import eu.kanade.tachiyomi.data.sync.service.GoogleDriveSyncService
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.storage.displayablePath
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.backup.service.BackupPreferences
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetFavorites
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.storage.service.StoragePreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsDataScreen : SearchableSettings {
    private fun readResolve(): Any = SettingsDataScreen

    val restorePreferenceKeyString = MR.strings.label_backup
    const val HELP_URL = "https://komikku-app.github.io/docs/faq/storage"

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.label_data_storage

    @Composable
    override fun RowScope.AppBarAction() {
        val uriHandler = LocalUriHandler.current
        IconButton(onClick = { uriHandler.openUri(HELP_URL) }) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                contentDescription = stringResource(MR.strings.tracking_guide),
            )
        }
    }

    @Composable
    override fun getPreferences(): List<Preference> {
        val backupPreferences = Injekt.get<BackupPreferences>()
        val storagePreferences = Injekt.get<StoragePreferences>()

        val syncPreferences = remember { Injekt.get<SyncPreferences>() }
        val syncService by syncPreferences.syncService().collectAsState()

        return persistentListOf(
            getStorageLocationPref(storagePreferences = storagePreferences),
            Preference.PreferenceItem.InfoPreference(stringResource(MR.strings.pref_storage_location_info)),

            getBackupAndRestoreGroup(backupPreferences = backupPreferences),
            getDataGroup(),
            getExportGroup(),
        ) +
            // SY -->
            getSyncPreferences(syncPreferences = syncPreferences, syncService = syncService)
        // SY <--
    }

    @Composable
    fun storageLocationPicker(
        storageDirPref: tachiyomi.core.common.preference.Preference<String>,
    ): ManagedActivityResultLauncher<Uri?, Uri?> {
        val context = LocalContext.current

        return rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
        ) { uri ->
            if (uri != null) {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION

                // For some reason InkBook devices do not implement the SAF properly. Persistable URI grants do not
                // work. However, simply retrieving the URI and using it works fine for these devices. Access is not
                // revoked after the app is closed or the device is restarted.
                // This also holds for some Samsung devices. Thus, we simply execute inside of a try-catch block and
                // ignore the exception if it is thrown.
                try {
                    context.contentResolver.takePersistableUriPermission(uri, flags)
                } catch (e: SecurityException) {
                    logcat(LogPriority.ERROR, e)
                    context.toast(MR.strings.file_picker_uri_permission_unsupported)
                }

                UniFile.fromUri(context, uri)?.let {
                    storageDirPref.set(it.uri.toString())
                }
            }
        }
    }

    @Composable
    fun storageLocationText(
        storageDirPref: tachiyomi.core.common.preference.Preference<String>,
    ): String {
        val context = LocalContext.current
        val storageDir by storageDirPref.collectAsState()

        if (storageDir == storageDirPref.defaultValue()) {
            return stringResource(MR.strings.no_location_set)
        }

        return remember(storageDir) {
            val file = UniFile.fromUri(context, storageDir.toUri())
            file?.displayablePath
        } ?: stringResource(MR.strings.invalid_location, storageDir)
    }

    @Composable
    private fun getStorageLocationPref(
        storagePreferences: StoragePreferences,
    ): Preference.PreferenceItem.TextPreference {
        val context = LocalContext.current
        val pickStorageLocation = storageLocationPicker(storagePreferences.baseStorageDirectory())

        return Preference.PreferenceItem.TextPreference(
            title = stringResource(MR.strings.pref_storage_location),
            subtitle = storageLocationText(storagePreferences.baseStorageDirectory()),
            onClick = {
                try {
                    pickStorageLocation.launch(null)
                } catch (e: ActivityNotFoundException) {
                    context.toast(MR.strings.file_picker_error)
                }
            },
        )
    }

    @Composable
    private fun getBackupAndRestoreGroup(backupPreferences: BackupPreferences): Preference.PreferenceGroup {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        val lastAutoBackup by backupPreferences.lastAutoBackupTimestamp().collectAsState()

        val chooseBackup = rememberLauncherForActivityResult(
            object : ActivityResultContracts.GetContent() {
                override fun createIntent(context: Context, input: String): Intent {
                    val intent = super.createIntent(context, input)
                    return Intent.createChooser(intent, context.stringResource(MR.strings.file_select_backup))
                }
            },
        ) {
            if (it == null) {
                context.toast(MR.strings.file_null_uri_error)
                return@rememberLauncherForActivityResult
            }

            navigator.push(RestoreBackupScreen(it.toString()))
        }

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.label_backup),
            preferenceItems = persistentListOf(
                // Manual actions
                Preference.PreferenceItem.CustomPreference(
                    title = stringResource(restorePreferenceKeyString),
                ) {
                    BasePreferenceWidget(
                        subcomponent = {
                            MultiChoiceSegmentedButtonRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(intrinsicSize = IntrinsicSize.Min)
                                    .padding(horizontal = PrefsHorizontalPadding),
                            ) {
                                SegmentedButton(
                                    modifier = Modifier.fillMaxHeight(),
                                    checked = false,
                                    onCheckedChange = { navigator.push(CreateBackupScreen()) },
                                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                                ) {
                                    Text(stringResource(MR.strings.pref_create_backup))
                                }
                                SegmentedButton(
                                    modifier = Modifier.fillMaxHeight(),
                                    checked = false,
                                    onCheckedChange = {
                                        if (!BackupRestoreJob.isRunning(context)) {
                                            if (DeviceUtil.isMiui && DeviceUtil.isMiuiOptimizationDisabled()) {
                                                context.toast(MR.strings.restore_miui_warning)
                                            }

                                            // no need to catch because it's wrapped with a chooser
                                            chooseBackup.launch("*/*")
                                        } else {
                                            context.toast(MR.strings.restore_in_progress)
                                        }
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                                ) {
                                    Text(stringResource(MR.strings.pref_restore_backup))
                                }
                            }
                        },
                    )
                },

                // Automatic backups
                Preference.PreferenceItem.ListPreference(
                    preference = backupPreferences.backupInterval(),
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
                    onValueChanged = {
                        BackupCreateJob.setupTask(context, it)
                        true
                    },
                ),
                Preference.PreferenceItem.InfoPreference(
                    stringResource(MR.strings.backup_info) + "\n\n" +
                        stringResource(MR.strings.last_auto_backup_info, relativeTimeSpanString(lastAutoBackup)),
                ),
                // KMK -->
                Preference.PreferenceItem.SwitchPreference(
                    preference = backupPreferences.showRestoringProgressBanner(),
                    title = stringResource(KMR.strings.pref_show_restoring_progress_banner),
                ),
                // KMK <--
            ),
        )
    }

    @Composable
    private fun getDataGroup(): Preference.PreferenceGroup {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val libraryPreferences = remember { Injekt.get<LibraryPreferences>() }

        val chapterCache = remember { Injekt.get<ChapterCache>() }
        var cacheReadableSizeSema by remember { mutableIntStateOf(0) }
        val cacheReadableSize = remember(cacheReadableSizeSema) { chapterCache.readableSize }

        // SY -->
        val pagePreviewCache = remember { Injekt.get<PagePreviewCache>() }
        var pagePreviewReadableSizeSema by remember { mutableIntStateOf(0) }
        val pagePreviewReadableSize = remember(pagePreviewReadableSizeSema) { pagePreviewCache.readableSize }
        // SY <--

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_storage_usage),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.CustomPreference(
                    title = stringResource(MR.strings.pref_storage_usage),
                ) {
                    BasePreferenceWidget(
                        subcomponent = {
                            StorageInfo(
                                modifier = Modifier.padding(horizontal = PrefsHorizontalPadding),
                            )
                        },
                    )
                },

                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_clear_chapter_cache),
                    subtitle = stringResource(MR.strings.used_cache, cacheReadableSize),
                    onClick = {
                        scope.launchNonCancellable {
                            try {
                                val deletedFiles = chapterCache.clear()
                                withUIContext {
                                    context.toast(context.stringResource(MR.strings.cache_deleted, deletedFiles))
                                    cacheReadableSizeSema++
                                }
                            } catch (e: Throwable) {
                                logcat(LogPriority.ERROR, e)
                                withUIContext { context.toast(MR.strings.cache_delete_error) }
                            }
                        }
                    },
                ),
                // SY -->
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(SYMR.strings.pref_clear_page_preview_cache),
                    subtitle = stringResource(MR.strings.used_cache, pagePreviewReadableSize),
                    onClick = {
                        scope.launchNonCancellable {
                            try {
                                val deletedFiles = pagePreviewCache.clear()
                                withUIContext {
                                    context.toast(context.stringResource(MR.strings.cache_deleted, deletedFiles))
                                    pagePreviewReadableSizeSema++
                                }
                            } catch (e: Throwable) {
                                logcat(LogPriority.ERROR, e)
                                withUIContext { context.toast(MR.strings.cache_delete_error) }
                            }
                        }
                    },
                ),
                // SY <--
                Preference.PreferenceItem.SwitchPreference(
                    preference = libraryPreferences.autoClearChapterCache(),
                    title = stringResource(MR.strings.pref_auto_clear_chapter_cache),
                ),
            ),
        )
    }

    @Composable
    private fun getExportGroup(): Preference.PreferenceGroup {
        var showDialog by remember { mutableStateOf(false) }
        var exportOptions by remember {
            mutableStateOf(
                ExportOptions(
                    includeTitle = true,
                    includeAuthor = true,
                    includeArtist = true,
                ),
            )
        }

        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val getFavorites = remember { Injekt.get<GetFavorites>() }
        var favorites by remember { mutableStateOf<List<Manga>>(emptyList()) }
        LaunchedEffect(Unit) {
            favorites = getFavorites.await()
        }

        val saveFileLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("text/csv"),
        ) { uri ->
            uri?.let {
                scope.launch {
                    LibraryExporter.exportToCsv(
                        context = context,
                        uri = it,
                        favorites = favorites,
                        options = exportOptions,
                        onExportComplete = {
                            scope.launch(Dispatchers.Main) {
                                context.toast(MR.strings.library_exported)
                            }
                        },
                    )
                }
            }
        }

        if (showDialog) {
            ColumnSelectionDialog(
                options = exportOptions,
                onConfirm = { options ->
                    exportOptions = options
                    saveFileLauncher.launch("komikku_library.csv")
                },
                onDismissRequest = { showDialog = false },
            )
        }

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.export),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.library_list),
                    onClick = { showDialog = true },
                ),
            ),
        )
    }

    @Composable
    private fun ColumnSelectionDialog(
        options: ExportOptions,
        onConfirm: (ExportOptions) -> Unit,
        onDismissRequest: () -> Unit,
    ) {
        var titleSelected by remember { mutableStateOf(options.includeTitle) }
        var authorSelected by remember { mutableStateOf(options.includeAuthor) }
        var artistSelected by remember { mutableStateOf(options.includeArtist) }

        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = {
                Text(text = stringResource(MR.strings.migration_dialog_what_to_include))
            },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = titleSelected,
                            onCheckedChange = { checked ->
                                titleSelected = checked
                                if (!checked) {
                                    authorSelected = false
                                    artistSelected = false
                                }
                            },
                        )
                        Text(text = stringResource(MR.strings.title))
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = authorSelected,
                            onCheckedChange = { authorSelected = it },
                            enabled = titleSelected,
                        )
                        Text(text = stringResource(MR.strings.author))
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = artistSelected,
                            onCheckedChange = { artistSelected = it },
                            enabled = titleSelected,
                        )
                        Text(text = stringResource(MR.strings.artist))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onConfirm(
                            ExportOptions(
                                includeTitle = titleSelected,
                                includeAuthor = authorSelected,
                                includeArtist = artistSelected,
                            ),
                        )
                        onDismissRequest()
                    },
                ) {
                    Text(text = stringResource(MR.strings.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
            },
        )
    }

    // SY -->
    @Composable
    private fun getSyncPreferences(syncPreferences: SyncPreferences, syncService: Int): List<Preference> {
        return listOf(
            Preference.PreferenceGroup(
                title = stringResource(SYMR.strings.pref_sync_service_category),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.ListPreference(
                        preference = syncPreferences.syncService(),
                        entries = persistentMapOf(
                            SyncManager.SyncService.NONE.value to stringResource(MR.strings.off),
                            SyncManager.SyncService.SYNCYOMI.value to stringResource(SYMR.strings.syncyomi),
                            SyncManager.SyncService.GOOGLE_DRIVE.value to stringResource(SYMR.strings.google_drive),
                        ),
                        title = stringResource(SYMR.strings.pref_sync_service),
                        onValueChanged = { true },
                    ),
                ),
            ),
        ) + getSyncServicePreferences(syncPreferences, syncService)
    }

    @Composable
    private fun getSyncServicePreferences(syncPreferences: SyncPreferences, syncService: Int): List<Preference> {
        val syncServiceType = SyncManager.SyncService.fromInt(syncService)

        val basePreferences = getBasePreferences(syncServiceType, syncPreferences)

        return if (syncServiceType != SyncManager.SyncService.NONE) {
            basePreferences + getAdditionalPreferences(syncPreferences)
        } else {
            basePreferences
        }
    }

    @Composable
    private fun getBasePreferences(
        syncServiceType: SyncManager.SyncService,
        syncPreferences: SyncPreferences,
    ): List<Preference> {
        val navigator = LocalNavigator.currentOrThrow
        val preferences = when (syncServiceType) {
            SyncManager.SyncService.NONE -> emptyList()
            SyncManager.SyncService.SYNCYOMI -> getSelfHostPreferences(syncPreferences)
            SyncManager.SyncService.GOOGLE_DRIVE -> getGoogleDrivePreferences()
        }

        return if (syncServiceType != SyncManager.SyncService.NONE) {
            preferences + Preference.PreferenceItem.TextPreference(
                title = stringResource(SYMR.strings.pref_choose_what_to_sync),
                onClick = {
                    navigator.push(SyncSettingsSelector())
                },
            )
        } else {
            preferences
        }
    }

    @Composable
    private fun getAdditionalPreferences(syncPreferences: SyncPreferences): List<Preference> {
        return listOf(
            getSyncNowPref(),
            getAutomaticSyncGroup(syncPreferences),
            // KMK -->
            Preference.PreferenceItem.SwitchPreference(
                preference = syncPreferences.showSyncingProgressBanner(),
                title = stringResource(KMR.strings.pref_show_syncing_progress_banner),
            ),
            // KMK <--
        )
    }

    @Composable
    private fun getGoogleDrivePreferences(): List<Preference> {
        val context = LocalContext.current
        val googleDriveSync = Injekt.get<GoogleDriveService>()
        return listOf(
            Preference.PreferenceItem.TextPreference(
                title = stringResource(SYMR.strings.pref_google_drive_sign_in),
                onClick = {
                    val intent = googleDriveSync.getSignInIntent()
                    context.startActivity(intent)
                },
            ),
            getGoogleDrivePurge(),
        )
    }

    @Composable
    private fun getGoogleDrivePurge(): Preference.PreferenceItem.TextPreference {
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val googleDriveSync = remember { GoogleDriveSyncService(context) }
        var showPurgeDialog by remember { mutableStateOf(false) }

        if (showPurgeDialog) {
            PurgeConfirmationDialog(
                onConfirm = {
                    showPurgeDialog = false
                    scope.launch {
                        val result = googleDriveSync.deleteSyncDataFromGoogleDrive()
                        when (result) {
                            GoogleDriveSyncService.DeleteSyncDataStatus.NOT_INITIALIZED -> context.toast(
                                SYMR.strings.google_drive_not_signed_in,
                                duration = 5000,
                            )
                            GoogleDriveSyncService.DeleteSyncDataStatus.NO_FILES -> context.toast(
                                SYMR.strings.google_drive_sync_data_not_found,
                                duration = 5000,
                            )
                            GoogleDriveSyncService.DeleteSyncDataStatus.SUCCESS -> context.toast(
                                SYMR.strings.google_drive_sync_data_purged,
                                duration = 5000,
                            )
                            GoogleDriveSyncService.DeleteSyncDataStatus.ERROR -> context.toast(
                                SYMR.strings.google_drive_sync_data_purge_error,
                                duration = 10000,
                            )
                        }
                    }
                },
                onDismissRequest = { showPurgeDialog = false },
            )
        }

        return Preference.PreferenceItem.TextPreference(
            title = stringResource(SYMR.strings.pref_google_drive_purge_sync_data),
            onClick = { showPurgeDialog = true },
        )
    }

    @Composable
    private fun PurgeConfirmationDialog(
        onConfirm: () -> Unit,
        onDismissRequest: () -> Unit,
    ) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(text = stringResource(SYMR.strings.pref_purge_confirmation_title)) },
            text = { Text(text = stringResource(SYMR.strings.pref_purge_confirmation_message)) },
            dismissButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text(text = stringResource(MR.strings.action_ok))
                }
            },
        )
    }

    @Composable
    private fun getSelfHostPreferences(syncPreferences: SyncPreferences): List<Preference> {
        val scope = rememberCoroutineScope()
        return listOf(
            Preference.PreferenceItem.EditTextPreference(
                preference = syncPreferences.clientHost(),
                title = stringResource(SYMR.strings.pref_sync_host),
                subtitle = stringResource(SYMR.strings.pref_sync_host_summ),
                onValueChanged = { newValue ->
                    scope.launch {
                        // Trim spaces at the beginning and end, then remove trailing slash if present
                        val trimmedValue = newValue.trim()
                        val modifiedValue = trimmedValue.trimEnd { it == '/' }
                        syncPreferences.clientHost().set(modifiedValue)
                    }
                    true
                },
            ),
            Preference.PreferenceItem.EditTextPreference(
                preference = syncPreferences.clientAPIKey(),
                title = stringResource(SYMR.strings.pref_sync_api_key),
                subtitle = stringResource(SYMR.strings.pref_sync_api_key_summ),
            ),
        )
    }

    @Composable
    private fun getSyncNowPref(): Preference.PreferenceGroup {
        val context = LocalContext.current
        return Preference.PreferenceGroup(
            title = stringResource(SYMR.strings.pref_sync_now_group_title),
            preferenceItems = persistentListOf(
                getSyncOptionsPref(),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(SYMR.strings.pref_sync_now),
                    subtitle = stringResource(SYMR.strings.pref_sync_now_subtitle),
                    onClick = {
                        if (!SyncDataJob.isRunning(context)) {
                            SyncDataJob.startNow(context, manual = true)
                        } else {
                            context.toast(SYMR.strings.sync_in_progress)
                        }
                    },
                ),
            ),
        )
    }

    @Composable
    private fun getSyncOptionsPref(): Preference.PreferenceItem.TextPreference {
        val navigator = LocalNavigator.currentOrThrow
        return Preference.PreferenceItem.TextPreference(
            title = stringResource(SYMR.strings.pref_sync_options),
            subtitle = stringResource(SYMR.strings.pref_sync_options_summ),
            onClick = { navigator.push(SyncTriggerOptionsScreen()) },
        )
    }

    @Composable
    private fun getAutomaticSyncGroup(syncPreferences: SyncPreferences): Preference.PreferenceGroup {
        val context = LocalContext.current
        val syncIntervalPref = syncPreferences.syncInterval()
        val lastSync by syncPreferences.lastSyncTimestamp().collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(SYMR.strings.pref_sync_automatic_category),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    preference = syncIntervalPref,
                    entries = persistentMapOf(
                        0 to stringResource(MR.strings.off),
                        30 to stringResource(SYMR.strings.update_30min),
                        60 to stringResource(SYMR.strings.update_1hour),
                        180 to stringResource(SYMR.strings.update_3hour),
                        360 to stringResource(MR.strings.update_6hour),
                        720 to stringResource(MR.strings.update_12hour),
                        1440 to stringResource(MR.strings.update_24hour),
                        2880 to stringResource(MR.strings.update_48hour),
                        10080 to stringResource(MR.strings.update_weekly),
                    ),
                    title = stringResource(SYMR.strings.pref_sync_interval),
                    onValueChanged = {
                        SyncDataJob.setupTask(context, it)
                        true
                    },
                ),
                Preference.PreferenceItem.InfoPreference(
                    stringResource(SYMR.strings.last_synchronization, relativeTimeSpanString(lastSync)),
                ),
            ),
        )
    }
    // SY <--
}
