package app.komikku.desktop.webview

import app.komikku.network.browser.CloudflareHostState
import app.komikku.network.browser.CloudflareHosts
import app.komikku.network.browser.UserAgentOverrideStore
import tachiyomi.core.common.preference.PreferenceStore

/**
 * KMK: the desktop's Cloudflare state set-up (P7 review): the per-host User-Agent overrides that the interceptor applies
 * while their clearance is in the cookie jar (a solver's or a pasted User-Agent) are kept in the
 * [USER_AGENTS_KEY] preference, so that they survive a restart like the cookies do.
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
}
