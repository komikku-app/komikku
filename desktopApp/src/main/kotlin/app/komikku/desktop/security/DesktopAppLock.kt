package app.komikku.desktop.security

import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.ui.base.delegate.AppLockRules
import exh.log.xLogE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Calendar
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * P16: the desktop app lock, Android's `SecureActivityDelegate` with a PIN or password of the app's own instead of the
 * device's authenticator (desktop operating systems offer none to a JVM app; no biometrics).
 *
 * - On: Android's "Require unlock" (`SecurityPreferences.useAuthenticator`) **and** a PIN / password set on this machine.
 *   The secret is kept only as a salted PBKDF2 hash in an app-state preference, so backups never carry it. A "Require
 *   unlock" without a secret (an Android backup restored here) counts as off and is switched off at start, like Android
 *   does when the device has no authenticator.
 * - Locked at start-up (Android: `requireUnlock` is true for a new process) and by [lockNow].
 * - "Lock when idle" (`lockAppAfter`) and SY's lock times / days ([AppLockRules]) apply after the window was hidden
 *   (minimised: Android's app going to the background): [onWindowHidden] / [onWindowShown].
 * - Wrong entries: from the [FREE_ATTEMPTS]th failure in a row, the next try waits 30 s more each time (at most 5 min).
 *
 * [clock] and [calendar] are injected for tests.
 */
class DesktopAppLock(
    private val securityPreferences: SecurityPreferences,
    preferenceStore: PreferenceStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val calendar: () -> Calendar = { Calendar.getInstance() },
) {

    private val secret = preferenceStore.getString(Preference.appStateKey(SECRET_KEY), "")

    private val _locked = MutableStateFlow(false)

    /** Whether the app is locked now (the lock screen covers it). */
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private val _enabled = MutableStateFlow(false)

    /** Whether the lock is on (see the class docs). */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private var failures = 0
    private var blockedUntil = 0L

    /** The window was hidden while unlocked: the next [onWindowShown] applies "Lock when idle". */
    private var hidden = false

    init {
        val require = securityPreferences.useAuthenticator()
        if (require.get() && secret.get().isEmpty()) require.set(false)
        _enabled.value = require.get() && secret.get().isNotEmpty()
        // Android: a new process always starts locked
        _locked.value = _enabled.value
    }

    sealed interface Attempt {
        data object Accepted : Attempt
        data object Wrong : Attempt

        /** Too many wrong entries: the next try is possible in [seconds]. */
        data class Blocked(val seconds: Long) : Attempt
    }

    /** Unlocks with [input] when it is the PIN / password. */
    suspend fun unlock(input: String): Attempt {
        val attempt = check(input)
        if (attempt == Attempt.Accepted) _locked.value = false
        return attempt
    }

    /** Switches the lock on with [newSecret] (at least [MIN_LENGTH] characters); the app stays unlocked. */
    suspend fun enable(newSecret: String) {
        require(newSecret.length >= MIN_LENGTH) { "The PIN or password is too short" }
        val stored = withContext(Dispatchers.Default) { AppLockSecret.hash(newSecret) }
        secret.set(stored)
        securityPreferences.useAuthenticator().set(true)
        resetFailures()
        _enabled.value = true
    }

    /** Switches the lock off when [current] is the PIN / password. */
    suspend fun disable(current: String): Attempt {
        val attempt = check(current)
        if (attempt == Attempt.Accepted) {
            securityPreferences.useAuthenticator().set(false)
            securityPreferences.lastAppClosed().delete()
            secret.delete()
            _enabled.value = false
            _locked.value = false
        }
        return attempt
    }

    /** Replaces the PIN / password when [current] is the present one. */
    suspend fun change(current: String, newSecret: String): Attempt {
        require(newSecret.length >= MIN_LENGTH) { "The PIN or password is too short" }
        val attempt = check(current)
        if (attempt == Attempt.Accepted) {
            secret.set(withContext(Dispatchers.Default) { AppLockSecret.hash(newSecret) })
        }
        return attempt
    }

    /** Locks the app now (while the lock is on). */
    fun lockNow() {
        if (_enabled.value) _locked.value = true
    }

    /** Android's `onApplicationStopped`: the window was minimised. */
    fun onWindowHidden() {
        if (!_enabled.value || _locked.value) return
        hidden = true
        if (securityPreferences.lockAppAfter().get() > 0) securityPreferences.lastAppClosed().set(clock())
    }

    /** Android's `onApplicationStart`: the window is shown again; locks when "Lock when idle" says so. */
    fun onWindowShown() {
        if (!_enabled.value) return
        val lastClosed = securityPreferences.lastAppClosed()
        if (hidden && !_locked.value) {
            _locked.value = AppLockRules.canLockNow(
                timeRanges = securityPreferences.authenticatorTimeRanges().get(),
                lockedDays = securityPreferences.authenticatorDays().get(),
                now = calendar(),
            ) &&
                AppLockRules.isLockDue(
                    lockAppAfter = securityPreferences.lockAppAfter().get(),
                    lastClosed = lastClosed.get(),
                    nowMillis = clock(),
                )
        }
        hidden = false
        lastClosed.delete()
    }

    private suspend fun check(input: String): Attempt {
        val now = clock()
        if (now < blockedUntil) return Attempt.Blocked((blockedUntil - now + 999) / 1000)
        val stored = secret.get()
        val matches = stored.isNotEmpty() && withContext(Dispatchers.Default) { AppLockSecret.verify(input, stored) }
        if (matches) {
            resetFailures()
            return Attempt.Accepted
        }
        failures++
        if (failures >= FREE_ATTEMPTS) {
            val waitMillis = (30_000L * (failures - FREE_ATTEMPTS + 1)).coerceAtMost(MAX_WAIT_MILLIS)
            blockedUntil = now + waitMillis
        }
        return Attempt.Wrong
    }

    private fun resetFailures() {
        failures = 0
        blockedUntil = 0L
    }

    companion object {
        /** The fewest characters a PIN / password may have. */
        const val MIN_LENGTH = 4

        /** Wrong entries in a row before each further try has to wait. */
        const val FREE_ATTEMPTS = 5

        private const val MAX_WAIT_MILLIS = 5 * 60_000L

        internal const val SECRET_KEY = "desktop_app_lock_secret"
    }
}

/** The stored form of the PIN / password: `pbkdf2-sha256:<iterations>:<salt>:<hash>` (Base64), never the secret. */
internal object AppLockSecret {

    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val PREFIX = "pbkdf2-sha256"
    private const val ITERATIONS = 210_000
    private const val KEY_BITS = 256

    private val random = SecureRandom()

    fun hash(secret: String): String {
        val salt = ByteArray(16).also(random::nextBytes)
        val derived = derive(secret, salt, ITERATIONS)
        val encoder = Base64.getEncoder()
        return "$PREFIX:$ITERATIONS:${encoder.encodeToString(salt)}:${encoder.encodeToString(derived)}"
    }

    fun verify(secret: String, stored: String): Boolean {
        return try {
            val parts = stored.split(':')
            if (parts.size != 4 || parts[0] != PREFIX) return false
            val iterations = parts[1].toInt()
            val decoder = Base64.getDecoder()
            val salt = decoder.decode(parts[2])
            val expected = decoder.decode(parts[3])
            MessageDigest.isEqual(derive(secret, salt, iterations), expected)
        } catch (e: Exception) {
            xLogE("Unreadable app lock secret", e)
            false
        }
    }

    private fun derive(secret: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(secret.toCharArray(), salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
