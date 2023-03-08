package eu.kanade.presentation.more.settings.screen

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.core.text.HtmlCompat
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.UnsortedPreferences
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.manga.interactor.GetAllManga
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.util.collectAsState
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.cache.PagePreviewCache
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.data.preference.PreferenceValues
import eu.kanade.tachiyomi.data.track.TrackManager
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
import eu.kanade.tachiyomi.util.CrashLogUtil
import eu.kanade.tachiyomi.util.storage.DiskUtil
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import logcat.LogPriority
import okhttp3.Headers
import tachiyomi.core.util.lang.launchNonCancellable
import tachiyomi.core.util.lang.withUIContext
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.chapter.interactor.GetChapterByMangaId
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

object SettingsAdvancedScreen : SearchableSettings {
    @ReadOnlyComposable
    @Composable
    @StringRes
    override fun getTitleRes() = R.string.pref_category_advanced

    @Composable
    override fun getPreferences(): List<Preference> {
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val basePreferences = remember { Injekt.get<BasePreferences>() }
        val networkPreferences = remember { Injekt.get<NetworkPreferences>() }

        return listOf(
            /* SY --> Preference.PreferenceItem.SwitchPreference(
                pref = basePreferences.acraEnabled(),
                title = stringResource(R.string.pref_enable_acra),
                subtitle = stringResource(R.string.pref_acra_summary),
                enabled = isPreviewBuildType || isReleaseBuildType,
            ), SY <-- */
            Preference.PreferenceItem.TextPreference(
                title = stringResource(R.string.pref_dump_crash_logs),
                subtitle = stringResource(R.string.pref_dump_crash_logs_summary),
                onClick = {
                    scope.launch {
                        CrashLogUtil(context).dumpLogs()
                    }
                },
            ),
            /* SY --> Preference.PreferenceItem.SwitchPreference(
                pref = networkPreferences.verboseLogging(),
                title = stringResource(R.string.pref_verbose_logging),
                subtitle = stringResource(R.string.pref_verbose_logging_summary),
                onValueChanged = {
                    context.toast(R.string.requires_app_restart)
                    true
                },
            ), SY <-- */
            getBackgroundActivityGroup(),
            getDataGroup(),
            getNetworkGroup(networkPreferences = networkPreferences),
            getLibraryGroup(),
            getExtensionsGroup(basePreferences = basePreferences),
            // SY -->
            getDownloaderGroup(),
            getDataSaverGroup(),
            getDeveloperToolsGroup(),
            // SY <--
        )
    }

