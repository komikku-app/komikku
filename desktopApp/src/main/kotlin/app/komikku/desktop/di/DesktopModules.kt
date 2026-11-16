package app.komikku.desktop.di

import android.app.Application
import app.cash.sqldelight.db.SqlDriver
import app.komikku.desktop.compat.DesktopPreferenceStores
import app.komikku.desktop.extension.ExtensionRuntime
import app.komikku.desktop.extensions.DesktopExtensionManager
import app.komikku.desktop.source.DesktopSourceManager
import app.komikku.desktop.window.DesktopWindowPreferences
import app.komikku.shared.extension.GetPlatformExtensionsByType
import app.komikku.shared.extension.PlatformExtensionManager
import eu.kanade.domain.extension.interactor.GetExtensionSources
import eu.kanade.domain.extension.interactor.RefreshExtensionStores
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.core.security.PrivacyPreferences
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.network.JavaScriptEngine
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.NetworkPreferences
import exh.pref.DelegateSourcePreferences
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
import tachiyomi.data.source.SourceRepositoryImpl
import tachiyomi.domain.di.registerDomainPreferences
import tachiyomi.domain.di.registerLibraryInteractors
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
    private val dataDir: File,
) : InjektModule {

    private val preferencesDir: File get() = dataDir.resolve("preferences")

    override fun InjektRegistrar.registerInjectables() {
        addSingletonFactory<PreferenceStore> {
            DesktopPreferenceStores.get(DesktopPreferenceStore.DEFAULT_STORE_NAME, preferencesDir)
        }
        addSingletonFactory { NetworkPreferences(get()) }
        addSingletonFactory { SecurityPreferences(get()) }
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

        addSingletonFactory { NetworkHelper(app, get()) }
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
            )
        }
        addSingletonFactory<SourceRepository> { SourceRepositoryImpl(get(), get()) }
        addFactory { GetRemoteManga(get()) }
        addFactory { GetSourcesWithNonLibraryManga(get()) }
    }
}
