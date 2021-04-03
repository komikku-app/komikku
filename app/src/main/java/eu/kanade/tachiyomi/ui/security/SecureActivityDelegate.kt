package eu.kanade.tachiyomi.ui.security

import android.content.Intent
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.category.biometric.TimeRange
import eu.kanade.tachiyomi.util.system.BiometricUtil
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import uy.kohesive.injekt.injectLazy
import java.util.Calendar
import java.util.Date
import kotlin.time.ExperimentalTime
import kotlin.time.hours
import kotlin.time.minutes

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
        val lockApp = preferences.useBiometricLock().get()
        if (lockApp && BiometricUtil.isSupported(activity)) {
            if (isAppLocked()) {
                val intent = Intent(activity, BiometricUnlockActivity::class.java)
                activity.startActivity(intent)
                activity.overridePendingTransition(0, 0)
            }
        } else if (lockApp) {
            preferences.useBiometricLock().set(false)
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun isAppLocked(): Boolean {
        return locked &&
            (
                preferences.lockAppAfter().get() <= 0 ||
                    Date().time >= preferences.lastAppUnlock().get() + 60 * 1000 * preferences.lockAppAfter().get()
                ) && preferences.biometricTimeRanges().get().mapNotNull { TimeRange.fromPreferenceString(it) }.let { timeRanges ->
            if (timeRanges.isNotEmpty()) {
                val today: Calendar = Calendar.getInstance()
                val now = today.get(Calendar.HOUR_OF_DAY).hours + today.get(Calendar.MINUTE).minutes
                timeRanges.any { now in it.startTime..it.endTime }
            } else true
        }
    }

    companion object {
        var locked: Boolean = true
    }
}
