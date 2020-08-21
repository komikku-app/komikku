package eu.kanade.tachiyomi.ui.setting

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.core.net.toUri
import androidx.core.text.HtmlCompat
import androidx.preference.PreferenceScreen
import com.afollestad.materialdialogs.MaterialDialog
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.library.LibraryUpdateService
import eu.kanade.tachiyomi.data.library.LibraryUpdateService.Target
import eu.kanade.tachiyomi.data.preference.PreferenceKeys as Keys
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.SourceManager.Companion.DELEGATED_SOURCES
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.util.lang.launchUI
import eu.kanade.tachiyomi.util.preference.defaultValue
import eu.kanade.tachiyomi.util.preference.intListPreference
import eu.kanade.tachiyomi.util.preference.onChange
import eu.kanade.tachiyomi.util.preference.onClick
import eu.kanade.tachiyomi.util.preference.preference
import eu.kanade.tachiyomi.util.preference.preferenceCategory
import eu.kanade.tachiyomi.util.preference.summaryRes
import eu.kanade.tachiyomi.util.preference.switchPreference
import eu.kanade.tachiyomi.util.preference.titleRes
import eu.kanade.tachiyomi.util.system.powerManager
import eu.kanade.tachiyomi.util.system.toast
import exh.EH_SOURCE_ID
import exh.EXH_SOURCE_ID
import exh.debug.SettingsDebugController
import exh.log.EHLogLevel
import exh.source.BlacklistedSources
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

class SettingsAdvancedController : SettingsController() {

    private val network: NetworkHelper by injectLazy()

    private val chapterCache: ChapterCache by injectLazy()

    private val db: DatabaseHelper by injectLazy()

    @SuppressLint("BatteryLife")
    override fun setupPreferenceScreen(screen: PreferenceScreen) = with(screen) {
        titleRes = R.string.pref_category_advanced

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            preference {
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
        }

        preferenceCategory {
            titleRes = R.string.label_data

            preference {
                key = CLEAR_CACHE_KEY
                titleRes = R.string.pref_clear_chapter_cache
                summary = context.getString(R.string.used_cache, chapterCache.readableSize)

                onClick { clearChapterCache() }
            }
            preference {
                titleRes = R.string.pref_clear_database
                summaryRes = R.string.pref_clear_database_summary

                onClick {
                    val ctrl = ClearDatabaseDialogController()
                    ctrl.targetController = this@SettingsAdvancedController
                    ctrl.showDialog(router)
                }
            }
        }

        preferenceCategory {
            titleRes = R.string.label_network

            preference {
                titleRes = R.string.pref_clear_cookies

                onClick {
                    network.cookieManager.removeAll()
                    activity?.toast(R.string.cookies_cleared)
                }
            }
            switchPreference {
                key = Keys.enableDoh
                titleRes = R.string.pref_dns_over_https
                summaryRes = R.string.pref_dns_over_https_summary
                defaultValue = false
            }
        }

        preferenceCategory {
            titleRes = R.string.label_library

            preference {
                titleRes = R.string.pref_refresh_library_covers

                onClick { LibraryUpdateService.start(context, target = Target.COVERS) }
            }
            preference {
                titleRes = R.string.pref_refresh_library_tracking
                summaryRes = R.string.pref_refresh_library_tracking_summary

                onClick { LibraryUpdateService.start(context, target = Target.TRACKING) }
            }
        }

        // --> EXH
        preferenceCategory {
            titleRes = R.string.group_downloader

            preference {
                titleRes = R.string.clean_up_downloaded_chapters
                summaryRes = R.string.delete_unused_chapters

                onClick { cleanupDownloads() }
            }
        }

        preferenceCategory {
            titleRes = R.string.developer_tools
            isPersistent = false

            switchPreference {
                titleRes = R.string.toggle_hentai_features
                summaryRes = R.string.toggle_hentai_features_summary
                key = Keys.eh_is_hentai_enabled
                defaultValue = true

                onChange {
                    if (preferences.eh_isHentaiEnabled().get()) {
                        if (EH_SOURCE_ID !in BlacklistedSources.HIDDEN_SOURCES) {
                            BlacklistedSources.HIDDEN_SOURCES += EH_SOURCE_ID
                        }
                        if (EXH_SOURCE_ID !in BlacklistedSources.HIDDEN_SOURCES) {
                            BlacklistedSources.HIDDEN_SOURCES += EXH_SOURCE_ID
                        }
                    } else {
                        if (EH_SOURCE_ID in BlacklistedSources.HIDDEN_SOURCES) {
                            BlacklistedSources.HIDDEN_SOURCES -= EH_SOURCE_ID
                        }
                        if (EXH_SOURCE_ID in BlacklistedSources.HIDDEN_SOURCES) {
                            BlacklistedSources.HIDDEN_SOURCES -= EXH_SOURCE_ID
                        }
                    }
                    true
                }
            }

            switchPreference {
                titleRes = R.string.toggle_delegated_sources
                key = Keys.eh_delegateSources
                defaultValue = true
                summary = context.getString(R.string.toggle_delegated_sources_summary, context.getString(R.string.app_name), DELEGATED_SOURCES.values.map { it.sourceName }.distinct().joinToString())
            }

            switchPreference {
                titleRes = R.string.toggle_experimental_features
                key = Keys.experimentalFeatures
                defaultValue = false
                summary = context.getString(R.string.toggle_experimental_features_summary)
            }

            intListPreference {
                key = Keys.eh_logLevel
                titleRes = R.string.log_level

                entries = EHLogLevel.values().map {
                    "${context.getString(it.nameRes)} (${context.getString(it.description)})"
                }.toTypedArray()
                entryValues = EHLogLevel.values().mapIndexed { index, _ -> "$index" }.toTypedArray()
                defaultValue = "0"

                summaryRes = R.string.log_level_summary
            }

            switchPreference {
                titleRes = R.string.enable_source_blacklist
                key = Keys.eh_enableSourceBlacklist
                defaultValue = true
                summary = context.getString(R.string.enable_source_blacklist_summary, context.getString(R.string.app_name))
            }

            preference {
                titleRes = R.string.open_debug_menu
                summary = HtmlCompat.fromHtml(context.getString(R.string.open_debug_menu_summary), HtmlCompat.FROM_HTML_MODE_LEGACY)
                onClick { router.pushController(SettingsDebugController().withFadeTransaction()) }
            }
        }
        // <-- EXH
    }

