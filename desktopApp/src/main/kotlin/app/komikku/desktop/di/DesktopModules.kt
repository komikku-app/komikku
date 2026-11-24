package app.komikku.desktop.di

import android.app.Application
import app.cash.sqldelight.db.SqlDriver
import app.komikku.desktop.backup.DesktopBackupJobs
import app.komikku.desktop.backup.DesktopBackupService
import app.komikku.desktop.compat.DesktopPreferenceStores
import app.komikku.desktop.extension.ExtensionRuntime
import app.komikku.desktop.extensions.DesktopExtensionManager
import app.komikku.desktop.library.desktopLibraryUpdateRunner
import app.komikku.desktop.notification.DesktopNotifications
import app.komikku.desktop.notification.DesktopNotifier
import app.komikku.desktop.notification.DesktopTray
import app.komikku.desktop.notification.DesktopTrayPreferences
import app.komikku.desktop.platform.AwtFileDialogs
import app.komikku.desktop.platform.DesktopFileDialogs
import app.komikku.desktop.scheduler.DesktopSchedulerPreferences
import app.komikku.desktop.scheduler.desktopScheduler
import app.komikku.desktop.security.DesktopAppLock
import app.komikku.desktop.source.DesktopSourceManager
import app.komikku.desktop.track.DesktopReaderTrackerHook
import app.komikku.desktop.ui.reader.ReaderLauncher
import app.komikku.desktop.updater.DesktopAppUpdateChecker
import app.komikku.desktop.updater.DesktopUpdaterConfig
import app.komikku.desktop.webview.DesktopCloudflare
import app.komikku.desktop.webview.DesktopCloudflarePreferences
import app.komikku.desktop.webview.DesktopWebView
import app.komikku.desktop.webview.FlareSolverrBackend
import app.komikku.desktop.window.DesktopWindowPreferences
import app.komikku.shared.download.DesktopDownloadEvents
import app.komikku.shared.download.DownloadNotificationSink
import app.komikku.shared.extension.GetPlatformExtensionsByType
import app.komikku.shared.extension.PlatformExtensionManager
import app.komikku.shared.jssource.JsSourcePreferences
import app.komikku.shared.jssource.MangayomiSourceStore
import app.komikku.shared.library.LibraryUpdateRunner
import app.komikku.shared.reader.ReaderProgressSaver
import app.komikku.shared.reader.ReaderTrackerHook
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.chapter.interactor.SetReadStatus
import eu.kanade.domain.chapter.interactor.SyncChaptersWithSource
import eu.kanade.domain.download.interactor.DeleteDownload
import eu.kanade.domain.extension.interactor.GetExtensionSources
import eu.kanade.domain.extension.interactor.RefreshExtensionStores
import eu.kanade.domain.manga.interactor.GetExcludedScanlators
import eu.kanade.domain.manga.interactor.SetMangaViewerFlags
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.source.interactor.GetEnabledSources
import eu.kanade.domain.source.interactor.GetExhSavedSearch
import eu.kanade.domain.source.interactor.GetIncognitoState
import eu.kanade.domain.source.interactor.GetLanguagesWithSources
import eu.kanade.domain.source.interactor.ToggleLanguage
import eu.kanade.domain.source.interactor.ToggleSource
import eu.kanade.domain.source.interactor.ToggleSourcePin
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.track.interactor.AddTracks
import eu.kanade.domain.track.interactor.RefreshTracks
import eu.kanade.domain.track.interactor.SyncChapterProgressWithTrack
import eu.kanade.domain.track.interactor.TrackChapter
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.domain.track.store.DelayedTrackingStore
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.core.security.PrivacyPreferences
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.network.JavaScriptEngine
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.NetworkPreferences
import eu.kanade.tachiyomi.source.online.MetadataSource
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.util.system.toast
import exh.pref.DelegateSourcePreferences
import exh.search.SearchEngine
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.data.extension.repository.ExtensionStoreRepositoryImpl
import mihon.data.extension.service.ExtensionStoreService
import mihon.domain.extension.interactor.AddExtensionStore
import mihon.domain.extension.interactor.GetExtensionStoreCountAsFlow
import mihon.domain.extension.interactor.GetExtensionStores
import mihon.domain.extension.interactor.RemoveExtensionStore
import mihon.domain.extension.interactor.UpdateExtensionStores
import mihon.domain.extension.repository.ExtensionStoreRepository
import mihon.domain.migration.usecases.MigrateMangaUseCase
import mihon.domain.source.interactor.UpdateMangaFromRemote
import nl.adaptivity.xmlutil.XmlDeclMode
import nl.adaptivity.xmlutil.core.XmlVersion
import nl.adaptivity.xmlutil.serialization.XML
import tachiyomi.core.common.preference.DesktopPreferenceStore
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.storage.DesktopStorageFolderProvider
import tachiyomi.data.Database
import tachiyomi.data.DatabaseHandler
import tachiyomi.data.DesktopDatabase
import tachiyomi.data.di.registerDatabaseRepositories
import tachiyomi.data.release.ReleaseServiceImpl
import tachiyomi.data.source.SourceRepositoryImpl
import tachiyomi.domain.di.registerDomainPreferences
import tachiyomi.domain.di.registerLibraryInteractors
import tachiyomi.domain.manga.interactor.GetFlatMetadataById
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.InsertFlatMetadata
import tachiyomi.domain.release.interactor.GetApplicationRelease
import tachiyomi.domain.release.service.ReleaseService
import tachiyomi.domain.source.interactor.GetRemoteManga
import tachiyomi.domain.source.interactor.GetSourcesWithNonLibraryManga
import tachiyomi.domain.source.repository.SourceRepository
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.domain.storage.service.StoragePreferences
import tachiyomi.source.local.image.LocalCoverManager
import tachiyomi.source.local.io.LocalSourceFileSystem
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addFactory
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get
import xyz.nulldev.ts.api.http.serializer.FilterSerializer
import java.io.File

