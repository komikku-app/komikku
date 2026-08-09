package eu.kanade.presentation.more.settings.screen

import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.download.interactor.CleanInvalidDownloads
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.advanced.ClearDatabaseScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.cache.PagePreviewCache
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.library.MetadataUpdateJob
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.system.setDefaultSettings
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.persistentListOf
import logcat.LogPriority
import tachiyomi.core.common.i18n.pluralStringResource
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.manga.interactor.GetAllManga
import tachiyomi.domain.manga.interactor.ResetViewerFlags
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

object SettingsCleanupScreen : SearchableSettings {
    @Suppress("unused")
    private fun readResolve(): Any = SettingsCleanupScreen

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = KMR.strings.pref_category_cleanup

    @Composable
    override fun getPreferences(): List<Preference> {
        return listOf(
            getCacheGroup(),
            getDatabaseGroup(),
            getNetworkGroup(),
            getLibraryGroup(),
            getDownloadsGroup(),
            getDownloadedChaptersGroup(),
        )
    }

    @Composable
    private fun getNetworkGroup(): Preference.PreferenceGroup {
        val context = LocalContext.current
        val networkHelper = remember { Injekt.get<NetworkHelper>() }

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
            ),
        )
    }

    @Composable
    private fun getLibraryGroup(): Preference.PreferenceGroup {
        val scope = rememberCoroutineScope()
        val context = LocalContext.current

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.label_library),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_refresh_library_covers),
                    onClick = { MetadataUpdateJob.startNow(context) },
                ),
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
    private fun getCacheGroup(): Preference.PreferenceGroup {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val chapterCache = remember { Injekt.get<ChapterCache>() }
        var cacheReadableSizeSema by remember { mutableIntStateOf(0) }
        val cacheReadableSize = remember(cacheReadableSizeSema) { chapterCache.readableSize }

        val pagePreviewCache = remember { Injekt.get<PagePreviewCache>() }
        var pagePreviewReadableSizeSema by remember { mutableIntStateOf(0) }
        val pagePreviewReadableSize = remember(pagePreviewReadableSizeSema) { pagePreviewCache.readableSize }

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_storage_usage),
            preferenceItems = persistentListOf(
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
            ),
        )
    }

    @Composable
    private fun getDatabaseGroup(): Preference.PreferenceGroup {
        val navigator = LocalNavigator.currentOrThrow

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.label_data),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_clear_database),
                    subtitle = stringResource(MR.strings.pref_clear_database_summary),
                    onClick = { navigator.push(ClearDatabaseScreen()) },
                ),
            ),
        )
    }

    @Composable
    private fun getDownloadsGroup(): Preference.PreferenceGroup {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.label_downloaded),
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
                    title = stringResource(KMR.strings.pref_clean_invalid_downloads),
                    subtitle = stringResource(KMR.strings.pref_clean_invalid_downloads_summary),
                    onClick = {
                        scope.launchNonCancellable {
                            Injekt.get<CleanInvalidDownloads>().await()
                            withUIContext {
                                context.toast(KMR.strings.invalid_downloads_cleaned)
                            }
                        }
                    },
                ),
            ),
        )
    }

    @Composable
    fun CleanupDownloadsDialog(
        onDismissRequest: () -> Unit,
        onCleanupDownloads: (removeRead: Boolean, removeNonFavorite: Boolean) -> Unit,
    ) {
        val resources = androidx.compose.ui.platform.LocalResources.current
        val options = remember(resources) { resources.getStringArray(R.array.clean_up_downloads).toList() }
        val selection = remember {
            options.toMutableStateList()
        }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { androidx.compose.material3.Text(text = stringResource(SYMR.strings.clean_up_downloaded_chapters)) },
            text = {
                androidx.compose.foundation.lazy.LazyColumn {
                    options.forEachIndexed { index, option ->
                        item {
                            tachiyomi.presentation.core.components.LabeledCheckbox(
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
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = true,
            ),
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        val removeRead = options[1] in selection
                        val removeNonFavorite = options[2] in selection
                        onCleanupDownloads(removeRead, removeNonFavorite)
                    },
                ) {
                    androidx.compose.material3.Text(text = stringResource(MR.strings.action_ok))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = onDismissRequest) {
                    androidx.compose.material3.Text(text = stringResource(MR.strings.action_cancel))
                }
            },
        )
    }

    @Composable
    private fun getDownloadedChaptersGroup(): Preference.PreferenceGroup {
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        var dialogOpen by remember { mutableStateOf(false) }
        if (dialogOpen) {
            CleanupDownloadsDialog(
                onDismissRequest = { dialogOpen = false },
                onCleanupDownloads = { removeRead, removeNonFavorite ->
                    dialogOpen = false
                    context.toast(SYMR.strings.starting_cleanup)
                    scope.launchNonCancellable {
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

                            mangaFolders.forEach { mangaFolder ->
                                val manga = sourceManga.find { it.second == mangaFolder.name }?.first
                                if (manga == null) {
                                    // download is orphaned delete it
                                    foldersCleared += 1 + (mangaFolder.listFiles().orEmpty().size)
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
}
