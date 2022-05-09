package exh.debug

import eu.kanade.core.prefs.PreferenceMutableState
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import kotlinx.coroutines.CoroutineScope
import uy.kohesive.injekt.injectLazy
import java.util.Locale

enum class DebugToggles(val default: Boolean) {
    // Redirect to master version of gallery when encountering a gallery that has a parent/child that is already in the library
    ENABLE_EXH_ROOT_REDIRECT(true),

    // Enable debug overlay (only available in debug builds)
    ENABLE_DEBUG_OVERLAY(true),

    // Convert non-root galleries into root galleries when loading them
    PULL_TO_ROOT_WHEN_LOADING_EXH_MANGA_DETAILS(true),

    // Do not update the same gallery too often
    RESTRICT_EXH_GALLERY_UPDATE_CHECK_FREQUENCY(true),

    // Pretend that all galleries only have a single version
    INCLUDE_ONLY_ROOT_WHEN_LOADING_EXH_VERSIONS(false);

    private val prefKey = "eh_debug_toggle_${name.lowercase(Locale.US)}"

    var enabled: Boolean
        get() = prefs.flowPrefs.getBoolean(prefKey, default).get()
        set(value) {
            prefs.flowPrefs.getBoolean(prefKey).set(value)
        }

    fun asPref(scope: CoroutineScope) = PreferenceMutableState(prefs.flowPrefs.getBoolean(prefKey, default), scope)

    companion object {
        private val prefs: PreferencesHelper by injectLazy()
    }
}
