package app.komikku.sourcejs

import app.komikku.sourcejs.bridge.DarwinHostHttp
import app.komikku.sourcejs.bridge.HostHttp
import app.komikku.sourcejs.bridge.HostLog
import app.komikku.sourcejs.bridge.HostPreferencesProvider
import app.komikku.sourcejs.bridge.JsHostServices
import app.komikku.sourcejs.bridge.NSUserDefaultsHostPreferences
import app.komikku.sourcejs.mihon.JsSourceIds
import app.komikku.sourcejs.model.JsPreference
import platform.Foundation.NSLog
import platform.Foundation.NSUserDefaults

/**
 * The JavaScript source host of an iOS app: one [JsExtensionHost] with the iOS services, so a Mangayomi source is one
 * call away: `runtime.extension(descriptor, code).getPopular(1)`.
 *
 * - HTTP: [DarwinHostHttp] (NSURLSession with the shared `NSHTTPCookieStorage`, every cookie accepted) unless [http]
 *   is given.
 * - Preferences: [NSUserDefaultsHostPreferences], one `NSUserDefaults` suite per source named [suiteName] of its
 *   Komikku id (by default `source_<komikkuId>`, the name of the source's preference file on Android and desktop).
 *   [declaredPreferences] types the stored values once the source's `getSourcePreferences()` is known (the app's store
 *   caches its metadata); empty, every stored type is read as stored.
 * - Log: [NSLogHostLog]; crypto and DOM are the common implementations; no WebView yet.
 *
 * Unlike the JVM's `MangayomiJsRuntime` there is no source adapter (Mihon's `HttpSource` is JVM-only): the app calls
 * [JsExtension] directly.
 *
 * @param http the HTTP client; `null` creates a [DarwinHostHttp] that [close] closes.
 */
class IosJsSourceRuntime(
    http: HostHttp? = null,
    private val suiteName: (komikkuId: Long) -> String = JsSourceIds::preferencesName,
    private val declaredPreferences: (JsSourceDescriptor) -> List<JsPreference> = { emptyList() },
    limits: JsHostLimits = JsHostLimits(),
    log: HostLog = NSLogHostLog,
) {
    private val ownedHttp: DarwinHostHttp? = if (http == null) DarwinHostHttp() else null

    private val preferencesProvider = HostPreferencesProvider { descriptor: JsSourceDescriptor -> preferences(descriptor) }

    val host = JsExtensionHost(
        JsHostServices(http = http ?: ownedHttp!!, preferences = preferencesProvider, log = log),
        limits = limits,
    )

    /** The provider calls of [descriptor]'s extension text [code]. */
    fun extension(descriptor: JsSourceDescriptor, code: String): JsExtension = host.extension(descriptor, code)

    /** The stored preferences of [descriptor] (the ones its script reads), e.g. for a settings screen. */
    fun preferences(descriptor: JsSourceDescriptor): NSUserDefaultsHostPreferences =
        NSUserDefaultsHostPreferences(defaultsOf(descriptor)) { declaredPreferences(descriptor) }

    /** The `NSUserDefaults` suite of [descriptor]. */
    fun defaultsOf(descriptor: JsSourceDescriptor): NSUserDefaults = NSUserDefaults(suiteName = suiteNameOf(descriptor))

    /** Deletes every stored preference of [descriptor] (uninstalled). */
    fun clearPreferences(descriptor: JsSourceDescriptor) {
        NSUserDefaults.standardUserDefaults.removePersistentDomainForName(suiteNameOf(descriptor))
    }

    /** Closes the HTTP client this runtime created. */
    fun close() {
        ownedHttp?.close()
    }

    private fun suiteNameOf(descriptor: JsSourceDescriptor) = suiteName(JsSourceIds.komikkuId(descriptor.id))

    /** Script console output and host warnings in the device log, prefixed with the source. */
    object NSLogHostLog : HostLog {
        override fun log(level: HostLog.Level, source: JsSourceDescriptor, message: String) {
            // The line is the format string: escape `%`.
            NSLog("[JS ${source.name}] ${level.name}: $message".replace("%", "%%"))
        }
    }
}
