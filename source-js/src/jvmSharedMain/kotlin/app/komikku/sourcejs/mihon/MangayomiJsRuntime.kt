package app.komikku.sourcejs.mihon

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import app.komikku.sourcejs.JsExtension
import app.komikku.sourcejs.JsExtensionHost
import app.komikku.sourcejs.JsHostLimits
import app.komikku.sourcejs.JsSourceDescriptor
import app.komikku.sourcejs.bridge.HostHttp
import app.komikku.sourcejs.bridge.HostLog
import app.komikku.sourcejs.bridge.HostPreferencesProvider
import app.komikku.sourcejs.bridge.JsHostServices
import app.komikku.sourcejs.bridge.OkHttpHostHttp
import app.komikku.sourcejs.model.JsSourceMetadata
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import logcat.LogPriority
import okhttp3.OkHttpClient
import tachiyomi.core.common.util.system.logcat
import java.util.concurrent.ConcurrentHashMap

/**
 * Builds [MangayomiJsSource]s on one [JsExtensionHost], shared by every JavaScript source of the app (Android and
 * desktop): the script's HTTP goes through the app's client ([OkHttpHostHttp], so cookies, rate limits, DNS over HTTPS
 * and Cloudflare handling apply), its `SharedPreferences` through the source's preference file
 * ([SharedPreferencesHostPreferences], typed by the source's declared preferences once its metadata is known), its
 * console and the host's warnings to logcat.
 *
 * The store of installed sources (I4) owns one runtime and calls [source] for each installed extension text.
 *
 * @param preferences the preference file of the source with the given Komikku id ([JsSourceIds.preferencesName]).
 * @param client the app's HTTP client, read for every request (it may be rebuilt when network settings change).
 */
class MangayomiJsRuntime(
    private val preferences: (komikkuId: Long) -> SharedPreferences,
    private val client: () -> OkHttpClient,
    limits: JsHostLimits = JsHostLimits(),
    log: HostLog = LogcatHostLog,
) {

    /** The runtime of the app: [application]'s preference files and [network]'s client. */
    constructor(application: Application, network: NetworkHelper, limits: JsHostLimits = JsHostLimits()) : this(
        preferences = { id -> application.getSharedPreferences(JsSourceIds.preferencesName(id), Context.MODE_PRIVATE) },
        client = { network.client },
        limits = limits,
    )

    /** The sources built so far, by Mangayomi id: their metadata types the stored preferences. */
    private val sources = ConcurrentHashMap<Long, MangayomiJsSource>()

    private val http = HostHttp { request -> OkHttpHostHttp(client()).execute(request) }

    private val preferencesProvider = HostPreferencesProvider { descriptor: JsSourceDescriptor ->
        SharedPreferencesHostPreferences(preferencesOf(descriptor)) {
            sources[descriptor.id]?.metadata?.preferences.orEmpty()
        }
    }

    val host = JsExtensionHost(JsHostServices(http = http, preferences = preferencesProvider, log = log), limits = limits)

    /** The provider calls of [descriptor]'s extension text [code], without the source adapter. */
    fun extension(descriptor: JsSourceDescriptor, code: String): JsExtension = host.extension(descriptor, code)

    /**
     * The source for [descriptor] whose extension text is [code]. [metadata] is read from the text when not given
     * (a cache of the store); failing to load the text fails with its [app.komikku.sourcejs.JsSourceException]. A source built again for
     * the same id (an update) replaces the earlier one, which is [closed][MangayomiJsSource.close].
     */
    suspend fun source(descriptor: JsSourceDescriptor, code: String, metadata: JsSourceMetadata? = null): MangayomiJsSource {
        val extension = extension(descriptor, code)
        val initial = metadata ?: extension.metadata()
        val source = MangayomiJsSource(
            extension = extension,
            initialMetadata = initial,
            clientProvider = client,
            preferencesProvider = { preferencesOf(descriptor) },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        )
        sources.put(descriptor.id, source)?.takeIf { it !== source }?.close()
        return source
    }

    /** Forgets and closes the source of [descriptor] (uninstalled). */
    fun remove(descriptor: JsSourceDescriptor) {
        sources.remove(descriptor.id)?.close()
    }

    /** Closes every source built by this runtime. */
    fun close() {
        sources.values.toList().forEach { it.close() }
        sources.clear()
    }

    private fun preferencesOf(descriptor: JsSourceDescriptor): SharedPreferences =
        preferences(JsSourceIds.komikkuId(descriptor.id))

    /** Script console output and host warnings in logcat, tagged with the source. */
    object LogcatHostLog : HostLog {
        override fun log(level: HostLog.Level, source: JsSourceDescriptor, message: String) {
            val priority = when (level) {
                HostLog.Level.INFO -> LogPriority.INFO
                HostLog.Level.WARN -> LogPriority.WARN
                HostLog.Level.ERROR -> LogPriority.ERROR
            }
            logcat(priority, tag = "JS ${source.name}") { message }
        }
    }
}
