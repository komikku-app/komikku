package eu.kanade.tachiyomi.ui.base.delegate

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import eu.kanade.tachiyomi.data.preference.PreferenceValues
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.base.delegate.SecureActivityDelegate.Companion.LOCK_ALL_DAYS
import eu.kanade.tachiyomi.ui.base.delegate.SecureActivityDelegate.Companion.LOCK_FRIDAY
import eu.kanade.tachiyomi.ui.base.delegate.SecureActivityDelegate.Companion.LOCK_MONDAY
import eu.kanade.tachiyomi.ui.base.delegate.SecureActivityDelegate.Companion.LOCK_SATURDAY
import eu.kanade.tachiyomi.ui.base.delegate.SecureActivityDelegate.Companion.LOCK_SUNDAY
import eu.kanade.tachiyomi.ui.base.delegate.SecureActivityDelegate.Companion.LOCK_THURSDAY
import eu.kanade.tachiyomi.ui.base.delegate.SecureActivityDelegate.Companion.LOCK_TUESDAY
import eu.kanade.tachiyomi.ui.base.delegate.SecureActivityDelegate.Companion.LOCK_WEDNESDAY
import eu.kanade.tachiyomi.ui.category.biometric.TimeRange
import eu.kanade.tachiyomi.ui.security.UnlockActivity
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil.isAuthenticationSupported
import eu.kanade.tachiyomi.util.view.setSecureScreen
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import uy.kohesive.injekt.injectLazy
import java.util.Calendar
import java.util.Date
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

interface SecureActivityDelegate {
    fun registerSecureActivity(activity: AppCompatActivity)

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

class SecureActivityDelegateImpl : SecureActivityDelegate, DefaultLifecycleObserver {

    private lateinit var activity: AppCompatActivity

    private val preferences: PreferencesHelper by injectLazy()

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
        val secureScreenFlow = preferences.secureScreen().asFlow()
        val incognitoModeFlow = preferences.incognitoMode().asFlow()
        combine(secureScreenFlow, incognitoModeFlow) { secureScreen, incognitoMode ->
            secureScreen == PreferenceValues.SecureScreenMode.ALWAYS ||
                secureScreen == PreferenceValues.SecureScreenMode.INCOGNITO && incognitoMode
        }
            .onEach { activity.window.setSecureScreen(it) }
            .launchIn(activity.lifecycleScope)
    }

    private fun setAppLock() {
        if (!preferences.useAuthenticator().get()) return
        if (activity.isAuthenticationSupported()) {
            if (!isAppLocked()) return
            activity.startActivity(Intent(activity, UnlockActivity::class.java))
            activity.overridePendingTransition(0, 0)
        } else {
            preferences.useAuthenticator().set(false)
        }
    }

    private fun isAppLocked(): Boolean {
        if (!SecureActivityDelegate.locked) return false

        // SY -->
        val today: Calendar = Calendar.getInstance()
        val timeRanges = preferences.authenticatorTimeRanges().get()
            .mapNotNull { TimeRange.fromPreferenceString(it) }
        if (timeRanges.isNotEmpty()) {
            val now = today.get(Calendar.HOUR_OF_DAY).hours + today.get(Calendar.MINUTE).minutes
            val lockedNow = timeRanges.any { now in it }
            if (!lockedNow) {
                return false
            }
        }

        val lockedDays = preferences.authenticatorDays().get()
        val lockedToday = lockedDays == LOCK_ALL_DAYS || when (today.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SUNDAY -> (lockedDays and LOCK_SUNDAY) == LOCK_SUNDAY
            Calendar.MONDAY -> (lockedDays and LOCK_MONDAY) == LOCK_MONDAY
            Calendar.TUESDAY -> (lockedDays and LOCK_TUESDAY) == LOCK_TUESDAY
            Calendar.WEDNESDAY -> (lockedDays and LOCK_WEDNESDAY) == LOCK_WEDNESDAY
            Calendar.THURSDAY -> (lockedDays and LOCK_THURSDAY) == LOCK_THURSDAY
            Calendar.FRIDAY -> (lockedDays and LOCK_FRIDAY) == LOCK_FRIDAY
            Calendar.SATURDAY -> (lockedDays and LOCK_SATURDAY) == LOCK_SATURDAY
            else -> false
        }

        if (!lockedToday) {
            return false
        }
        // SY <--

        return preferences.lockAppAfter().get() <= 0 ||
            Date().time >= preferences.lastAppClosed().get() + 60 * 1000 * preferences.lockAppAfter().get()
    }
}