    @Composable
    private fun getBackgroundActivityGroup(): Preference.PreferenceGroup {
        val context = LocalContext.current
        val uriHandler = LocalUriHandler.current
        val navigator = LocalNavigator.currentOrThrow

        return Preference.PreferenceGroup(
            title = stringResource(R.string.label_background_activity),
            preferenceItems = listOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(R.string.pref_disable_battery_optimization),
                    subtitle = stringResource(R.string.pref_disable_battery_optimization_summary),
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
                                context.toast(R.string.battery_optimization_setting_activity_not_found)
                            }
                        } else {
                            context.toast(R.string.battery_optimization_disabled)
                        }
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = "Don't kill my app!",
                    subtitle = stringResource(R.string.about_dont_kill_my_app),
                    onClick = { uriHandler.openUri("https://dontkillmyapp.com/") },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(R.string.pref_worker_info),
                    onClick = { navigator.push(WorkerInfoScreen) },
                ),
            ),
        )
    }

    @Composable
    private fun getDataGroup(): Preference.PreferenceGroup {
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val libraryPreferences = remember { Injekt.get<LibraryPreferences>() }

        val chapterCache = remember { Injekt.get<ChapterCache>() }
        var readableSizeSema by remember { mutableStateOf(0) }
        val readableSize = remember(readableSizeSema) { chapterCache.readableSize }

        // SY -->
        val pagePreviewCache = remember { Injekt.get<PagePreviewCache>() }
        var pagePreviewReadableSizeSema by remember { mutableStateOf(0) }
        val pagePreviewReadableSize = remember(pagePreviewReadableSizeSema) { pagePreviewCache.readableSize }
        // SY <--

        return Preference.PreferenceGroup(
            title = stringResource(R.string.label_data),
            preferenceItems = listOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(R.string.pref_clear_chapter_cache),
                    subtitle = stringResource(R.string.used_cache, readableSize),
                    onClick = {
                        scope.launchNonCancellable {
                            try {
                                val deletedFiles = chapterCache.clear()
                                withUIContext {
                                    context.toast(context.getString(R.string.cache_deleted, deletedFiles))
                                    readableSizeSema++
                                }
                            } catch (e: Throwable) {
                                logcat(LogPriority.ERROR, e)
                                withUIContext { context.toast(R.string.cache_delete_error) }
                            }
                        }
                    },
                ),
                // SY -->
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(R.string.pref_clear_page_preview_cache),
                    subtitle = stringResource(R.string.used_cache, pagePreviewReadableSize),
                    onClick = {
                        scope.launchNonCancellable {
                            try {
                                val deletedFiles = pagePreviewCache.clear()
                                withUIContext {
                                    context.toast(context.getString(R.string.cache_deleted, deletedFiles))
                                    pagePreviewReadableSizeSema++
                                }
                            } catch (e: Throwable) {
                                logcat(LogPriority.ERROR, e)
                                withUIContext { context.toast(R.string.cache_delete_error) }
                            }
                        }
                    },
                ),
                // SY <--
                Preference.PreferenceItem.SwitchPreference(
                    pref = libraryPreferences.autoClearChapterCache(),
                    title = stringResource(R.string.pref_auto_clear_chapter_cache),
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(R.string.pref_invalidate_download_cache),
                    subtitle = stringResource(R.string.pref_invalidate_download_cache_summary),
                    onClick = { Injekt.get<DownloadCache>().invalidateCache() },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(R.string.pref_clear_database),
                    subtitle = stringResource(R.string.pref_clear_database_summary),
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
            title = stringResource(R.string.label_network),
            preferenceItems = listOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(R.string.pref_clear_cookies),
                    onClick = {
                        networkHelper.cookieJar.removeAll()
                        context.toast(R.string.cookies_cleared)
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(R.string.pref_clear_webview_data),
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
                            context.applicationInfo?.dataDir?.let { File("$it/app_webview/").deleteRecursively() }
                            context.toast(R.string.webview_data_deleted)
                        } catch (e: Throwable) {
                            logcat(LogPriority.ERROR, e)
                            context.toast(R.string.cache_delete_error)
                        }
                    },
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = networkPreferences.dohProvider(),
                    title = stringResource(R.string.pref_dns_over_https),
                    entries = mapOf(
                        -1 to stringResource(R.string.disabled),
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
                        context.toast(R.string.requires_app_restart)
                        true
                    },
                ),
                Preference.PreferenceItem.EditTextPreference(
                    pref = userAgentPref,
                    title = stringResource(R.string.pref_user_agent_string),
                    onValueChanged = {
                        try {
                            // OkHttp checks for valid values internally
                            Headers.Builder().add("User-Agent", it)
                        } catch (_: IllegalArgumentException) {
                            context.toast(R.string.error_user_agent_string_invalid)
                            return@EditTextPreference false
                        }
                        true
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(R.string.pref_reset_user_agent_string),
                    enabled = remember(userAgent) { userAgent != userAgentPref.defaultValue() },
                    onClick = {
                        userAgentPref.delete()
                        context.toast(R.string.requires_app_restart)
                    },
                ),
            ),
        )
    }

    @Composable
    private fun getLibraryGroup(): Preference.PreferenceGroup {
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val trackManager = remember { Injekt.get<TrackManager>() }

        return Preference.PreferenceGroup(
            title = stringResource(R.string.label_library),
            preferenceItems = listOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(R.string.pref_refresh_library_covers),
                    onClick = { LibraryUpdateJob.startNow(context, target = LibraryUpdateJob.Target.COVERS) },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(R.string.pref_refresh_library_tracking),
                    subtitle = stringResource(R.string.pref_refresh_library_tracking_summary),
                    enabled = trackManager.hasLoggedServices(),
                    onClick = { LibraryUpdateJob.startNow(context, target = LibraryUpdateJob.Target.TRACKING) },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(R.string.pref_reset_viewer_flags),
                    subtitle = stringResource(R.string.pref_reset_viewer_flags_summary),
                    onClick = {
                        scope.launchNonCancellable {
                            val success = Injekt.get<MangaRepository>().resetViewerFlags()
                            withUIContext {
                                val message = if (success) {
                                    R.string.pref_reset_viewer_flags_success
                                } else {
                                    R.string.pref_reset_viewer_flags_error
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
    private fun getExtensionsGroup(
        basePreferences: BasePreferences,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current
        val uriHandler = LocalUriHandler.current
        val extensionInstallerPref = basePreferences.extensionInstaller()
        var shizukuMissing by rememberSaveable { mutableStateOf(false) }

        if (shizukuMissing) {
            val dismiss = { shizukuMissing = false }
            AlertDialog(
                onDismissRequest = dismiss,
                title = { Text(text = stringResource(R.string.ext_installer_shizuku)) },
                text = { Text(text = stringResource(R.string.ext_installer_shizuku_unavailable_dialog)) },
                dismissButton = {
                    TextButton(onClick = dismiss) {
                        Text(text = stringResource(R.string.action_cancel))
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            dismiss()
                            uriHandler.openUri("https://shizuku.rikka.app/download")
                        },
                    ) {
                        Text(text = stringResource(android.R.string.ok))
                    }
                },
            )
        }
        return Preference.PreferenceGroup(
            title = stringResource(R.string.label_extensions),
            preferenceItems = listOf(
                Preference.PreferenceItem.ListPreference(
                    pref = extensionInstallerPref,
                    title = stringResource(R.string.ext_installer_pref),
                    entries = extensionInstallerPref.entries
                        .associateWith { stringResource(it.titleResId) },
                    onValueChanged = {
                        if (it == PreferenceValues.ExtensionInstaller.SHIZUKU &&
                            !context.isShizukuInstalled
                        ) {
                            shizukuMissing = true
                            false
                        } else {
                            true
                        }
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
            title = { Text(text = stringResource(R.string.clean_up_downloaded_chapters)) },
            text = {
                LazyColumn {
                    options.forEachIndexed { index, option ->
                        item {
                            val isSelected = index == 0 || selection.contains(option)
                            val onSelectionChanged = {
                                when (!isSelected) {
                                    true -> selection.add(option)
                                    false -> selection.remove(option)
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectionChanged() },
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { onSelectionChanged() },
                                )
                                Text(
                                    text = option,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(start = 12.dp),
                                )
                            }
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
                    Text(text = stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(android.R.string.cancel))
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
                    context.toast(R.string.starting_cleanup)
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
                                    sourceManga.find { (_, folderName) -> folderName == mangaFolder.name }?.first
                                if (manga == null) {
                                    // download is orphaned delete it
                                    foldersCleared += 1 + (
                                        mangaFolder.listFiles()
                                            .orEmpty().size
                                        )
                                    mangaFolder.delete()
                                } else {
                                    val chapterList = Injekt.get<GetChapterByMangaId>().await(manga.id)
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
                                    context.getString(R.string.no_folders_to_cleanup)
                                } else {
                                    context.resources!!.getQuantityString(
                                        R.plurals.cleanup_done,
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
            title = stringResource(R.string.download_notifier_downloader_title),
            preferenceItems = listOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(R.string.clean_up_downloaded_chapters),
                    subtitle = stringResource(R.string.delete_unused_chapters),
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
            title = stringResource(R.string.data_saver),
            preferenceItems = listOf(
                Preference.PreferenceItem.SwitchPreference(
                    pref = sourcePreferences.dataSaver(),
                    title = stringResource(R.string.data_saver),
                    subtitle = stringResource(R.string.data_saver_summary),
                ),
                Preference.PreferenceItem.EditTextPreference(
                    pref = sourcePreferences.dataSaverServer(),
                    title = stringResource(R.string.data_saver_server),
                    subtitle = stringResource(R.string.data_saver_server_summary),
                    enabled = dataSaver,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = sourcePreferences.dataSaverDownloader(),
                    title = stringResource(R.string.data_saver_downloader),
                    enabled = dataSaver,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = sourcePreferences.dataSaverIgnoreJpeg(),
                    title = stringResource(R.string.data_saver_ignore_jpeg),
                    enabled = dataSaver,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = sourcePreferences.dataSaverIgnoreGif(),
                    title = stringResource(R.string.data_saver_ignore_gif),
                    enabled = dataSaver,
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = sourcePreferences.dataSaverImageQuality(),
                    title = stringResource(R.string.data_saver_image_quality),
                    subtitle = stringResource(R.string.data_saver_image_quality_summary),
                    entries = listOf(
                        "10%",
                        "20%",
                        "40%",
                        "50%",
                        "70%",
                        "80%",
                        "90%",
                        "95%",
                    ).associateBy { it.trimEnd('%').toInt() },
                    enabled = dataSaver,
                ),
                kotlin.run {
                    val dataSaverImageFormatJpeg by sourcePreferences.dataSaverImageFormatJpeg().collectAsState()
                    Preference.PreferenceItem.SwitchPreference(
                        pref = sourcePreferences.dataSaverImageFormatJpeg(),
                        title = stringResource(R.string.data_saver_image_format),
                        subtitle = if (dataSaverImageFormatJpeg) {
                            stringResource(R.string.data_saver_image_format_summary_on)
                        } else {
                            stringResource(R.string.data_saver_image_format_summary_off)
                        },
                        enabled = dataSaver,
                    )
                },
                Preference.PreferenceItem.SwitchPreference(
                    pref = sourcePreferences.dataSaverColorBW(),
                    title = stringResource(R.string.data_saver_color_bw),
                    enabled = dataSaver,
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
        return Preference.PreferenceGroup(
            title = stringResource(R.string.developer_tools),
            preferenceItems = listOf(
                Preference.PreferenceItem.SwitchPreference(
                    pref = unsortedPreferences.isHentaiEnabled(),
                    title = stringResource(R.string.toggle_hentai_features),
                    subtitle = stringResource(R.string.toggle_hentai_features_summary),
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
                    title = stringResource(R.string.toggle_delegated_sources),
                    subtitle = stringResource(
                        R.string.toggle_delegated_sources_summary,
                        stringResource(R.string.app_name),
                        AndroidSourceManager.DELEGATED_SOURCES.values.map { it.sourceName }.distinct()
                            .joinToString(),
                    ),
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = unsortedPreferences.logLevel(),
                    title = stringResource(R.string.log_level),
                    subtitle = stringResource(R.string.log_level_summary),
                    entries = EHLogLevel.values().mapIndexed { index, ehLogLevel ->
                        index to "${context.getString(ehLogLevel.nameRes)} (${
                        context.getString(ehLogLevel.description)
                        })"
                    }.toMap(),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = sourcePreferences.enableSourceBlacklist(),
                    title = stringResource(R.string.enable_source_blacklist),
                    subtitle = stringResource(
                        R.string.enable_source_blacklist_summary,
                        stringResource(R.string.app_name),
                    ),
                ),
                Preference.PreferenceItem.AnnotatedTextPreference(
                    title = stringResource(R.string.open_debug_menu),
                    annotatedSubtitle = remember {
                        HtmlCompat.fromHtml(context.getString(R.string.open_debug_menu_summary), HtmlCompat.FROM_HTML_MODE_COMPACT)
                            .toAnnotatedString()
                    },
                    onClick = { navigator.push(SettingsDebugScreen()) },
                ),
            ),
        )
    }

    private var job: Job? = null
    // SY <--
}
