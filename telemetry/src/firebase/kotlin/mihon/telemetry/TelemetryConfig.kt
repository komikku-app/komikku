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

private val MIHON_PACKAGES = hashSetOf("app.komikkurnz", "app.komikkurnz.beta")
private const val MIHON_CERTIFICATE_FINGERPRINT =
    "91:2F:BF:F3:15:66:10:34:6D:90:10:A6:FD:C2:DF:6B:CA:0F:AD:19:60:1B:C6:83:A0:67:BE:EA:DA:5C:BC:FC"
