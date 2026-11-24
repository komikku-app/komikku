package eu.kanade.tachiyomi.ui.base.delegate

import android.app.Activity
import android.content.Intent
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.ui.security.UnlockActivity
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil.isAuthenticationSupported
import eu.kanade.tachiyomi.util.view.setSecureScreen
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.util.Calendar

interface SecureActivityDelegate {
    fun registerSecureActivity(activity: AppCompatActivity)

    companion object {
        // SY -->
        // KMK --> the values of the shared AppLockRules
        const val LOCK_SUNDAY = AppLockRules.LOCK_SUNDAY
        const val LOCK_MONDAY = AppLockRules.LOCK_MONDAY
        const val LOCK_TUESDAY = AppLockRules.LOCK_TUESDAY
        const val LOCK_WEDNESDAY = AppLockRules.LOCK_WEDNESDAY
        const val LOCK_THURSDAY = AppLockRules.LOCK_THURSDAY
        const val LOCK_FRIDAY = AppLockRules.LOCK_FRIDAY
        const val LOCK_SATURDAY = AppLockRules.LOCK_SATURDAY
        const val LOCK_ALL_DAYS = AppLockRules.LOCK_ALL_DAYS
        // KMK <--
        // SY <--

        /**
         * Set to true if we need the first activity to authenticate.
         *
         * Always require unlock if app is killed.
         */
        var requireUnlock = true

        fun onApplicationStopped() {
            val preferences = Injekt.get<SecurityPreferences>()
            if (!preferences.useAuthenticator().get()) return

            if (!AuthenticatorUtil.isAuthenticating) {
                // Return if app is closed in locked state
                if (requireUnlock) return
                // Save app close time if lock is delayed
                if (preferences.lockAppAfter().get() > 0) {
                    preferences.lastAppClosed().set(System.currentTimeMillis())
                }
            }
        }

        // SY -->
        private fun canLockNow(preferences: SecurityPreferences): Boolean {
            // KMK --> the shared AppLockRules (also used by the desktop app lock)
            return AppLockRules.canLockNow(
                timeRanges = preferences.authenticatorTimeRanges().get(),
                lockedDays = preferences.authenticatorDays().get(),
                now = Calendar.getInstance(),
            )
            // KMK <--
        }
        // SY <--

        /**
         * Checks if unlock is needed when app comes foreground.
         */
        fun onApplicationStart() {
            val preferences = Injekt.get<SecurityPreferences>()
            if (!preferences.useAuthenticator().get()) return

            val lastClosedPref = preferences.lastAppClosed()

            // `requireUnlock` can be true on process start or if app was closed in locked state
            if (!AuthenticatorUtil.isAuthenticating && !requireUnlock) {
                requireUnlock =
                    /* SY --> */ canLockNow(preferences) &&
                    /* SY <-- */
                    // KMK --> the shared AppLockRules.isLockDue (Never / Always / after the delay)
                    AppLockRules.isLockDue(
                        lockAppAfter = preferences.lockAppAfter().get(),
                        lastClosed = lastClosedPref.get(),
                        nowMillis = System.currentTimeMillis(),
                    )
                // KMK <--
            }

            lastClosedPref.delete()
        }

        fun unlock() {
            requireUnlock = false
        }
    }
}

class SecureActivityDelegateImpl : SecureActivityDelegate, DefaultLifecycleObserver {

    private lateinit var activity: AppCompatActivity

    private val preferences: BasePreferences by injectLazy()
    private val securityPreferences: SecurityPreferences by injectLazy()

    override fun registerSecureActivity(activity: AppCompatActivity) {
        this.activity = activity
        activity.lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        setSecureScreen()
    }

    override fun onResume(owner: LifecycleOwner) {
        setAppLock()
    }

    private fun setSecureScreen() {
        val secureScreenFlow = securityPreferences.secureScreen().changes()
        val incognitoModeFlow = preferences.incognitoMode().changes()
        combine(secureScreenFlow, incognitoModeFlow) { secureScreen, incognitoMode ->
            secureScreen == SecurityPreferences.SecureScreenMode.ALWAYS ||
                (secureScreen == SecurityPreferences.SecureScreenMode.INCOGNITO && incognitoMode)
        }
            .onEach(activity.window::setSecureScreen)
            .launchIn(activity.lifecycleScope)
    }

    private fun setAppLock() {
        if (!securityPreferences.useAuthenticator().get()) return
        if (activity.isAuthenticationSupported()) {
            if (!SecureActivityDelegate.requireUnlock) return
            activity.startActivity(Intent(activity, UnlockActivity::class.java))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0)
            } else {
                @Suppress("DEPRECATION")
                activity.overridePendingTransition(0, 0)
            }
        } else {
            securityPreferences.useAuthenticator().set(false)
        }
    }
}
