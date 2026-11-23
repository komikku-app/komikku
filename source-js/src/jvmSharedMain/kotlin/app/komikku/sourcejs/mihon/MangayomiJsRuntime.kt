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
import app.komikku.sourcejs.model.JsPreference
import app.komikku.sourcejs.model.JsSourceMetadata
import eu.kanade.tachiyomi.network.NetworkHelper
import exh.log.xLogE
import exh.log.xLogI
import exh.log.xLogW
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap

/**
 * Builds [MangayomiJsSource]s on one [JsExtensionHost], shared by every JavaScript source of the app (Android and
 * desktop): the script's HTTP goes through the app's client ([OkHttpHostHttp], so cookies, rate limits, DNS over HTTPS
 * and Cloudflare handling apply), its `SharedPreferences` through the source's preference file
 * ([SharedPreferencesHostPreferences], typed by the source's declared preferences once its metadata is known), its
 * console and the host's warnings to the app log (`exh.log`).
 *
 * Calls of all its sources run at most [concurrentCalls] at a time (each one is a JavaScript context of its own, on its
 * own thread), next to each source's own limit ([MangayomiJsSource.DEFAULT_CONCURRENT_CALLS]).
 *
 * The store of installed sources (I4) owns one runtime and calls [source] for each installed extension text.
 *
 * @param preferences the preference file of the source with the given Komikku id ([JsSourceIds.preferencesName]).
 * @param client the app's HTTP client, read for every request (it may be rebuilt when network settings change).
 * @param concurrentCalls the calls that may run at once across the runtime's sources.
 */
class MangayomiJsRuntime(
    private val preferences: (komikkuId: Long) -> SharedPreferences,
    private val client: () -> OkHttpClient,
    limits: JsHostLimits = JsHostLimits(),
    log: HostLog = XLogHostLog,
    concurrentCalls: Int = DEFAULT_CONCURRENT_CALLS,
) {

    /** The runtime of the app: [application]'s preference files and [network]'s client. */
    constructor(application: Application, network: NetworkHelper, limits: JsHostLimits = JsHostLimits()) : this(
        preferences = { id -> application.getSharedPreferences(JsSourceIds.preferencesName(id), Context.MODE_PRIVATE) },
        client = { network.client },
        limits = limits,
    )

    /** The sources built so far, by Mangayomi id: their metadata types the stored preferences. */
    private val sources = ConcurrentHashMap<Long, MangayomiJsSource>()

    /** Shared by every source of this runtime; each source takes its own permit first. */
    private val sharedPermits = Semaphore(concurrentCalls)

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
     *
     * The first read cannot know the new text's declared preferences yet (it reads them), so the stored values are
     * typed by the earlier source's declarations, or by their stored types. When the new declarations type a stored
     * value differently (a value of another type now reads as the default, a set gets the declared order), the
     * metadata is read once more after registering the source, so headers and `supportsLatest` see what later calls see.
     */
    suspend fun source(descriptor: JsSourceDescriptor, code: String, metadata: JsSourceMetadata? = null): MangayomiJsSource {
        val source = prepare(descriptor, code, metadata)
        register(source, metadataRead = metadata == null)
        return source
    }

    /**
     * The source for [descriptor] whose extension text is [code], like [source] but not registered yet: the source of
     * the same id built earlier stays in use (and open) until [register] swaps it. A prepared source that is not
     * registered must be [closed][MangayomiJsSource.close].
     */
    suspend fun prepare(descriptor: JsSourceDescriptor, code: String, metadata: JsSourceMetadata? = null): MangayomiJsSource {
        val extension = extension(descriptor, code)
        val initial = metadata ?: sharedPermits.withPermit { extension.metadata() }
        return MangayomiJsSource(
            extension = extension,
            initialMetadata = initial,
            clientProvider = client,
            preferencesProvider = { preferencesOf(descriptor) },
            sharedPermits = sharedPermits,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        )
    }

    /**
     * Registers the [prepare]d [source], replacing (and closing) the earlier one of its id. [metadataRead]: its metadata
     * was read from its text while the earlier declarations typed the stored values, so it is read once more when the
     * new declarations type them differently (see [source]).
     */
    suspend fun register(source: MangayomiJsSource, metadataRead: Boolean = true) {
        val descriptor = source.descriptor
        val readWith = sources[descriptor.id]?.metadata?.preferences.orEmpty()
        sources.put(descriptor.id, source)?.takeIf { it !== source }?.close()
        if (metadataRead && readsDiffer(preferencesOf(descriptor), readWith, source.metadata.preferences)) source.refreshMetadata()
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

    /** Whether a stored value of [stored] reads differently under the declarations [before] and [after]. */
    private fun readsDiffer(stored: SharedPreferences, before: List<JsPreference>, after: List<JsPreference>): Boolean {
        val old = SharedPreferencesHostPreferences(stored) { before }
        val new = SharedPreferencesHostPreferences(stored) { after }
        return stored.all.keys.any { key ->
            !key.startsWith(SharedPreferencesHostPreferences.STRING_KEY_PREFIX) && old.getValue(key) != new.getValue(key)
        }
    }

    /** Script console output and host warnings in the app log (`exh.log`), prefixed with the source. */
    object XLogHostLog : HostLog {
        override fun log(level: HostLog.Level, source: JsSourceDescriptor, message: String) {
            val line = "[JS ${source.name}] $message"
            when (level) {
                HostLog.Level.INFO -> xLogI(line)
                HostLog.Level.WARN -> xLogW(line)
                HostLog.Level.ERROR -> xLogE(line)
            }
        }
    }

    companion object {
        /** Calls of all sources of one runtime that may run at once. */
        const val DEFAULT_CONCURRENT_CALLS = 4
    }
}
