package app.komikku.desktop.di

import android.app.Application
import app.cash.sqldelight.db.SqlDriver
import app.komikku.desktop.compat.DesktopPreferenceStores
import eu.kanade.tachiyomi.core.security.PrivacyPreferences
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.network.JavaScriptEngine
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.NetworkPreferences
import exh.pref.DelegateSourcePreferences
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
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
import tachiyomi.domain.di.registerDomainPreferences
import tachiyomi.domain.di.registerLibraryInteractors
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.domain.storage.service.StoragePreferences
import tachiyomi.source.local.image.LocalCoverManager
import tachiyomi.source.local.io.LocalSourceFileSystem
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * Desktop counterpart of the Android `PreferenceModule` + `SYPreferenceModule`: the main
 * [PreferenceStore] (the process-wide instance from [DesktopPreferenceStores], shared with
 * `SharedPreferences` users) and the preference classes that live in shared modules.
 *
 * App-module preference classes (`SourcePreferences`, `UiPreferences`, `ReaderPreferences`,
 * `TrackPreferences`, `BasePreferences`, …) are not available on desktop yet (`:app-shared`, Phase 4+).
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
 * the shared registration functions in `:data` and `:domain`. Grows with the desktop features:
 * register platform services (source manager, extensions, downloads, trackers) and the interactors
 * that need them here.
 */
class DesktopDomainModule(
    private val app: Application,
) : InjektModule {

    override fun InjektRegistrar.registerInjectables() {
        registerDatabaseRepositories(app)
        registerLibraryInteractors()
    }
}
