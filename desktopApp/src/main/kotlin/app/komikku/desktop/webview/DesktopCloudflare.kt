package app.komikku.desktop.webview

import app.komikku.network.browser.BrowserBackends
import app.komikku.network.browser.CloudflareHostState
import app.komikku.network.browser.CloudflareHosts
import app.komikku.network.browser.UserAgentOverrideStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
     * change in the settings applies at once; empty = unavailable, the P7 manual flow). Another address forgets the
     * hosts' remembered failed solves (P8 review).
     */
    fun installBackend(preferences: DesktopCloudflarePreferences): FlareSolverrBackend =
        FlareSolverrBackend(
            endpoint = { preferences.flareSolverrUrl().get() },
            onEndpointChanged = { CloudflareHosts.state.clearFailures() },
        ).also { BrowserBackends.current = it }
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

/**
 * KMK: the FlareSolverr address as the settings show it (P8 review): the password of `user:password@` in it is never
 * rendered, neither in the subtitle nor in the edit field, but shown as [PASSWORD_MASK]; an edited address that still
 * holds the mask keeps the stored password (typing another one over the mask replaces it). Plain text work on the
 * address as typed (no normalising), so what the user typed stays as it was.
 */
object FlareSolverrAddress {

    const val PASSWORD_MASK = "••••"

    /** [address] with its password, if it has a non-empty one, replaced by [PASSWORD_MASK]. */
    fun mask(address: String): String {
        val range = passwordRange(address) ?: return address
        return if (range.isEmpty()) address else address.replaceRange(range, PASSWORD_MASK)
    }

    /** [edited] with a [PASSWORD_MASK] password replaced by [stored]'s password (as stored). */
    fun unmask(edited: String, stored: String): String {
        val range = passwordRange(edited) ?: return edited
        if (edited.substring(range) != PASSWORD_MASK) return edited
        val storedRange = passwordRange(stored)?.takeUnless { it.isEmpty() } ?: return edited
        return edited.replaceRange(range, stored.substring(storedRange))
    }

    /** Where the password of `scheme://user:password@host…` is in [address]; `null` without user info and password. */
    private fun passwordRange(address: String): IntRange? {
        val schemeEnd = address.indexOf("://").takeIf { it >= 0 } ?: return null
        val authorityStart = schemeEnd + 3
        val authorityEnd = address.indexOfAny(charArrayOf('/', '?', '#'), authorityStart).takeIf { it >= 0 } ?: address.length
        val at = address.lastIndexOf('@', authorityEnd - 1).takeIf { it >= authorityStart } ?: return null
        val colon = address.indexOf(':', authorityStart).takeIf { it in authorityStart until at } ?: return null
        return colon + 1 until at
    }
}

/** KMK: [delegate] (the stored address) as [FlareSolverrAddress] shows and takes it, for the settings' edit field. */
class MaskedAddressPreference(private val delegate: Preference<String>) : Preference<String> by delegate {

    override fun get(): String = FlareSolverrAddress.mask(delegate.get())

    override fun set(value: String) = delegate.set(FlareSolverrAddress.unmask(value, delegate.get()))

    override fun defaultValue(): String = FlareSolverrAddress.mask(delegate.defaultValue())

    override fun changes(): Flow<String> = delegate.changes().map(FlareSolverrAddress::mask)

    override fun stateIn(scope: CoroutineScope): StateFlow<String> = changes().stateIn(scope, SharingStarted.Eagerly, get())
}