/**
 * Desktop counterpart of the Android `PreferenceModule` + `SYPreferenceModule`: the main
 * [PreferenceStore] (the process-wide instance from [DesktopPreferenceStores], shared with
 * `SharedPreferences` users) and the preference classes that live in shared modules.
 *
 * App-module preference classes are added as `:app-shared` borrows them: `UiPreferences` (4.2),
 * `SourcePreferences` (5.2); `ReaderPreferences`, `TrackPreferences`, `BasePreferences`, … are not
 * available on desktop yet. [DesktopWindowPreferences] is desktop-only.
 */
class DesktopPreferenceModule(
    private val app: Application,
    private val dataDir: File,
) : InjektModule {

    private val preferencesDir: File get() = dataDir.resolve("preferences")

    override fun InjektRegistrar.registerInjectables() {
        addSingletonFactory<PreferenceStore> {
            DesktopPreferenceStores.get(DesktopPreferenceStore.DEFAULT_STORE_NAME, preferencesDir)
        }
        addSingletonFactory { NetworkPreferences(get()) }
        addSingletonFactory { SecurityPreferences(get()) }
        // P16: the desktop app lock (PIN / password)
        addSingletonFactory { DesktopAppLock(get(), get()) }
        addSingletonFactory { PrivacyPreferences(get()) }
        addSingletonFactory { DelegateSourcePreferences(get()) }
        addSingletonFactory { DesktopStorageFolderProvider(baseDir = dataDir) }
        addSingletonFactory {
            StoragePreferences(
                folderProvider = get<DesktopStorageFolderProvider>(),
                preferenceStore = get(),
            )
        }
        registerDomainPreferences()
        addSingletonFactory { UiPreferences(get()) }
        addSingletonFactory { SourcePreferences(get()) }
        addSingletonFactory { DesktopWindowPreferences(get()) }
        addSingletonFactory { JsSourcePreferences(get()) }
        // Borrowed (desktopCopy) app preference classes (6L.1, 7.x)
        addSingletonFactory { BasePreferences(app, get()) }
        addSingletonFactory { ReaderPreferences(get()) }
        addSingletonFactory { TrackPreferences(get()) }
    }
}

/**
 * Desktop counterpart of the Android `AppModule`: compat [Application], database (same schema,
 * desktop driver, see [DesktopDatabase]), serializers with the app's configuration, network,
 * JavaScript engine, storage and the local source's file system.
 */
class DesktopAppModule(
    private val app: Application,
    private val databaseFile: File,
) : InjektModule {

    override fun InjektRegistrar.registerInjectables() {
        addSingleton(app)

        addSingletonFactory<SqlDriver> { DesktopDatabase.createDriver(databaseFile) }
        addSingletonFactory<Database> { DesktopDatabase.createDatabase(get()) }
        addSingletonFactory<DatabaseHandler> { DesktopDatabase.createHandler(get(), get()) }

        addSingletonFactory {
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            }
        }
        addSingletonFactory {
            XML {
                defaultPolicy {
                    ignoreUnknownChildren()
                }
                autoPolymorphic = true
                xmlDeclMode = XmlDeclMode.Charset
                indent = 2
                xmlVersion = XmlVersion.XML10
            }
        }
        addSingletonFactory<ProtoBuf> { ProtoBuf }

        addSingletonFactory {
            // P7 review: the interceptors' per-host User-Agent overrides persist in a desktop preference.
            DesktopCloudflare.installHostState(get())
            // P8: a FlareSolverr-compatible service, when its address is set, solves challenges automatically.
            get<FlareSolverrBackend>()
            NetworkHelper(app, get())
        }
        addSingletonFactory { DesktopCloudflarePreferences(get()) }
        addSingletonFactory { DesktopCloudflare.installBackend(get()) }
        // "Open in WebView" (P7): embedded browser backend or the system browser + pasted cookies.
        addSingletonFactory { DesktopWebView(get(), get(), get()) }
        addSingletonFactory { JavaScriptEngine(app) }

        addSingletonFactory { StorageManager(app, get()) }
        addSingletonFactory { LocalSourceFileSystem(get()) }
        addSingletonFactory { LocalCoverManager(app, get()) }
    }
}

