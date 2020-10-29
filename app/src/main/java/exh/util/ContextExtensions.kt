package exh.util

import android.app.job.JobScheduler
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.net.wifi.WifiManager
import androidx.core.content.getSystemService

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
