package app.komikku.sourcejs

import app.komikku.sourcejs.JsExtensionHost.CallMode
import app.komikku.sourcejs.JsExtensionHost.CallOutcome
import app.komikku.sourcejs.bridge.HostLog
import app.komikku.sourcejs.model.JsFilter
import app.komikku.sourcejs.model.JsManga
import app.komikku.sourcejs.model.JsMangaPage
import app.komikku.sourcejs.model.JsPage
import app.komikku.sourcejs.model.JsPreference
import app.komikku.sourcejs.model.JsResultParser
import app.komikku.sourcejs.model.JsSourceMetadata
import app.komikku.sourcejs.model.MalformedResultFailure
import app.komikku.sourcejs.model.jsonBooleanOrNull
import app.komikku.sourcejs.model.toJsonArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The provider calls (contract 3) of one source [source] whose extension text is [code], from
 * [JsExtensionHost.extension]. Each call runs in a fresh context and returns typed models parsed leniently from the
 * JSON the extension returns; failures are [JsSourceException]s.
 */
class JsExtension internal constructor(
    private val host: JsExtensionHost,
    val source: JsSourceDescriptor,
    val code: String,
) {
    private val log get() = host.services.log

    /** `getPopular(page)`, page 1-based. */
    suspend fun getPopular(page: Int): JsMangaPage =
        callAsync("getPopular($page)", "getPopular", JsonPrimitive(page)) { JsResultParser.mangaPage(it) }

    /** `getLatestUpdates(page)`; only meaningful when [supportsLatest] is true. */
    suspend fun getLatestUpdates(page: Int): JsMangaPage =
        callAsync("getLatestUpdates($page)", "getLatestUpdates", JsonPrimitive(page)) { JsResultParser.mangaPage(it) }

    /**
     * `search(query, page, filters)`. [filters] is the list from [getFilterList] / [metadata] with the user's
     * state, in the original order; `null` sends the source's default list (never `[]`, which breaks sources that
     * read filters by position, contract 5.1).
     */
    suspend fun search(query: String, page: Int, filters: List<JsFilter>? = null): JsMangaPage {
        val filterList = filters ?: getFilterList()
        val operation = "search(${Json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(query))}, $page)"
        return callAsync(operation, "search", JsonPrimitive(query), JsonPrimitive(page), filterList.toJsonArray()) {
            JsResultParser.mangaPage(it)
        }
    }

    /** `getDetail(url)`: details and chapters (newest first) of the manga whose `link` is [url]. */
    suspend fun getDetail(url: String): JsManga = callAsync("getDetail", "getDetail", JsonPrimitive(url)) { JsResultParser.mangaDetail(it) }

    /** `getPageList(url)` for the chapter whose `url` is [url]; empty and duplicate URLs removed. */
    suspend fun getPageList(url: String): List<JsPage> = callAsync("getPageList", "getPageList", JsonPrimitive(url)) { JsResultParser.pages(it) }

    /**
     * `getFilterList()`. Like Mangayomi, any failure (not implemented, an error, a result of the wrong shape) gives
     * `[]`, logged as a warning, so [search] still runs.
     */
    suspend fun getFilterList(): List<JsFilter> = filters(callSync("getFilterList", CallMode.SYNC))

    /** `getSourcePreferences()`; `[]` when not implemented or not an array. */
    suspend fun getSourcePreferences(): List<JsPreference> = preferences(callSync("getSourcePreferences", CallMode.SYNC))

    /** `getHeaders(url)` (always called with the base URL in Mangayomi); `{}` when not implemented or not an object. */
    suspend fun getHeaders(url: String = source.baseUrl): Map<String, String> = headers(callSync("getHeaders", CallMode.SYNC, JsonPrimitive(url)))

    /** The `supportsLatest` getter; `true` when not implemented or not a boolean. */
    suspend fun supportsLatest(): Boolean = supportsLatest(callSync("supportsLatest", CallMode.GETTER))

    /**
     * [supportsLatest], [getFilterList], [getSourcePreferences] and [getHeaders] (for `baseUrl`) read in one context,
     * with the same defaults and errors as the single calls.
     */
    suspend fun metadata(): JsSourceMetadata {
        val outcomes = host.callMetadata(source, code)
        // A failure to load (or a timeout) is reported under the entry's own name.
        (outcomes["metadata"] as? CallOutcome.Failed)?.let { throw it.error }
        fun outcome(member: String) = outcomes[member] ?: CallOutcome.Value(null)
        return JsSourceMetadata(
            supportsLatest = supportsLatest(outcome("supportsLatest")),
            filters = filters(outcome("getFilterList")),
            preferences = preferences(outcome("getSourcePreferences")),
            headers = headers(outcome("getHeaders")),
        )
    }

    private suspend fun <T> callAsync(operation: String, member: String, vararg args: JsonElement, decode: (JsonElement?) -> T): T =
        when (val outcome = host.call(source, code, operation, member, CallMode.ASYNC, args.toList())) {
            is CallOutcome.Value -> parse(operation, member) { decode(outcome.value) }
            is CallOutcome.Failed -> throw outcome.error
        }

    private suspend fun callSync(member: String, mode: CallMode, vararg args: JsonElement): CallOutcome =
        host.call(source, code, member, member, mode, args.toList())

    // Sync members (contract 2.4): Mangayomi's defaults when the base class says "not implemented" or the result has
    // the wrong type (its `_extensionCall`); other errors are reported, except by getFilterList.

    private fun filters(outcome: CallOutcome): List<JsFilter> = when (outcome) {
        is CallOutcome.Value -> try {
            parse("getFilterList", "getFilterList") { JsResultParser.filters(outcome.value) }
        } catch (e: JsSourceException) {
            ignoredFilters(e)
        }
        is CallOutcome.Failed -> ignoredFilters(outcome.error)
    }

    private fun ignoredFilters(error: JsSourceException): List<JsFilter> {
        log.log(HostLog.Level.WARN, source, "Ignoring getFilterList failure: ${error.message}")
        return emptyList()
    }

    private fun preferences(outcome: CallOutcome): List<JsPreference> = syncValue(outcome, emptyList()) { result ->
        if (result !is JsonArray) {
            wrongType("getSourcePreferences", result, emptyList())
        } else {
            parse("getSourcePreferences", "getSourcePreferences") { JsResultParser.preferences(result) }
        }
    }

    private fun headers(outcome: CallOutcome): Map<String, String> = syncValue(outcome, emptyMap()) { result ->
        if (result !is JsonObject) wrongType("getHeaders", result, emptyMap()) else parse("getHeaders", "getHeaders") { JsResultParser.headers(result) }
    }

    private fun supportsLatest(outcome: CallOutcome): Boolean = syncValue(outcome, true) { result ->
        result.jsonBooleanOrNull() ?: wrongType("supportsLatest", result, true)
    }

    private inline fun <T> syncValue(outcome: CallOutcome, default: T, decode: (JsonElement?) -> T): T = when (outcome) {
        is CallOutcome.Value -> decode(outcome.value)
        is CallOutcome.Failed -> if (outcome.notImplemented) default else throw outcome.error
    }

    private fun <T> wrongType(member: String, result: JsonElement?, default: T): T {
        val got = when (result) {
            null, is JsonNull -> "nothing"
            is JsonObject -> "an object"
            is JsonArray -> "an array"
            is JsonPrimitive -> if (result.isString) "a string" else result.toString()
        }
        log.log(HostLog.Level.WARN, source, "$member returned $got; using the default $default")
        return default
    }

    private inline fun <T> parse(operation: String, method: String, block: () -> T): T = try {
        block()
    } catch (e: MalformedResultFailure) {
        throw JsSourceException.MalformedResult(source.name, operation, method, e.field, e.problem)
    }
}
