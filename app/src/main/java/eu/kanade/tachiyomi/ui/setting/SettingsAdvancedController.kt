package eu.kanade.tachiyomi.ui.setting

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import androidx.core.text.HtmlCompat
import androidx.preference.PreferenceScreen
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.domain.chapter.interactor.GetChapterByMangaId
import eu.kanade.domain.chapter.model.toDbChapter
import eu.kanade.domain.manga.interactor.GetAllManga
import eu.kanade.domain.manga.repository.MangaRepository
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.library.LibraryUpdateService
import eu.kanade.tachiyomi.data.library.LibraryUpdateService.Target
import eu.kanade.tachiyomi.data.preference.PreferenceValues
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.PREF_DOH_360
import eu.kanade.tachiyomi.network.PREF_DOH_ADGUARD
import eu.kanade.tachiyomi.network.PREF_DOH_ALIDNS
import eu.kanade.tachiyomi.network.PREF_DOH_CLOUDFLARE
import eu.kanade.tachiyomi.network.PREF_DOH_DNSPOD
import eu.kanade.tachiyomi.network.PREF_DOH_GOOGLE
import eu.kanade.tachiyomi.network.PREF_DOH_QUAD101
import eu.kanade.tachiyomi.network.PREF_DOH_QUAD9
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.SourceManager.Companion.DELEGATED_SOURCES
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.ui.base.controller.openInBrowser
import eu.kanade.tachiyomi.ui.base.controller.pushController
import eu.kanade.tachiyomi.ui.setting.database.ClearDatabaseController
import eu.kanade.tachiyomi.util.CrashLogUtil
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.lang.withUIContext
import eu.kanade.tachiyomi.util.preference.bindTo
import eu.kanade.tachiyomi.util.preference.defaultValue
import eu.kanade.tachiyomi.util.preference.editTextPreference
import eu.kanade.tachiyomi.util.preference.entriesRes
import eu.kanade.tachiyomi.util.preference.intListPreference
import eu.kanade.tachiyomi.util.preference.listPreference
import eu.kanade.tachiyomi.util.preference.onChange
import eu.kanade.tachiyomi.util.preference.onClick
import eu.kanade.tachiyomi.util.preference.preference
import eu.kanade.tachiyomi.util.preference.preferenceCategory
import eu.kanade.tachiyomi.util.preference.summaryRes
import eu.kanade.tachiyomi.util.preference.switchPreference
import eu.kanade.tachiyomi.util.preference.titleRes
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.isPackageInstalled
import eu.kanade.tachiyomi.util.system.logcat
import eu.kanade.tachiyomi.util.system.powerManager
import eu.kanade.tachiyomi.util.system.setDefaultSettings
import eu.kanade.tachiyomi.util.system.toast
import exh.debug.SettingsDebugController
import exh.log.EHLogLevel
import exh.source.BlacklistedSources
import exh.source.EH_SOURCE_ID
import exh.source.EXH_SOURCE_ID
import kotlinx.coroutines.Job
import logcat.LogPriority
import rikka.sui.Sui
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.io.File
import eu.kanade.tachiyomi.data.preference.PreferenceKeys as Keys

