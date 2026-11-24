package app.komikku.desktop

import android.app.Application
import app.komikku.desktop.compat.DesktopPreferenceStores
import app.komikku.desktop.webview.DesktopCloudflare
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.NetworkPreferences
import tachiyomi.core.common.preference.DesktopPreferenceStore
import tachiyomi.core.common.preference.PreferenceStore

/**
 * Hand-wired network services (the compat [Application] context, the main preference store and the
 * shared `NetworkHelper`) for tests that do not need the whole graph (`DesktopNetworkTest`).
 * The app itself resolves everything through Injekt (`app.komikku.desktop.di.DesktopInjekt`).
 */
class DesktopServices(
    val application: Application = Application(),
    val preferenceStore: PreferenceStore = DesktopPreferenceStores.get(DesktopPreferenceStore.DEFAULT_STORE_NAME),
) {
    val networkPreferences = NetworkPreferences(preferenceStore)

    val network: NetworkHelper by lazy {
        DesktopCloudflare.installHostState(preferenceStore)
        NetworkHelper(application, networkPreferences)
    }
}
