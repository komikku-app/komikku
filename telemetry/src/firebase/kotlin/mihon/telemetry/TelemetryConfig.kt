package mihon.telemetry

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

object TelemetryConfig {
    private var analytics: FirebaseAnalytics? = null
    private var crashlytics: FirebaseCrashlytics? = null

    fun init(context: Context, isPreviewBuildType: Boolean, commitCount: String) {
        // To stop forks/test builds from polluting our data
        if (!context.isMihonProductionApp()) return

        analytics = FirebaseAnalytics.getInstance(context)
        FirebaseApp.initializeApp(context)
        crashlytics = FirebaseCrashlytics.getInstance()
        // KMK -->
        if (isPreviewBuildType) {
            analytics?.setUserProperty("preview_version", commitCount)
        }
        // KMK <--
    }

    fun setAnalyticsEnabled(enabled: Boolean) {
        analytics?.setAnalyticsCollectionEnabled(enabled)
    }

    fun setCrashlyticsEnabled(enabled: Boolean) {
        crashlytics?.isCrashlyticsCollectionEnabled = enabled
    }

    private fun Context.isMihonProductionApp(): Boolean {
        if (packageName !in MIHON_PACKAGES) return false

        return packageManager.getPackageInfo(packageName, SignatureFlags)
            .getCertificateFingerprints()
            .any { it == MIHON_CERTIFICATE_FINGERPRINT }
    }
}

private val MIHON_PACKAGES = hashSetOf("app.komikku", "app.komikku.beta")
private const val MIHON_CERTIFICATE_FINGERPRINT =
    "CB:EC:12:1A:A8:2E:BB:02:AA:A7:38:06:99:2E:03:68:A9:7D:47:B5:45:1E:D6:52:48:16:D0:30:84:C4:59:05"