class SettingsAdvancedController(
    private val mangaRepository: MangaRepository = Injekt.get(),
) : SettingsController() {

    private val network: NetworkHelper by injectLazy()
    private val chapterCache: ChapterCache by injectLazy()
    private val trackManager: TrackManager by injectLazy()
    private val getAllManga: GetAllManga by injectLazy()
    private val getChapterByMangaId: GetChapterByMangaId by injectLazy()

    @SuppressLint("BatteryLife")
    override fun setupPreferenceScreen(screen: PreferenceScreen) = screen.apply {
        titleRes = R.string.pref_category_advanced

        preference {
            key = "dump_crash_logs"
            titleRes = R.string.pref_dump_crash_logs
            summaryRes = R.string.pref_dump_crash_logs_summary

            onClick {
                CrashLogUtil(context).dumpLogs()
            }
        }

        /*switchPreference {
            key = Keys.verboseLogging
            titleRes = R.string.pref_verbose_logging
            summaryRes = R.string.pref_verbose_logging_summary
            defaultValue = isDevFlavor

            onChange {
                activity?.toast(R.string.requires_app_restart)
                true
            }
        }*/

        preferenceCategory {
            titleRes = R.string.label_background_activity

            preference {
                key = "pref_disable_battery_optimization"
                titleRes = R.string.pref_disable_battery_optimization
                summaryRes = R.string.pref_disable_battery_optimization_summary

                onClick {
                    val packageName: String = context.packageName
                    if (!context.powerManager.isIgnoringBatteryOptimizations(packageName)) {
                        try {
                            val intent = Intent().apply {
                                action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                                data = "package:$packageName".toUri()
                            }
                            startActivity(intent)
                        } catch (e: ActivityNotFoundException) {
                            context.toast(R.string.battery_optimization_setting_activity_not_found)
                        }
                    } else {
                        context.toast(R.string.battery_optimization_disabled)
                    }
                }
            }

            preference {
                key = "pref_dont_kill_my_app"
                title = "Don't kill my app!"
                summaryRes = R.string.about_dont_kill_my_app

                onClick {
                    openInBrowser("https://dontkillmyapp.com/")
                }
            }
        }

        preferenceCategory {
            titleRes = R.string.label_data

            preference {
                key = CLEAR_CACHE_KEY
                titleRes = R.string.pref_clear_chapter_cache
                summary = context.getString(R.string.used_cache, chapterCache.readableSize)

                onClick { clearChapterCache() }
            }
            switchPreference {
                key = Keys.autoClearChapterCache
                titleRes = R.string.pref_auto_clear_chapter_cache
                defaultValue = false
            }
            preference {
                key = "pref_clear_database"
                titleRes = R.string.pref_clear_database
                summaryRes = R.string.pref_clear_database_summary

                onClick {
                    router.pushController(ClearDatabaseController())
                }
            }
        }

        preferenceCategory {
            titleRes = R.string.label_network

            preference {
                key = "pref_clear_cookies"
                titleRes = R.string.pref_clear_cookies

                onClick {
                    network.cookieManager.removeAll()
                    activity?.toast(R.string.cookies_cleared)
                }
            }
            preference {
                key = "pref_clear_webview_data"
                titleRes = R.string.pref_clear_webview_data

                onClick { clearWebViewData() }
            }
            intListPreference {
                key = Keys.dohProvider
                titleRes = R.string.pref_dns_over_https
                entries = arrayOf(
                    context.getString(R.string.disabled),
                    "Cloudflare",
                    "Google",
                    "AdGuard",
                    "Quad9",
                    "AliDNS",
                    "DNSPod",
                    "360",
                    "Quad 101",
                )
                entryValues = arrayOf(
                    "-1",
                    PREF_DOH_CLOUDFLARE.toString(),
                    PREF_DOH_GOOGLE.toString(),
                    PREF_DOH_ADGUARD.toString(),
                    PREF_DOH_QUAD9.toString(),
                    PREF_DOH_ALIDNS.toString(),
                    PREF_DOH_DNSPOD.toString(),
                    PREF_DOH_360.toString(),
                    PREF_DOH_QUAD101.toString(),
                )
                defaultValue = "-1"
                summary = "%s"

                onChange {
                    activity?.toast(R.string.requires_app_restart)
                    true
                }
            }
        }

        preferenceCategory {
            titleRes = R.string.label_library

            preference {
                key = "pref_refresh_library_covers"
                titleRes = R.string.pref_refresh_library_covers

                onClick { LibraryUpdateService.start(context, target = Target.COVERS) }
            }
            if (trackManager.hasLoggedServices()) {
                preference {
                    key = "pref_refresh_library_tracking"
                    titleRes = R.string.pref_refresh_library_tracking
                    summaryRes = R.string.pref_refresh_library_tracking_summary

                    onClick { LibraryUpdateService.start(context, target = Target.TRACKING) }
                }
            }
            preference {
                key = "pref_reset_viewer_flags"
                titleRes = R.string.pref_reset_viewer_flags
                summaryRes = R.string.pref_reset_viewer_flags_summary

                onClick { resetViewerFlags() }
            }
        }

        preferenceCategory {
            titleRes = R.string.label_extensions

            listPreference {
                bindTo(preferences.extensionInstaller())
                titleRes = R.string.ext_installer_pref
                summary = "%s"

                // PackageInstaller doesn't work on MIUI properly for non-allowlisted apps
                val values = if (DeviceUtil.isMiui) {
                    PreferenceValues.ExtensionInstaller.values()
                        .filter { it != PreferenceValues.ExtensionInstaller.PACKAGEINSTALLER }
                } else {
                    PreferenceValues.ExtensionInstaller.values().toList()
                }

                entriesRes = values.map { it.titleResId }.toTypedArray()
                entryValues = values.map { it.name }.toTypedArray()

                onChange {
                    if (it == PreferenceValues.ExtensionInstaller.SHIZUKU.name &&
                        !(context.isPackageInstalled("moe.shizuku.privileged.api") || Sui.isSui())
                    ) {
                        MaterialAlertDialogBuilder(context)
                            .setTitle(R.string.ext_installer_shizuku)
                            .setMessage(R.string.ext_installer_shizuku_unavailable_dialog)
                            .setPositiveButton(android.R.string.ok) { _, _ ->
                                openInBrowser("https://shizuku.rikka.app/download")
                            }
                            .setNegativeButton(android.R.string.cancel, null)
                            .show()
                        false
                    } else {
                        true
                    }
                }
            }
        }

        preferenceCategory {
            titleRes = R.string.pref_category_display

            listPreference {
                bindTo(preferences.tabletUiMode())
                titleRes = R.string.pref_tablet_ui_mode
                summary = "%s"
                entriesRes = PreferenceValues.TabletUiMode.values().map { it.titleResId }.toTypedArray()
                entryValues = PreferenceValues.TabletUiMode.values().map { it.name }.toTypedArray()

                onChange {
                    activity?.toast(R.string.requires_app_restart)
                    true
                }
            }
        }

        // --> EXH
        preferenceCategory {
            titleRes = R.string.download_notifier_downloader_title

            preference {
                key = "clean_up_downloaded_chapters"
                titleRes = R.string.clean_up_downloaded_chapters
                summaryRes = R.string.delete_unused_chapters

                onClick {
                    val ctrl = CleanupDownloadsDialogController()
                    ctrl.targetController = this@SettingsAdvancedController
                    ctrl.showDialog(router)
                }
            }
        }

        preferenceCategory {
            titleRes = R.string.data_saver

            switchPreference {
                bindTo(preferences.dataSaver())
                titleRes = R.string.data_saver
                summaryRes = R.string.data_saver_summary
            }

            editTextPreference {
                bindTo(preferences.dataSaverServer())
                titleRes = R.string.data_saver_server
                summaryRes = R.string.data_saver_server_summary

                visibleIf(preferences.dataSaver()) { it }
            }

            switchPreference {
                bindTo(preferences.dataSaverDownloader())
                titleRes = R.string.data_saver_downloader

                visibleIf(preferences.dataSaver()) { it }
            }

            switchPreference {
                bindTo(preferences.dataSaverIgnoreJpeg())
                titleRes = R.string.data_saver_ignore_jpeg

                visibleIf(preferences.dataSaver()) { it }
            }

            switchPreference {
                bindTo(preferences.dataSaverIgnoreGif())
                titleRes = R.string.data_saver_ignore_gif

                visibleIf(preferences.dataSaver()) { it }
            }

            intListPreference {
                bindTo(preferences.dataSaverImageQuality())
                titleRes = R.string.data_saver_image_quality
                entries = arrayOf("10%", "20%", "40%", "50%", "70%", "80%", "90%", "95%")
                entryValues = entries.map { it.trimEnd('%') }.toTypedArray()
                summaryRes = R.string.data_saver_image_quality_summary

                visibleIf(preferences.dataSaver()) { it }
            }

            switchPreference {
                bindTo(preferences.dataSaverImageFormatJpeg())
                titleRes = R.string.data_saver_image_format
                summaryOn = context.getString(R.string.data_saver_image_format_summary_on)
                summaryOff = context.getString(R.string.data_saver_image_format_summary_off)

                visibleIf(preferences.dataSaver()) { it }
            }

            switchPreference {
                bindTo(preferences.dataSaverColorBW())
                titleRes = R.string.data_saver_color_bw

                visibleIf(preferences.dataSaver()) { it }
            }
        }

        preferenceCategory {
            titleRes = R.string.developer_tools
            isPersistent = false

            switchPreference {
                bindTo(preferences.isHentaiEnabled())
                titleRes = R.string.toggle_hentai_features
                summaryRes = R.string.toggle_hentai_features_summary

                onChange {
                    if (preferences.isHentaiEnabled().get()) {
                        BlacklistedSources.HIDDEN_SOURCES += EH_SOURCE_ID
                        BlacklistedSources.HIDDEN_SOURCES += EXH_SOURCE_ID
                    } else {
                        BlacklistedSources.HIDDEN_SOURCES -= EH_SOURCE_ID
                        BlacklistedSources.HIDDEN_SOURCES -= EXH_SOURCE_ID
                    }
                    true
                }
            }

            switchPreference {
                bindTo(preferences.delegateSources())
                titleRes = R.string.toggle_delegated_sources
                summary = context.getString(R.string.toggle_delegated_sources_summary, context.getString(R.string.app_name), DELEGATED_SOURCES.values.map { it.sourceName }.distinct().joinToString())
            }

            intListPreference {
                bindTo(preferences.logLevel())
                titleRes = R.string.log_level

                entries = EHLogLevel.values().map {
                    "${context.getString(it.nameRes)} (${context.getString(it.description)})"
                }.toTypedArray()
                entryValues = EHLogLevel.values().mapIndexed { index, _ -> "$index" }.toTypedArray()

                summaryRes = R.string.log_level_summary
            }

            switchPreference {
                bindTo(preferences.enableSourceBlacklist())
                titleRes = R.string.enable_source_blacklist
                summary = context.getString(R.string.enable_source_blacklist_summary, context.getString(R.string.app_name))
            }

            preference {
                key = "pref_open_debug_menu"
                titleRes = R.string.open_debug_menu
                summary = HtmlCompat.fromHtml(context.getString(R.string.open_debug_menu_summary), HtmlCompat.FROM_HTML_MODE_LEGACY)
                onClick { router.pushController(SettingsDebugController()) }
            }
        }
        // <-- EXH
    }

    // SY -->
    class CleanupDownloadsDialogController : DialogController() {
        override fun onCreateDialog(savedViewState: Bundle?): Dialog {
            val options = activity!!.resources.getStringArray(R.array.clean_up_downloads)
            val selected = options.map { true }.toBooleanArray()
            return MaterialAlertDialogBuilder(activity!!)
                .setTitle(R.string.clean_up_downloaded_chapters)
                .setMultiChoiceItems(options, selected) { dialog, which, checked ->
                    if (which == 0) {
                        (dialog as AlertDialog).listView.setItemChecked(which, true)
                    } else {
                        selected[which] = checked
                    }
                }
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    (targetController as? SettingsAdvancedController)?.cleanupDownloads(
                        selected[1],
                        selected[2],
                    )
                }
                .setNegativeButton(android.R.string.cancel, null)
                .create()
        }
    }

    private fun cleanupDownloads(removeRead: Boolean, removeNonFavorite: Boolean) {
        if (job?.isActive == true) return
        activity?.toast(R.string.starting_cleanup)
        job = launchIO {
            val mangaList = getAllManga.await()
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
                    val manga = sourceManga.find { (_, folderName) -> folderName == mangaFolder.name }?.first
                    if (manga == null) {
                        // download is orphaned delete it
                        foldersCleared += 1 + (mangaFolder.listFiles().orEmpty().size)
                        mangaFolder.delete()
                    } else {
                        val chapterList = getChapterByMangaId.await(manga.id)
                        foldersCleared += downloadManager.cleanupChapters(chapterList.map { it.toDbChapter() }, manga, source, removeRead, removeNonFavorite)
                    }
                }
            }
            withUIContext {
                val activity = activity ?: return@withUIContext
                val cleanupString =
                    if (foldersCleared == 0) activity.getString(R.string.no_folders_to_cleanup)
                    else resources!!.getQuantityString(
                        R.plurals.cleanup_done,
                        foldersCleared,
                        foldersCleared,
                    )
                activity.toast(cleanupString, Toast.LENGTH_LONG)
            }
        }
    }
    // SY <--

    private fun clearChapterCache() {
        val activity = activity ?: return
        launchIO {
            try {
                val deletedFiles = chapterCache.clear()
                withUIContext {
                    activity.toast(resources?.getString(R.string.cache_deleted, deletedFiles))
                    findPreference(CLEAR_CACHE_KEY)?.summary =
                        resources?.getString(R.string.used_cache, chapterCache.readableSize)
                }
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
                withUIContext { activity.toast(R.string.cache_delete_error) }
            }
        }
    }

    private fun clearWebViewData() {
        val activity = activity ?: return
        try {
            val webview = WebView(activity)
            webview.setDefaultSettings()
            webview.clearCache(true)
            webview.clearFormData()
            webview.clearHistory()
            webview.clearSslPreferences()
            WebStorage.getInstance().deleteAllData()
            activity.applicationInfo?.dataDir?.let { File("$it/app_webview/").deleteRecursively() }
            activity.toast(R.string.webview_data_deleted)
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e)
            activity.toast(R.string.cache_delete_error)
        }
    }

    private fun resetViewerFlags() {
        val activity = activity ?: return
        launchIO {
            val success = mangaRepository.resetViewerFlags()
            withUIContext {
                val message = if (success) {
                    R.string.pref_reset_viewer_flags_success
                } else {
                    R.string.pref_reset_viewer_flags_error
                }
                activity.toast(message)
            }
        }
    }

    private companion object {
        // SY -->
        private var job: Job? = null
        // SY <--
    }
}

private const val CLEAR_CACHE_KEY = "pref_clear_cache_key"
