package app.komikku.desktop.webview

import app.komikku.network.browser.BrowserBackends
import app.komikku.network.browser.CloudflareHostState
import app.komikku.network.browser.CloudflareHosts
import app.komikku.network.browser.UserAgentOverrideStore
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

/**
 * KMK: the desktop's Cloudflare state set-up (P7 review): the per-host User-Agent overrides that the interceptor applies
 * while their clearance is in the cookie jar (a solver's or a pasted User-Agent) are kept in the
 * [USER_AGENTS_KEY] preference, so that they survive a restart like the cookies do; the automatic backend (P8) is a
 * FlareSolverr-compatible service at [DesktopCloudflarePreferences.flareSolverrUrl] (empty = none, the default).
 */
object DesktopCloudflare {

    /** JSON: host → User-Agent and the hash of the clearance it belongs to (no cookie values). */
    const val USER_AGENTS_KEY = "desktop_cloudflare_user_agents"

    /** Makes [CloudflareHosts.state] keep its User-Agent overrides in [preferenceStore]. */
    fun installHostState(preferenceStore: PreferenceStore) {
        CloudflareHosts.state = CloudflareHostState(
            UserAgentOverrideStore.InPreference(preferenceStore.getString(USER_AGENTS_KEY, "")),
        )
    }

    /**
     * Makes [BrowserBackends.current] the FlareSolverr backend on [preferences]' address (read per challenge, so a
     * change in the settings applies at once; empty = unavailable, the P7 manual flow).
     */
    fun installBackend(preferences: DesktopCloudflarePreferences): FlareSolverrBackend =
        FlareSolverrBackend({ preferences.flareSolverrUrl().get() }).also { BrowserBackends.current = it }
}

/**
 * KMK: desktop-only Cloudflare settings (P8). Android has no FlareSolverr setting (checked: no such key in Android,
 * Mihon or SY), so this is a desktop preference.
 */
class DesktopCloudflarePreferences(private val preferenceStore: PreferenceStore) {

    /** Address of a FlareSolverr-compatible service (`http(s)://host:port`, or its `/v1`); empty = off. */
    fun flareSolverrUrl(): Preference<String> = preferenceStore.getString("desktop_flaresolverr_url", "")

    companion object {
        /** Whether [value] can be saved as [flareSolverrUrl]: empty (off) or an `http(s)` address. */
        fun isValidFlareSolverrUrl(value: String): Boolean =
            value.isBlank() || FlareSolverrBackend.apiUrl(value) != null
    }
}
