package app.komikku.desktop

import android.app.Application
import app.komikku.desktop.compat.DesktopPreferenceStores
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.NetworkPreferences
import tachiyomi.core.common.preference.DesktopPreferenceStore
import tachiyomi.core.common.preference.PreferenceStore

/**
 * The shared non-UI services the desktop app runs on, wired by hand until the Injekt bootstrap
 * (docs/kmp-desktop/PLAN.md, step 3.4): the compat [Application] context, the main preference store
 * (the same instance `SharedPreferences` users get) and the shared `NetworkHelper`.
 */
class DesktopServices(
    val application: Application = Application(),
    val preferenceStore: PreferenceStore = DesktopPreferenceStores.get(DesktopPreferenceStore.DEFAULT_STORE_NAME),
) {
    val networkPreferences = NetworkPreferences(preferenceStore)

    val network: NetworkHelper by lazy { NetworkHelper(application, networkPreferences) }
}
