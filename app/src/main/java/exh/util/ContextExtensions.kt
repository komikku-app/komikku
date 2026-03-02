package exh.util

import android.app.job.JobScheduler
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.getSystemService
import eu.kanade.tachiyomi.util.system.powerManager

/**
 * Property to get the wifi manager from the context.
 */
val Context.wifiManager: WifiManager
    get() = applicationContext.getSystemService()!!

val Context.clipboardManager: ClipboardManager
    get() = applicationContext.getSystemService()!!

val Context.jobScheduler: JobScheduler
    get() = applicationContext.getSystemService()!!

val Context.isInNightMode: Boolean
    get() {
        val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return currentNightMode == Configuration.UI_MODE_NIGHT_YES
    }

fun Context.createPartialWakeLock(tag: String): PowerManager.WakeLock =
    powerManager.newWakeLock(
        PowerManager.PARTIAL_WAKE_LOCK,
        tag,
    )

fun Context.createWifiLock(tag: String): WifiManager.WifiLock =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        wifiManager.createWifiLock(
            WifiManager.WIFI_MODE_FULL_LOW_LATENCY,
            tag,
        )
    } else {
        @Suppress("DEPRECATION")
        wifiManager.createWifiLock(
            WifiManager.WIFI_MODE_FULL_HIGH_PERF,
            tag,
        )
    }
