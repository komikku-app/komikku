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
import eu.kanade.tachiyomi.ui.category.biometric.TimeRange
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
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

interface SecureActivityDelegate {
    fun registerSecureActivity(activity: AppCompatActivity)

    companion object {
        // SY -->
        const val LOCK_SUNDAY = 0x40
        const val LOCK_MONDAY = 0x20
        const val LOCK_TUESDAY = 0x10
        const val LOCK_WEDNESDAY = 0x8
        const val LOCK_THURSDAY = 0x4
        const val LOCK_FRIDAY = 0x2
        const val LOCK_SATURDAY = 0x1
        const val LOCK_ALL_DAYS = 0x7F
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
            val today: Calendar = Calendar.getInstance()
            val timeRanges = preferences.authenticatorTimeRanges().get()
                .mapNotNull { TimeRange.fromPreferenceString(it) }
            val canLockNow = if (timeRanges.isNotEmpty()) {
                val now = today.get(Calendar.HOUR_OF_DAY).hours + today.get(Calendar.MINUTE).minutes
                timeRanges.any { now in it }
            } else {
                true
            }

            val lockedDays = preferences.authenticatorDays().get()
            val canLockToday = lockedDays == LOCK_ALL_DAYS ||
                when (today.get(Calendar.DAY_OF_WEEK)) {
                    Calendar.SUNDAY -> (lockedDays and LOCK_SUNDAY) == LOCK_SUNDAY
                    Calendar.MONDAY -> (lockedDays and LOCK_MONDAY) == LOCK_MONDAY
                    Calendar.TUESDAY -> (lockedDays and LOCK_TUESDAY) == LOCK_TUESDAY
                    Calendar.WEDNESDAY -> (lockedDays and LOCK_WEDNESDAY) == LOCK_WEDNESDAY
                    Calendar.THURSDAY -> (lockedDays and LOCK_THURSDAY) == LOCK_THURSDAY
                    Calendar.FRIDAY -> (lockedDays and LOCK_FRIDAY) == LOCK_FRIDAY
                    Calendar.SATURDAY -> (lockedDays and LOCK_SATURDAY) == LOCK_SATURDAY
                    else -> false
                }

            return canLockNow && canLockToday
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
                    /* SY <-- */ when (val lockDelay = preferences.lockAppAfter().get()) {
                        -1 -> false // Never
                        0 -> true // Always
                        else -> lastClosedPref.get() + lockDelay * 60_000 <= System.currentTimeMillis()
                    }
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
