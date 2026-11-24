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
 * - On while a PIN / password is set on this machine (review fix): the secret is kept only as a salted PBKDF2 hash in an
 *   app-state preference, which backups neither carry nor restore, so restoring a backup can neither switch the lock off
 *   nor on. Android's backed-up "Require unlock" (`SecurityPreferences.useAuthenticator`) is Android's own setting: the
 *   desktop neither reads nor writes it (a backup carries it between Android devices unchanged).
 * - Locked at start-up (Android: `requireUnlock` is true for a new process) and by [lockNow].
 * - "Lock when idle" (`lockAppAfter`) and SY's lock times / days ([AppLockRules]) apply after the window was hidden
 *   (minimised: Android's app going to the background): [onWindowHidden] / [onWindowShown].
 * - Wrong entries: from the [FREE_ATTEMPTS]th failure in a row, the next try waits 30 s more each time (at most 5 min).
 *   The count and the end of the wait are app-state preferences too (review fix), so a restart does not reset them; a
 *   stored end further away than the current wait (the clock was set back) counts as the current wait from now.
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
    private val failures = preferenceStore.getInt(Preference.appStateKey(FAILURES_KEY), 0)
    private val blockedUntil = preferenceStore.getLong(Preference.appStateKey(BLOCKED_UNTIL_KEY), 0L)

    private val _locked = MutableStateFlow(false)

    /** Whether the app is locked now (the lock screen covers it). */
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private val _enabled = MutableStateFlow(false)

    /** Whether the lock is on (see the class docs). */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    /** The window was hidden while unlocked: the next [onWindowShown] applies "Lock when idle". */
    private var hidden = false

    init {
        _enabled.value = secret.get().isNotEmpty()
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
        resetFailures()
        _enabled.value = true
    }

    /** Switches the lock off when [current] is the PIN / password. */
    suspend fun disable(current: String): Attempt {
        val attempt = check(current)
        if (attempt == Attempt.Accepted) {
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
        val failed = failures.get()
        var until = blockedUntil.get()
        // A stored end further away than the wait it was set for (the clock was set back since): the wait from now
        val wait = waitMillis(failed)
        if (until - now > wait) {
            until = now + wait
            blockedUntil.set(until)
        }
        if (now < until) return Attempt.Blocked((until - now + 999) / 1000)
        val stored = secret.get()
        val matches = stored.isNotEmpty() && withContext(Dispatchers.Default) { AppLockSecret.verify(input, stored) }
        if (matches) {
            resetFailures()
            return Attempt.Accepted
        }
        failures.set(failed + 1)
        if (failed + 1 >= FREE_ATTEMPTS) blockedUntil.set(now + waitMillis(failed + 1))
        return Attempt.Wrong
    }

    /** The wait after [failed] wrong entries in a row: none before [FREE_ATTEMPTS], then 30 s more each, at most 5 min. */
    private fun waitMillis(failed: Int): Long = when {
        failed < FREE_ATTEMPTS -> 0L
        else -> (30_000L * (failed - FREE_ATTEMPTS + 1)).coerceAtMost(MAX_WAIT_MILLIS)
    }

    private fun resetFailures() {
        failures.delete()
        blockedUntil.delete()
    }

    companion object {
        /** The fewest characters a PIN / password may have. */
        const val MIN_LENGTH = 4

        /** Wrong entries in a row before each further try has to wait. */
        const val FREE_ATTEMPTS = 5

        private const val MAX_WAIT_MILLIS = 5 * 60_000L

        internal const val SECRET_KEY = "desktop_app_lock_secret"
        internal const val FAILURES_KEY = "desktop_app_lock_failures"
        internal const val BLOCKED_UNTIL_KEY = "desktop_app_lock_blocked_until"
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