    // SY -->
    private fun cleanupDownloads() {
        if (job?.isActive == true) return
        activity?.toast(R.string.starting_cleanup)
        job = GlobalScope.launch(Dispatchers.IO, CoroutineStart.DEFAULT) {
            val mangaList = db.getMangas().executeAsBlocking()
            val sourceManager: SourceManager = Injekt.get()
            val downloadManager: DownloadManager = Injekt.get()
            var foldersCleared = 0
            mangaList.forEach { manga ->
                val chapterList = db.getChapters(manga).executeAsBlocking()
                val source = sourceManager.getOrStub(manga.source)
                foldersCleared += downloadManager.cleanupChapters(chapterList, manga, source)
            }
            launchUI {
                val activity = activity ?: return@launchUI
                val cleanupString =
                    if (foldersCleared == 0) activity.getString(R.string.no_folders_to_cleanup)
                    else resources!!.getQuantityString(
                        R.plurals.cleanup_done,
                        foldersCleared,
                        foldersCleared
                    )
                activity.toast(cleanupString, Toast.LENGTH_LONG)
            }
        }
    }
    // SY <--

    private fun clearChapterCache() {
        if (activity == null) return
        val files = chapterCache.cacheDir.listFiles() ?: return

        var deletedFiles = 0

        Observable.defer { Observable.from(files) }
            .doOnNext { file ->
                if (chapterCache.removeFileFromCache(file.name)) {
                    deletedFiles++
                }
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnError {
                activity?.toast(R.string.cache_delete_error)
            }
            .doOnCompleted {
                activity?.toast(resources?.getString(R.string.cache_deleted, deletedFiles))
                findPreference(CLEAR_CACHE_KEY)?.summary =
                    resources?.getString(R.string.used_cache, chapterCache.readableSize)
            }
            .subscribe()
    }

    class ClearDatabaseDialogController : DialogController() {
        override fun onCreateDialog(savedViewState: Bundle?): Dialog {
            return MaterialDialog(activity!!)
                .message(R.string.clear_database_confirmation)
                .positiveButton(android.R.string.ok) {
                    (targetController as? SettingsAdvancedController)?.clearDatabase()
                }
                .negativeButton(android.R.string.cancel)
        }
    }

    private fun clearDatabase() {
        db.deleteMangasNotInLibrary().executeAsBlocking()
        db.deleteHistoryNoLastRead().executeAsBlocking()
        activity?.toast(R.string.clear_database_completed)
    }

    private companion object {
        const val CLEAR_CACHE_KEY = "pref_clear_cache_key"

        private var job: Job? = null
    }
}
