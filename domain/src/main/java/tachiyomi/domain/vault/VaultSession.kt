package tachiyomi.domain.vault

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-process state of the private vault.
 *
 * A vault session only lives inside one app process: it is started by the one-shot handoff in
 * [tachiyomi.domain.vault.service.VaultPreferences] and is never persisted. The session also
 * expires when the app is left in the background for [BACKGROUND_TIMEOUT_MS] or the screen is
 * turned off.
 */
object VaultSession {

    const val KEY_SUFFIX = "__vault"

    const val BACKGROUND_TIMEOUT_MS = 30_000L

    @Volatile
    private var _isActive: Boolean = false

    val isActive: Boolean
        get() = _isActive

    private val _requiresAuth = MutableStateFlow(true)

    /**
     * Whether the private vault is waiting for the device credential.
     *
     * Starts as `true` on every vault process start and becomes `false` after a successful
     * authentication. It is not reset when the app is backgrounded: the session instead ends
     * after [BACKGROUND_TIMEOUT_MS] or when the screen is turned off.
     */
    val requiresAuth: StateFlow<Boolean> = _requiresAuth.asStateFlow()

    /**
     * Set while the device credential prompt is in flight so backgrounding the app for the prompt
     * itself does not start the background timer.
     */
    @Volatile
    var isAuthenticating: Boolean = false
        private set

    @Volatile
    private var backgroundedAt: Long? = null

    @Volatile
    private var screenTurnedOff: Boolean = false

    /**
     * Set just before the app restarts into the standard library. Prevents the credential prompt
     * from opening while the process is being torn down.
     */
    @Volatile
    var isSessionEnding: Boolean = false
        private set

    fun init(active: Boolean) {
        _isActive = active
        _requiresAuth.value = true
        isAuthenticating = false
        backgroundedAt = null
        screenTurnedOff = false
        isSessionEnding = false
    }

    fun onAuthenticating() {
        isAuthenticating = true
    }

    fun onAuthenticated() {
        isAuthenticating = false
        _requiresAuth.value = false
    }

    fun onAuthenticationDismissed() {
        isAuthenticating = false
        _requiresAuth.value = true
    }

    fun onAppBackgrounded(now: Long = SystemClock.elapsedRealtime()) {
        if (!isActive) return
        // The system credential screen can move the app to the background on older versions.
        // The user is still authenticating, so this must not count as idle time.
        if (isAuthenticating) return
        backgroundedAt = now
    }

    fun onScreenOff() {
        if (!isActive) return
        screenTurnedOff = true
    }

    /**
     * Whether the vault must be closed when the app next comes to the foreground.
     */
    fun shouldEndSessionOnForeground(now: Long = SystemClock.elapsedRealtime()): Boolean {
        if (!isActive) return false
        if (screenTurnedOff) return true
        val backgroundedAt = backgroundedAt ?: return false
        return now - backgroundedAt >= BACKGROUND_TIMEOUT_MS
    }

    fun onAppForegrounded() {
        backgroundedAt = null
        screenTurnedOff = false
    }

    fun markSessionEnding() {
        isSessionEnding = true
    }

    /**
     * Keys that hold IDs belonging to the active database (categories, manga ids for cover
     * metadata, ...) must not be shared between the standard library and the vault.
     */
    fun profileKey(key: String): String = if (isActive) "$key$KEY_SUFFIX" else key

    fun isVaultKey(key: String): Boolean = key.endsWith(KEY_SUFFIX)
}