/**
 * Desktop counterpart of the Android `DomainModule` / `SYDomainModule` / `KMKDomainModule`, built from
 * the shared registration functions in `:data` and `:domain`, plus the desktop platform services:
 * extension stores (same repository / service as Android), the extension runtime and
 * [DesktopExtensionManager] (APKs under `<dataDir>/extensions`), the desktop [SourceManager] (local +
 * extension sources, see [DesktopSourceManager]) and what needs them.
 */
class DesktopDomainModule(
    private val app: Application,
    private val dataDir: File,
) : InjektModule {

    override fun InjektRegistrar.registerInjectables() {
        registerDatabaseRepositories(app)
        registerLibraryInteractors()

        // Extension stores (Android: DomainModule / KMKDomainModule)
        addSingletonFactory { ExtensionStoreService(get(), get(), get()) }
        addSingletonFactory<ExtensionStoreRepository> { ExtensionStoreRepositoryImpl(get(), get()) }
        addFactory { AddExtensionStore(get()) }
        addFactory { GetExtensionStoreCountAsFlow(get()) }
        addFactory { GetExtensionStores(get()) }
        addFactory { RemoveExtensionStore(get()) }
        addFactory { UpdateExtensionStores(get()) }
        addFactory { RefreshExtensionStores(get(), get()) }

        // Extensions
        addSingletonFactory { ExtensionRuntime(File(dataDir, "extensions")) }
        addSingletonFactory {
            DesktopExtensionManager(
                context = app,
                extensionsDir = File(dataDir, "extensions"),
                runtime = get(),
                network = get(),
                storeRepository = get(),
                preferences = get(),
                exhPreferences = get(),
            )
        }
        addSingletonFactory<PlatformExtensionManager> { get<DesktopExtensionManager>() }
        addFactory { GetPlatformExtensionsByType(get(), get()) }

        // Mangayomi JavaScript sources (Android: KMKDomainModule)
        addSingletonFactory { MangayomiSourceStore.forApp(app, get(), get(), get()) }
        addFactory { GetExtensionSources(get()) }

        addSingletonFactory<SourceManager> {
            DesktopSourceManager(
                context = app,
                fileSystem = get(),
                coverManager = get(),
                stubSourceRepository = get(),
                allowHiddenFiles = get<SourcePreferences>().allowLocalSourceHiddenFolders()::get,
                extensionManager = get(),
                exhPreferences = get(),
                jsSources = MangayomiSourceStore.sourcesOrEmpty { get() },
            )
        }
        addSingletonFactory<SourceRepository> { SourceRepositoryImpl(get(), get()) }
        addFactory { GetRemoteManga(get()) }
        addFactory { GetSourcesWithNonLibraryManga(get()) }
        // Sources tab, source filter, pins and global search (P1): the Android interactors (borrowed)
        addFactory { GetEnabledSources(get(), get()) }
        addFactory { GetLanguagesWithSources(get(), get()) }
        addFactory { ToggleLanguage(get()) }
        addFactory { ToggleSource(get()) }
        addFactory { ToggleSourcePin(get()) }
        addFactory { UpdateManga(get(), get()) }
        // SY delegated sources (P4): the metadata hooks of `MetadataSource` (Android: SYDomainModule)
        addFactory<MetadataSource.GetMangaId> { GetManga(get()) }
        addFactory<MetadataSource.GetFlatMetadataById> { GetFlatMetadataById(get()) }
        addFactory<MetadataSource.InsertFlatMetadata> { InsertFlatMetadata(get()) }
        // Library text search (8.5): the SY query parser used by the borrowed LibraryFilterSort.kt
        addFactory { SearchEngine() }

        // Manga details / chapters (6L.1): the Android interactors, borrowed as desktopCopy.
        // Downloads (8.1): the Android download stack, borrowed as desktopCopy (coroutine DownloadJob,
        // events instead of notifications).
        addSingletonFactory { CoverCache(app) }
        addSingletonFactory { DownloadProvider(app) }
        addSingletonFactory { DesktopDownloadEvents(showMessage = { app.toast(it) }) }
        addSingletonFactory<DownloadNotificationSink> { get<DesktopDownloadEvents>() }
        addSingletonFactory { DownloadCache(app) }
        addSingletonFactory { DownloadManager(app) }
        addFactory { GetExcludedScanlators(get()) }
        addFactory { SyncChaptersWithSource(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
        addFactory { DeleteDownload(get(), get()) }
        addFactory { SetReadStatus(get(), get(), get(), get(), get()) }
        addFactory { SetMangaViewerFlags(get()) }
        addFactory { GetIncognitoState(get(), get(), get()) }
        // Saved searches (P13): same filter serialisation as Android (SYDomainModule)
        addFactory { FilterSerializer() }
        addFactory { GetExhSavedSearch(get(), get(), get()) }
        addFactory { UpdateMangaFromRemote(get(), get(), get(), get(), get(), get(), get()) }
        // Source migration (P18): Mihon's / Komikku's use case (Android: DomainModule)
        addFactory {
            MigrateMangaUseCase(
                sourcePreferences = get(),
                trackerManager = get(),
                sourceManager = get(),
                downloadManager = get(),
                updateManga = get(),
                getChaptersByMangaId = get(),
                updateChapter = get(),
                getCategories = get(),
                setMangaCategories = get(),
                getTracks = get(),
                insertTrack = get(),
                coverCache = get(),
                updateMangaFromRemote = get(),
                getHistory = get(),
                upsertHistory = get(),
            )
        }

        // Library update (6L.2): shared coroutine runner, one per process
        addSingletonFactory<LibraryUpdateRunner> {
            desktopLibraryUpdateRunner(
                getLibraryManga = get(),
                libraryPreferences = get(),
                fetchInterval = get(),
                sourceManager = get(),
                updateMangaFromRemote = get(),
                insertErrors = get(),
                insertErrorMessages = get(),
            )
        }

        // Backup (8.2): borrowed Android creator / restorer
        addSingletonFactory { DesktopBackupService(app, get(), get()) }
        addSingletonFactory { DesktopBackupJobs(get<DesktopBackupService>()) }

        // Scheduled library updates and automatic backups (P3), started by main() for the app's lifetime
        addSingletonFactory { DesktopSchedulerPreferences(get()) }
        addSingletonFactory { desktopScheduler(get(), get(), get(), get(), get(), appUpdateChecker = get()) }

        // App update check (P6): Android's interactor on the GitHub releases, desktop installers with a comparable tag only
        addSingletonFactory { DesktopUpdaterConfig.fromSystemProperties() }
        addSingletonFactory<ReleaseService> {
            val config = get<DesktopUpdaterConfig>()
            ReleaseServiceImpl(get(), get(), downloadLinkSelector = { release, _ -> config.downloadLink(release) })
        }
        addFactory { GetApplicationRelease(get(), get()) }
        addSingletonFactory { DesktopAppUpdateChecker(get(), get(), get()) }
        addSingletonFactory<DesktopFileDialogs> { AwtFileDialogs(get<DesktopAppLock>().locked) }

        // Notifications and the system tray (P17): Android's notifiers' texts through the tray icon, when there is one
        addSingletonFactory { DesktopTrayPreferences(get()) }
        addSingletonFactory { DesktopTray() }
        addSingletonFactory<DesktopNotifier> { get<DesktopTray>() }
        addSingletonFactory {
            DesktopNotifications(
                context = app,
                notifier = get(),
                securityPreferences = get(),
                isLocked = { get<DesktopAppLock>().locked.value },
                libraryUpdates = get<LibraryUpdateRunner>().progress,
                downloadEvents = get<DesktopDownloadEvents>().events,
                backupStatus = get<DesktopBackupJobs>().status,
                appUpdates = get<DesktopAppUpdateChecker>().available,
            )
        }

        // Trackers (8.3): borrowed Android trackers and interactors, desktop TrackerManager counterpart
        addSingletonFactory { TrackerManager() }
        addSingletonFactory { DelayedTrackingStore(app) }
        addFactory { TrackChapter(get(), get(), get(), get()) }
        addFactory { AddTracks(get(), get(), get(), get()) }
        addFactory { RefreshTracks(get(), get(), get(), get()) }
        addFactory { SyncChapterProgressWithTrack(get(), get(), get()) }

        // Reader (7.x)
        addSingletonFactory { ChapterCache(app, get(), get()) }
        addSingletonFactory { ReaderLauncher() }
        addSingletonFactory<ReaderTrackerHook> { DesktopReaderTrackerHook(app, get(), get()) }
        addFactory {
            ReaderProgressSaver(
                updateChapter = get(),
                upsertHistory = get(),
                getChaptersByMangaId = get(),
                libraryPreferences = get(),
                trackerHook = get(),
            )
        }
    }
}
