package eu.kanade.presentation.more.settings.screen

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
import androidx.core.text.HtmlCompat
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.extension.interactor.TrustExtension
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.source.service.SourcePreferences.DataSaver
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.advanced.ClearDatabaseScreen
import eu.kanade.presentation.more.settings.screen.debug.DebugInfoScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.NetworkPreferences
import eu.kanade.tachiyomi.network.PREF_DOH_360
import eu.kanade.tachiyomi.network.PREF_DOH_ADGUARD
import eu.kanade.tachiyomi.network.PREF_DOH_ALIDNS
import eu.kanade.tachiyomi.network.PREF_DOH_CLOUDFLARE
import eu.kanade.tachiyomi.network.PREF_DOH_CONTROLD
import eu.kanade.tachiyomi.network.PREF_DOH_DNSPOD
import eu.kanade.tachiyomi.network.PREF_DOH_GOOGLE
import eu.kanade.tachiyomi.network.PREF_DOH_MULLVAD
import eu.kanade.tachiyomi.network.PREF_DOH_NJALLA
import eu.kanade.tachiyomi.network.PREF_DOH_QUAD101
import eu.kanade.tachiyomi.network.PREF_DOH_QUAD9
import eu.kanade.tachiyomi.network.PREF_DOH_SHECAN
import eu.kanade.tachiyomi.source.AndroidSourceManager
import eu.kanade.tachiyomi.ui.more.OnboardingScreen
import eu.kanade.tachiyomi.util.CrashLogUtil
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.system.GLUtil
import eu.kanade.tachiyomi.util.system.isDevFlavor
import eu.kanade.tachiyomi.util.system.isPreviewBuildType
import eu.kanade.tachiyomi.util.system.isShizukuInstalled
import eu.kanade.tachiyomi.util.system.powerManager
import eu.kanade.tachiyomi.util.system.setDefaultSettings
import eu.kanade.tachiyomi.util.system.toast
import exh.debug.SettingsDebugScreen
import exh.log.EHLogLevel
import exh.pref.DelegateSourcePreferences
import exh.source.BlacklistedSources
import exh.source.EH_SOURCE_ID
import exh.source.EXH_SOURCE_ID
import exh.util.toAnnotatedString
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import logcat.LogPriority
import okhttp3.Headers
import tachiyomi.core.common.i18n.pluralStringResource
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.UnsortedPreferences
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.manga.interactor.GetAllManga
import tachiyomi.domain.manga.interactor.ResetViewerFlags
import tachiyomi.domain.release.service.AppUpdatePolicy
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

object SettingsAdvancedScreen : SearchableSettings {
    private fun readResolve(): Any = SettingsAdvancedScreen

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_advanced

