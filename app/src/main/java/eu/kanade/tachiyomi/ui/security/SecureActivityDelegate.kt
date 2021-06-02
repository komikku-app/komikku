package eu.kanade.tachiyomi.ui.security

import android.content.Intent
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.category.biometric.TimeRange
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil
import exh.util.hours
import exh.util.minutes
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import uy.kohesive.injekt.injectLazy
import java.util.Calendar
import java.util.Date

class SecureActivityDelegate(private val activity: FragmentActivity) {

    private val preferences: PreferencesHelper by injectLazy()

    fun onCreate() {
        preferences.secureScreen().asFlow()
            .onEach {
                if (it) {
                    activity.window.setFlags(
                        WindowManager.LayoutParams.FLAG_SECURE,
                        WindowManager.LayoutParams.FLAG_SECURE
                    )
                } else {
                    activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
            .launchIn(activity.lifecycleScope)
    }

    fun onResume() {
        if (preferences.useAuthenticator().get()) {
            if (AuthenticatorUtil.isSupported(activity)) {
                if (isAppLocked()) {
                    activity.startActivity(Intent(activity, UnlockActivity::class.java))
                    activity.overridePendingTransition(0, 0)
                }
            } else {
                preferences.useAuthenticator().set(false)
            }
        }
    }

    private fun isAppLocked(): Boolean {
        if (!locked) {
            return false
        }

        // SY -->
        val today: Calendar = Calendar.getInstance()
        val timeRanges = preferences.authenticatorTimeRanges().get().mapNotNull { TimeRange.fromPreferenceString(it) }
        if (timeRanges.isNotEmpty()) {
            val now = today.get(Calendar.HOUR_OF_DAY).hours + today.get(Calendar.MINUTE).minutes
            val locked = timeRanges.any { now in it }
            if (!locked) {
                return false
            }
        }

        val lockedDays = preferences.authenticatorDays().get()
        val locked = lockedDays == LOCK_ALL_DAYS || when (today.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SUNDAY -> (lockedDays and LOCK_SUNDAY) == LOCK_SUNDAY
            Calendar.MONDAY -> (lockedDays and LOCK_MONDAY) == LOCK_MONDAY
            Calendar.TUESDAY -> (lockedDays and LOCK_TUESDAY) == LOCK_TUESDAY
            Calendar.WEDNESDAY -> (lockedDays and LOCK_WEDNESDAY) == LOCK_WEDNESDAY
            Calendar.THURSDAY -> (lockedDays and LOCK_THURSDAY) == LOCK_THURSDAY
            Calendar.FRIDAY -> (lockedDays and LOCK_FRIDAY) == LOCK_FRIDAY
            Calendar.SATURDAY -> (lockedDays and LOCK_SATURDAY) == LOCK_SATURDAY
            else -> false
        }

        if (!locked) {
            return false
        }
        // SY <--

        return preferences.lockAppAfter().get() <= 0 ||
            Date().time >= preferences.lastAppUnlock().get() + 60 * 1000 * preferences.lockAppAfter().get()
    }

    companion object {
        var locked: Boolean = true

        const val LOCK_SUNDAY = 0x40
        const val LOCK_MONDAY = 0x20
        const val LOCK_TUESDAY = 0x10
        const val LOCK_WEDNESDAY = 0x8
        const val LOCK_THURSDAY = 0x4
        const val LOCK_FRIDAY = 0x2
        const val LOCK_SATURDAY = 0x1
        const val LOCK_ALL_DAYS = 0x7F
    }
}
