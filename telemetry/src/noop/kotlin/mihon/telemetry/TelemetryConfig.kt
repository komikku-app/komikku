package mihon.telemetry

import android.content.Context

@Suppress("UNUSED_PARAMETER", "unused")
object TelemetryConfig {
    fun init(context: Context, isPreviewBuildType: Boolean, commitCount: String) = Unit

    fun setAnalyticsEnabled(enabled: Boolean) = Unit

    fun setCrashlyticsEnabled(enabled: Boolean) = Unit
}
