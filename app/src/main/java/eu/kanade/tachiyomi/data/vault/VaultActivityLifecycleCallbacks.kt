package eu.kanade.tachiyomi.data.vault

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.PowerManager
import androidx.core.content.getSystemService
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.vault.VaultSession

/**
 * Records the moment the app moves to the background using the activity lifecycle.
 *
 * [androidx.lifecycle.ProcessLifecycleOwner] dispatches its stop event 700 ms after the last
 * activity stops. Work-profile freezers can freeze the cached process before that, so the event
 * is never delivered and the vault never sees the background timestamp. The last activity's
 * stop callback runs before the process is cached/frozen, so it is the reliable signal.
 */
class VaultActivityLifecycleCallbacks : Application.ActivityLifecycleCallbacks {

    private var startedActivities = 0

    override fun onActivityStarted(activity: Activity) {
        startedActivities++
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivities = (startedActivities - 1).coerceAtLeast(0)
        if (startedActivities != 0) return
        if (!VaultSession.isActive) return

        VaultSession.onAppBackgrounded()

        val screenOff = activity.getSystemService<PowerManager>()?.isInteractive == false
        if (screenOff) {
            VaultSession.onScreenOff()
        }

        logcat(LogPriority.INFO, tag = "VaultSession") {
            "App moved to the background (screenOff=$screenOff)"
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit
}
