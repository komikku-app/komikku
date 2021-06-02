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

        return preferences.lockAppAfter().get() <= 0 ||
            Date().time >= preferences.lastAppUnlock().get() + 60 * 1000 * preferences.lockAppAfter().get() &&
            preferences.authenticatorTimeRanges().get().mapNotNull { TimeRange.fromPreferenceString(it) }.let { timeRanges ->
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