    @Composable
    override fun getPreferences(): List<Preference> {
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        val basePreferences = remember { Injekt.get<BasePreferences>() }
        val networkPreferences = remember { Injekt.get<NetworkPreferences>() }
        val unsortedPreferences = remember { Injekt.get<UnsortedPreferences>() }

        return listOf(
            Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.pref_dump_crash_logs),
                subtitle = stringResource(MR.strings.pref_dump_crash_logs_summary),
                onClick = {
                    scope.launch {
                        CrashLogUtil(context).dumpLogs()
                    }
                },
            ),
            /* SY --> Preference.PreferenceItem.SwitchPreference(
                pref = networkPreferences.verboseLogging(),
                title = stringResource(MR.strings.dpref_verbose_logging),
                subtitle = stringResource(MR.strings.pref_verbose_logging_summary),
                onValueChanged = {
                    context.toast(MR.strings.requires_app_restart)
                    true
                },
            ), SY <-- */
            Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.pref_debug_info),
                onClick = { navigator.push(DebugInfoScreen()) },
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.pref_onboarding_guide),
                onClick = { navigator.push(OnboardingScreen()) },
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.pref_manage_notifications),
                onClick = {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                    context.startActivity(intent)
                },
            ),
            Preference.PreferenceItem.ListPreference(
                pref = unsortedPreferences.appShouldAutoUpdate(),
                title = stringResource(KMR.strings.auto_update_app),
                entries = AppUpdatePolicy.entries
                    .associateWith {
                        when (it) {
                            AppUpdatePolicy.NEVER ->
                                stringResource(KMR.strings.auto_update_app_never)
                            AppUpdatePolicy.ALWAYS ->
                                stringResource(KMR.strings.auto_update_app_always)
                            AppUpdatePolicy.ONLY_ON_WIFI ->
                                stringResource(KMR.strings.auto_update_app_wifi_only)
                            else -> it.name
                        }
                    }
                    .toImmutableMap(),
                onValueChanged = {
                    (context as? Activity)?.let { ActivityCompat.recreate(it) }
                    true
                },
            ),
            getBackgroundActivityGroup(),
            getDataGroup(),
            getNetworkGroup(networkPreferences = networkPreferences),
            getLibraryGroup(),
            getReaderGroup(basePreferences = basePreferences),
            getExtensionsGroup(basePreferences = basePreferences),
            // SY -->
            // getDownloaderGroup(),
            getDataSaverGroup(),
            getDeveloperToolsGroup(),
            // SY <--
        )
    }

    @Composable
    private fun getBackgroundActivityGroup(): Preference.PreferenceGroup {
        val context = LocalContext.current
        val uriHandler = LocalUriHandler.current

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.label_background_activity),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_disable_battery_optimization),
                    subtitle = stringResource(MR.strings.pref_disable_battery_optimization_summary),
                    onClick = {
                        val packageName: String = context.packageName
                        if (!context.powerManager.isIgnoringBatteryOptimizations(packageName)) {
                            try {
                                @SuppressLint("BatteryLife")
                                val intent = Intent().apply {
                                    action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                                    data = "package:$packageName".toUri()
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (e: ActivityNotFoundException) {
                                context.toast(MR.strings.battery_optimization_setting_activity_not_found)
                            }
                        } else {
                            context.toast(MR.strings.battery_optimization_disabled)
                        }
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = "Don't kill my app!",
                    subtitle = stringResource(MR.strings.about_dont_kill_my_app),
                    onClick = { uriHandler.openUri("https://dontkillmyapp.com/") },
                ),
            ),
        )
    }

    @Composable
    private fun getDataGroup(): Preference.PreferenceGroup {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.label_data),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_invalidate_download_cache),
                    subtitle = stringResource(MR.strings.pref_invalidate_download_cache_summary),
                    onClick = {
                        Injekt.get<DownloadCache>().invalidateCache()
                        context.toast(MR.strings.download_cache_invalidated)
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_clear_database),
                    subtitle = stringResource(MR.strings.pref_clear_database_summary),
                    onClick = { navigator.push(ClearDatabaseScreen()) },
                ),
            ),
        )
    }

    @Composable
    private fun getNetworkGroup(
        networkPreferences: NetworkPreferences,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current
        val networkHelper = remember { Injekt.get<NetworkHelper>() }

        val userAgentPref = networkPreferences.defaultUserAgent()
        val userAgent by userAgentPref.collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.label_network),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_clear_cookies),
                    onClick = {
                        networkHelper.cookieJar.removeAll()
                        context.toast(MR.strings.cookies_cleared)
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_clear_webview_data),
                    onClick = {
                        try {
                            WebView(context).run {
                                setDefaultSettings()
                                clearCache(true)
                                clearFormData()
                                clearHistory()
                                clearSslPreferences()
                            }
                            WebStorage.getInstance().deleteAllData()
                            context.applicationInfo?.dataDir?.let {
                                File("$it/app_webview/").deleteRecursively()
                            }
                            context.toast(MR.strings.webview_data_deleted)
                        } catch (e: Throwable) {
                            logcat(LogPriority.ERROR, e)
                            context.toast(MR.strings.cache_delete_error)
                        }
                    },
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = networkPreferences.dohProvider(),
                    title = stringResource(MR.strings.pref_dns_over_https),
                    entries = persistentMapOf(
                        -1 to stringResource(MR.strings.disabled),
                        PREF_DOH_CLOUDFLARE to "Cloudflare",
                        PREF_DOH_GOOGLE to "Google",
                        PREF_DOH_ADGUARD to "AdGuard",
                        PREF_DOH_QUAD9 to "Quad9",
                        PREF_DOH_ALIDNS to "AliDNS",
                        PREF_DOH_DNSPOD to "DNSPod",
                        PREF_DOH_360 to "360",
                        PREF_DOH_QUAD101 to "Quad 101",
                        PREF_DOH_MULLVAD to "Mullvad",
                        PREF_DOH_CONTROLD to "Control D",
                        PREF_DOH_NJALLA to "Njalla",
                        PREF_DOH_SHECAN to "Shecan",
                    ),
                    onValueChanged = {
                        context.toast(MR.strings.requires_app_restart)
                        true
                    },
                ),
                Preference.PreferenceItem.EditTextPreference(
                    pref = userAgentPref,
                    title = stringResource(MR.strings.pref_user_agent_string),
                    onValueChanged = {
                        try {
                            // OkHttp checks for valid values internally
                            Headers.Builder().add("User-Agent", it)
                            context.toast(MR.strings.requires_app_restart)
                        } catch (_: IllegalArgumentException) {
                            context.toast(MR.strings.error_user_agent_string_invalid)
                            return@EditTextPreference false
                        }
                        true
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_reset_user_agent_string),
                    enabled = remember(userAgent) { userAgent != userAgentPref.defaultValue() },
                    onClick = {
                        userAgentPref.delete()
                        context.toast(MR.strings.requires_app_restart)
                    },
                ),
            ),
        )
    }

    @Composable
    private fun getLibraryGroup(): Preference.PreferenceGroup {
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        // KMK -->
        val uiPreferences = remember { Injekt.get<UiPreferences>() }
        // KMK <--

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.label_library),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_refresh_library_covers),
                    onClick = { LibraryUpdateJob.startNow(context, target = LibraryUpdateJob.Target.COVERS) },
                ),
                // KMK -->
                Preference.PreferenceItem.SwitchPreference(
                    pref = uiPreferences.preloadLibraryColor(),
                    title = stringResource(KMR.strings.preload_library_cover_color),
                ),
                // KMK <--
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_reset_viewer_flags),
                    subtitle = stringResource(MR.strings.pref_reset_viewer_flags_summary),
                    onClick = {
                        scope.launchNonCancellable {
                            val success = Injekt.get<ResetViewerFlags>().await()
                            withUIContext {
                                val message = if (success) {
                                    MR.strings.pref_reset_viewer_flags_success
                                } else {
                                    MR.strings.pref_reset_viewer_flags_error
                                }
                                context.toast(message)
                            }
                        }
                    },
                ),
            ),
        )
    }

    @Composable
    private fun getReaderGroup(
        basePreferences: BasePreferences,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current
        val chooseColorProfile = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
            uri?.let {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
                basePreferences.displayProfile().set(uri.toString())
            }
        }
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_reader),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    pref = basePreferences.hardwareBitmapThreshold(),
                    title = stringResource(MR.strings.pref_hardware_bitmap_threshold),
                    subtitleProvider = { value, options ->
                        stringResource(MR.strings.pref_hardware_bitmap_threshold_summary, options[value].orEmpty())
                    },
                    enabled = GLUtil.DEVICE_TEXTURE_LIMIT > GLUtil.SAFE_TEXTURE_LIMIT,
                    entries = GLUtil.CUSTOM_TEXTURE_LIMIT_OPTIONS
                        .mapIndexed { index, option ->
                            val display = if (index == 0) {
                                stringResource(MR.strings.pref_hardware_bitmap_threshold_default, option)
                            } else {
                                option.toString()
                            }
                            option to display
                        }
                        .toMap()
                        .toImmutableMap(),
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_display_profile),
                    subtitle = basePreferences.displayProfile().get(),
                    onClick = {
                        chooseColorProfile.launch(arrayOf("*/*"))
                    },
                ),
            ),
        )
    }

    @Composable
    private fun getExtensionsGroup(
        basePreferences: BasePreferences,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current
        val uriHandler = LocalUriHandler.current
        val extensionInstallerPref = basePreferences.extensionInstaller()
        var shizukuMissing by rememberSaveable { mutableStateOf(false) }
        val trustExtension = remember { Injekt.get<TrustExtension>() }

        if (shizukuMissing) {
            val dismiss = { shizukuMissing = false }
            AlertDialog(
                onDismissRequest = dismiss,
                title = { Text(text = stringResource(MR.strings.ext_installer_shizuku)) },
                text = { Text(text = stringResource(MR.strings.ext_installer_shizuku_unavailable_dialog)) },
                dismissButton = {
                    TextButton(onClick = dismiss) {
                        Text(text = stringResource(MR.strings.action_cancel))
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            dismiss()
                            uriHandler.openUri("https://shizuku.rikka.app/download")
                        },
                    ) {
                        Text(text = stringResource(MR.strings.action_ok))
                    }
                },
            )
        }
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.label_extensions),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    pref = extensionInstallerPref,
                    title = stringResource(MR.strings.ext_installer_pref),
                    entries = extensionInstallerPref.entries
                        .filter {
                            // TODO: allow private option in stable versions once URL handling is more fleshed out
                            if (isPreviewBuildType || isDevFlavor) {
                                true
                            } else {
                                it != BasePreferences.ExtensionInstaller.PRIVATE
                            }
                        }
                        .associateWith { stringResource(it.titleRes) }
                        .toImmutableMap(),
                    onValueChanged = {
                        if (it == BasePreferences.ExtensionInstaller.SHIZUKU &&
                            !context.isShizukuInstalled
                        ) {
                            shizukuMissing = true
                            false
                        } else {
                            true
                        }
                    },
                ),
                // KMK -->
                Preference.PreferenceItem.InfoPreference(stringResource(KMR.strings.pref_private_installer_warning)),
                // KMK <--
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.ext_revoke_trust),
                    onClick = {
                        trustExtension.revokeAll()
                        context.toast(MR.strings.requires_app_restart)
                    },
                ),
            ),
        )
    }

    // SY -->
    @Composable
    fun CleanupDownloadsDialog(
        onDismissRequest: () -> Unit,
        onCleanupDownloads: (removeRead: Boolean, removeNonFavorite: Boolean) -> Unit,
    ) {
        val context = LocalContext.current
        val options = remember { context.resources.getStringArray(R.array.clean_up_downloads).toList() }
        val selection = remember {
            options.toMutableStateList()
        }
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(text = stringResource(SYMR.strings.clean_up_downloaded_chapters)) },
            text = {
                LazyColumn {
                    options.forEachIndexed { index, option ->
                        item {
                            LabeledCheckbox(
                                label = option,
                                checked = index == 0 || selection.contains(option),
                                onCheckedChange = {
                                    when (it) {
                                        true -> selection.add(option)
                                        false -> selection.remove(option)
                                    }
                                },
                            )
                        }
                    }
                }
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = true,
            ),
            confirmButton = {
                TextButton(
                    onClick = {
                        val removeRead = options[1] in selection
                        val removeNonFavorite = options[2] in selection
                        onCleanupDownloads(removeRead, removeNonFavorite)
                    },
                ) {
                    Text(text = stringResource(MR.strings.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
            },
        )
    }

    @Composable
    private fun getDownloaderGroup(): Preference.PreferenceGroup {
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        var dialogOpen by remember { mutableStateOf(false) }
        if (dialogOpen) {
            CleanupDownloadsDialog(
                onDismissRequest = { dialogOpen = false },
                onCleanupDownloads = { removeRead, removeNonFavorite ->
                    dialogOpen = false
                    if (job?.isActive == true) return@CleanupDownloadsDialog
                    context.toast(SYMR.strings.starting_cleanup)
                    job = scope.launchNonCancellable {
                        val mangaList = Injekt.get<GetAllManga>().await()
                        val downloadManager: DownloadManager = Injekt.get()
                        var foldersCleared = 0
                        Injekt.get<SourceManager>().getOnlineSources().forEach { source ->
                            val mangaFolders = downloadManager.getMangaFolders(source)
                            val sourceManga = mangaList
                                .asSequence()
                                .filter { it.source == source.id }
                                .map { it to DiskUtil.buildValidFilename(it.ogTitle) }
                                .toList()

                            mangaFolders.forEach mangaFolder@{ mangaFolder ->
                                val manga =
                                    sourceManga.find { (_, folderName) ->
                                        folderName == mangaFolder.name
                                    }?.first
                                if (manga == null) {
                                    // download is orphaned delete it
                                    foldersCleared += 1 + (
                                        mangaFolder.listFiles()
                                            .orEmpty().size
                                        )
                                    mangaFolder.delete()
                                } else {
                                    val chapterList = Injekt.get<GetChaptersByMangaId>().await(manga.id)
                                    foldersCleared += downloadManager.cleanupChapters(
                                        chapterList,
                                        manga,
                                        source,
                                        removeRead,
                                        removeNonFavorite,
                                    )
                                }
                            }
                        }
                        withUIContext {
                            val cleanupString =
                                if (foldersCleared == 0) {
                                    context.stringResource(SYMR.strings.no_folders_to_cleanup)
                                } else {
                                    context.pluralStringResource(
                                        SYMR.plurals.cleanup_done,
                                        foldersCleared,
                                        foldersCleared,
                                    )
                                }
                            context.toast(cleanupString, Toast.LENGTH_LONG)
                        }
                    }
                },
            )
        }
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.download_notifier_downloader_title),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(SYMR.strings.clean_up_downloaded_chapters),
                    subtitle = stringResource(SYMR.strings.delete_unused_chapters),
                    onClick = { dialogOpen = true },
                ),
            ),
        )
    }

    @Composable
    private fun getDataSaverGroup(): Preference.PreferenceGroup {
        val sourcePreferences = remember { Injekt.get<SourcePreferences>() }
        val dataSaver by sourcePreferences.dataSaver().collectAsState()
        return Preference.PreferenceGroup(
            title = stringResource(SYMR.strings.data_saver),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    pref = sourcePreferences.dataSaver(),
                    title = stringResource(SYMR.strings.data_saver),
                    subtitle = stringResource(SYMR.strings.data_saver_summary),
                    entries = persistentMapOf(
                        DataSaver.NONE to stringResource(MR.strings.disabled),
                        DataSaver.BANDWIDTH_HERO to stringResource(SYMR.strings.bandwidth_hero),
                        DataSaver.WSRV_NL to stringResource(SYMR.strings.wsrv),
                    ),
                ),
                Preference.PreferenceItem.EditTextPreference(
                    pref = sourcePreferences.dataSaverServer(),
                    title = stringResource(SYMR.strings.bandwidth_data_saver_server),
                    subtitle = stringResource(SYMR.strings.data_saver_server_summary),
                    enabled = dataSaver == DataSaver.BANDWIDTH_HERO,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = sourcePreferences.dataSaverDownloader(),
                    title = stringResource(SYMR.strings.data_saver_downloader),
                    enabled = dataSaver != DataSaver.NONE,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = sourcePreferences.dataSaverIgnoreJpeg(),
                    title = stringResource(SYMR.strings.data_saver_ignore_jpeg),
                    enabled = dataSaver != DataSaver.NONE,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = sourcePreferences.dataSaverIgnoreGif(),
                    title = stringResource(SYMR.strings.data_saver_ignore_gif),
                    enabled = dataSaver != DataSaver.NONE,
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = sourcePreferences.dataSaverImageQuality(),
                    title = stringResource(SYMR.strings.data_saver_image_quality),
                    subtitle = stringResource(SYMR.strings.data_saver_image_quality_summary),
                    entries = listOf(
                        "10%",
                        "20%",
                        "40%",
                        "50%",
                        "70%",
                        "80%",
                        "90%",
                        "95%",
                    ).associateBy { it.trimEnd('%').toInt() }.toImmutableMap(),
                    enabled = dataSaver != DataSaver.NONE,
                ),
                kotlin.run {
                    val dataSaverImageFormatJpeg by sourcePreferences.dataSaverImageFormatJpeg()
                        .collectAsState()
                    Preference.PreferenceItem.SwitchPreference(
                        pref = sourcePreferences.dataSaverImageFormatJpeg(),
                        title = stringResource(SYMR.strings.data_saver_image_format),
                        subtitle = if (dataSaverImageFormatJpeg) {
                            stringResource(SYMR.strings.data_saver_image_format_summary_on)
                        } else {
                            stringResource(SYMR.strings.data_saver_image_format_summary_off)
                        },
                        enabled = dataSaver != DataSaver.NONE,
                    )
                },
                Preference.PreferenceItem.SwitchPreference(
                    pref = sourcePreferences.dataSaverColorBW(),
                    title = stringResource(SYMR.strings.data_saver_color_bw),
                    enabled = dataSaver == DataSaver.BANDWIDTH_HERO,
                ),
            ),
        )
    }

    @Composable
    private fun getDeveloperToolsGroup(): Preference.PreferenceGroup {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val sourcePreferences = remember { Injekt.get<SourcePreferences>() }
        val unsortedPreferences = remember { Injekt.get<UnsortedPreferences>() }
        val delegateSourcePreferences = remember { Injekt.get<DelegateSourcePreferences>() }
        val securityPreferences = remember { Injekt.get<SecurityPreferences>() }
        return Preference.PreferenceGroup(
            title = stringResource(SYMR.strings.developer_tools),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    pref = unsortedPreferences.isHentaiEnabled(),
                    title = stringResource(SYMR.strings.toggle_hentai_features),
                    subtitle = stringResource(SYMR.strings.toggle_hentai_features_summary),
                    onValueChanged = {
                        if (it) {
                            BlacklistedSources.HIDDEN_SOURCES += EH_SOURCE_ID
                            BlacklistedSources.HIDDEN_SOURCES += EXH_SOURCE_ID
                        } else {
                            BlacklistedSources.HIDDEN_SOURCES -= EH_SOURCE_ID
                            BlacklistedSources.HIDDEN_SOURCES -= EXH_SOURCE_ID
                        }
                        true
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = delegateSourcePreferences.delegateSources(),
                    title = stringResource(SYMR.strings.toggle_delegated_sources),
                    subtitle = stringResource(
                        SYMR.strings.toggle_delegated_sources_summary,
                        stringResource(MR.strings.app_name),
                        AndroidSourceManager.DELEGATED_SOURCES.values.map { it.sourceName }.distinct()
                            .joinToString(),
                    ),
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = unsortedPreferences.logLevel(),
                    title = stringResource(SYMR.strings.log_level),
                    subtitle = stringResource(SYMR.strings.log_level_summary),
                    entries = EHLogLevel.entries.mapIndexed { index, ehLogLevel ->
                        index to "${context.stringResource(ehLogLevel.nameRes)} (${
                            context.stringResource(ehLogLevel.description)
                        })"
                    }.toMap().toImmutableMap(),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = sourcePreferences.enableSourceBlacklist(),
                    title = stringResource(SYMR.strings.enable_source_blacklist),
                    subtitle = stringResource(
                        SYMR.strings.enable_source_blacklist_summary,
                        stringResource(MR.strings.app_name),
                    ),
                ),
                kotlin.run {
                    var enableEncryptDatabase by rememberSaveable { mutableStateOf(false) }

                    if (enableEncryptDatabase) {
                        val dismiss = { enableEncryptDatabase = false }
                        AlertDialog(
                            onDismissRequest = dismiss,
                            title = { Text(text = stringResource(SYMR.strings.encrypt_database)) },
                            text = {
                                Text(
                                    text = remember {
                                        HtmlCompat.fromHtml(
                                            context.stringResource(SYMR.strings.encrypt_database_message),
                                            HtmlCompat.FROM_HTML_MODE_COMPACT,
                                        ).toAnnotatedString()
                                    },
                                )
                            },
                            dismissButton = {
                                TextButton(onClick = dismiss) {
                                    Text(text = stringResource(MR.strings.action_cancel))
                                }
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        dismiss()
                                        securityPreferences.encryptDatabase().set(true)
                                    },
                                ) {
                                    Text(text = stringResource(MR.strings.action_ok))
                                }
                            },
                        )
                    }
                    Preference.PreferenceItem.SwitchPreference(
                        title = stringResource(SYMR.strings.encrypt_database),
                        pref = securityPreferences.encryptDatabase(),
                        subtitle = stringResource(SYMR.strings.encrypt_database_subtitle),
                        onValueChanged = {
                            if (it) {
                                enableEncryptDatabase = true
                                false
                            } else {
                                true
                            }
                        },
                    )
                },
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(SYMR.strings.open_debug_menu),
                    subtitle = remember {
                        HtmlCompat.fromHtml(
                            context.stringResource(SYMR.strings.open_debug_menu_summary),
                            HtmlCompat.FROM_HTML_MODE_COMPACT,
                        ).toAnnotatedString()
                    },
                    onClick = { navigator.push(SettingsDebugScreen()) },
                ),
            ),
        )
    }

    private var job: Job? = null
    // SY <--
}
