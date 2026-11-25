package app.komikku.desktop.compat

import android.webkit.CookieManager
import android.webkit.ValueCallback
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Desktop [CookieManager]: an RFC 6265 cookie store keyed by (name, domain, path), using OkHttp's
 * `Set-Cookie` parsing and URL matching.
 *
 * Persistent cookies are written to [file] (JSON) shortly after each change, on [flush] and at JVM
 * shutdown; session cookies live in memory only. `null` [file] keeps everything in memory.
 */
class DesktopCookieManager(
    private val file: File?,
    private val clock: () -> Long = System::currentTimeMillis,
) : CookieManager() {

    private data class Key(val name: String, val domain: String, val path: String)

    private val lock = Any()
    private val cookies = LinkedHashMap<Key, Cookie>()

    /** Keys of cookies a user pasted ([setPastedCookies]), which a cookie the site sets later replaces. */
    private val pasted = HashSet<Key>()

    @Volatile
    private var accept = true

    private var pendingWrite: ScheduledFuture<*>? = null

    init {
        if (file != null) {
            load(file)
            Runtime.getRuntime().addShutdownHook(Thread({ flush() }, "android-compat-cookie-flush"))
        }
    }

    override fun setAcceptCookie(accept: Boolean) {
        this.accept = accept
    }

    override fun acceptCookie(): Boolean = accept

    override fun setCookie(url: String, value: String) {
        setCookie(url, value, null)
    }

    override fun setCookie(url: String, value: String, callback: ValueCallback<Boolean>?) {
        val httpUrl = url.toHttpUrlOrNull()
        val cookie = httpUrl?.takeIf { accept }?.let { Cookie.parse(it, value) }
        if (cookie == null) {
            callback?.onReceiveValue(false)
            return
        }
        val replacedPasted = synchronized(lock) {
            val key = cookie.key()
            // An already expired cookie (e.g. `Max-Age=0`) deletes the stored one.
            if (cookie.expiresAt <= clock()) cookies.remove(key) else cookies[key] = cookie
            pasted.remove(key)
            // A pasted cookie of the same name that would be sent with this one (e.g. on the parent domain) is stale
            // now: the site's own cookie replaces it instead of being sent after it.
            val stale = pasted.filter { it.name == cookie.name && cookies[it]?.matches(httpUrl) == true }
            stale.forEach {
                cookies.remove(it)
                pasted.remove(it)
            }
            stale.isNotEmpty()
        }
        changed(cookie.persistent || replacedPasted)
        callback?.onReceiveValue(true)
    }

    /**
     * Stores cookies a user pasted (desktop "Open in WebView" without a browser, P7) under their own domain and path,
     * marked so that a later [setCookie] of the same name for an address they are sent to removes them (the site's
     * `Set-Cookie` may use another domain, e.g. host-only, than the pasted one, which would then be a second cookie).
     */
    fun setPastedCookies(pastedCookies: List<Cookie>) {
        if (!accept || pastedCookies.isEmpty()) return
        synchronized(lock) {
            pastedCookies.forEach { cookie ->
                val key = cookie.key()
                if (cookie.expiresAt <= clock()) {
                    cookies.remove(key)
                    pasted.remove(key)
                } else {
                    cookies[key] = cookie
                    pasted += key
                }
            }
        }
        changed(persistent = true)
    }

    /** Whether the cookie [name] sent to [url] was pasted by the user (for tests and the cookie dialog). */
    fun isPasted(url: String, name: String): Boolean {
        val httpUrl = url.toHttpUrlOrNull() ?: return false
        return synchronized(lock) {
            pasted.any { it.name == name && cookies[it]?.matches(httpUrl) == true }
        }
    }

    override fun getCookie(url: String): String? {
        val httpUrl = url.toHttpUrlOrNull() ?: return null
        val matching = synchronized(lock) {
            removeExpired()
            cookies.values.filter { it.matches(httpUrl) }
        }
        if (matching.isEmpty()) return null
        // RFC 6265 5.4: longer paths first, then creation order (map order).
        return matching
            .sortedByDescending { it.path.length }
            .joinToString("; ") { "${it.name}=${it.value}" }
    }

    /**
     * The stored cookies sent to [url], with all their attributes (domain, path, expiry, …), unlike [getCookie]'s
     * `name=value` header. Empty for an invalid address.
     */
    fun cookiesFor(url: String): List<Cookie> {
        val httpUrl = url.toHttpUrlOrNull() ?: return emptyList()
        return synchronized(lock) {
            removeExpired()
            cookies.values.filter { it.matches(httpUrl) }
        }
    }

    /**
     * Removes the stored cookies sent to [url] whose name is in [names] (all of them when `null`), whatever their domain
     * and path; returns how many were removed. A `Max-Age=0` [setCookie] only removes the cookie with the address's own
     * host and default path.
     */
    fun removeFor(url: String, names: Collection<String>? = null): Int {
        val httpUrl = url.toHttpUrlOrNull() ?: return 0
        val removed = synchronized(lock) {
            removeExpired()
            val matching = cookies.filterValues { it.matches(httpUrl) && (names == null || it.name in names) }.keys
            matching.forEach {
                cookies.remove(it)
                pasted.remove(it)
            }
            matching.size
        }
        if (removed > 0) changed(persistent = true)
        return removed
    }

    /**
     * Removes every stored cookie the host of [url] gets, whatever its path, `Secure` flag or name (P23: a site's "Clear
     * cookies"): host-only cookies of that host and domain cookies of the host or one of its parent domains (e.g.
     * `Domain=example.com` for `www.example.com`). Cookies of other hosts stay, the host's subdomains and sibling hosts
     * included. Returns how many were removed; 0 for an invalid address. [removeFor] only takes the cookies sent to the
     * address itself, so a cookie with its own path (e.g. `Path=/manga`) survives it.
     *
     * [includeSubdomains] (P23 review fix, the extension info's "Clear cookies"): also the cookies of the host's
     * subdomains (host-only or domain cookies of e.g. `cdn.www.example.com` for `www.example.com`); sibling hosts and
     * parent-domain hosts' own cookies still stay.
     *
     * A host that is itself a public suffix or shared hosting domain (`github.io`, `blogspot.com`; OkHttp's public
     * suffix list, no registrable domain) has unrelated sites as its "subdomains": only its own cookies go then
     * (P23 fixes review).
     */
    fun removeForHost(url: String, includeSubdomains: Boolean = false): Int {
        val httpUrl = url.toHttpUrlOrNull() ?: return 0
        val host = httpUrl.host
        val subdomains = includeSubdomains && httpUrl.topPrivateDomain() != null
        val removed = synchronized(lock) {
            removeExpired()
            val matching = cookies.filterValues {
                it.appliesToHost(host) || (subdomains && it.domain.endsWith(".$host"))
            }.keys
            matching.forEach {
                cookies.remove(it)
                pasted.remove(it)
            }
            matching.size
        }
        if (removed > 0) changed(persistent = true)
        return removed
    }

    /** RFC 6265 5.1.3 domain matching (the address's path and scheme left aside). */
    private fun Cookie.appliesToHost(host: String): Boolean =
        host == domain || (!hostOnly && host.endsWith(".$domain"))

    override fun removeSessionCookies(callback: ValueCallback<Boolean>?) {
        val removed = synchronized(lock) {
            cookies.values.removeAll { !it.persistent }.also { pasted.retainAll(cookies.keys) }
        }
        callback?.onReceiveValue(removed)
    }

    override fun removeAllCookies(callback: ValueCallback<Boolean>?) {
        val removed = synchronized(lock) {
            pasted.clear()
            cookies.isNotEmpty().also { cookies.clear() }
        }
        changed(persistent = true)
        callback?.onReceiveValue(removed)
    }

    override fun hasCookies(): Boolean = synchronized(lock) {
        removeExpired()
        cookies.isNotEmpty()
    }

    /** Writes the persistent cookies to disk now. */
    override fun flush() {
        val target = file ?: return
        val snapshot = synchronized(lock) {
            pendingWrite?.cancel(false)
            pendingWrite = null
            removeExpired()
            pasted.retainAll(cookies.keys)
            cookies.values.filter { it.persistent }.map { it to (it.key() in pasted) }
        }
        try {
            write(target, snapshot)
        } catch (e: Exception) {
            DesktopLog.log(DesktopLog.ERROR, "CookieManager", "Could not write ${target.path}", e)
        }
    }

    private fun changed(persistent: Boolean) {
        if (file == null || !persistent) return
        synchronized(lock) {
            if (pendingWrite?.isDone == false) return
            pendingWrite = writer.schedule({ flush() }, WRITE_DELAY_MS, TimeUnit.MILLISECONDS)
        }
    }

    private fun removeExpired() {
        val now = clock()
        cookies.values.removeAll { it.expiresAt <= now }
    }

    private fun Cookie.key() = Key(name, domain, path)

    private fun load(file: File) {
        if (!file.isFile) return
        try {
            val now = clock()
            json.parseToJsonElement(file.readText()).jsonObject[KEY_COOKIES]?.jsonArray?.forEach { element ->
                val cookie = element.jsonObject.toCookie() ?: return@forEach
                if (cookie.expiresAt > now) {
                    cookies[cookie.key()] = cookie
                    if (element.jsonObject["pasted"]?.jsonPrimitive?.booleanOrNull == true) pasted += cookie.key()
                }
            }
        } catch (e: Exception) {
            DesktopLog.log(DesktopLog.ERROR, "CookieManager", "Ignoring unreadable ${file.path}", e)
        }
    }

    private fun write(target: File, snapshot: List<Pair<Cookie, Boolean>>) {
        val root = buildJsonObject {
            put(KEY_VERSION, 1)
            put(KEY_COOKIES, buildJsonArray { snapshot.forEach { (cookie, isPasted) -> add(cookie.toJson(isPasted)) } })
        }
        target.absoluteFile.parentFile?.mkdirs()
        val tmp = File(target.absoluteFile.parentFile, "${target.name}.tmp")
        FileOutputStream(tmp).use { out ->
            out.write(json.encodeToString(JsonObject.serializer(), root).toByteArray(Charsets.UTF_8))
            out.fd.sync()
        }
        try {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun Cookie.toJson(isPasted: Boolean) = buildJsonObject {
        put("name", name)
        put("value", value)
        put("domain", domain)
        put("path", path)
        put("expiresAt", expiresAt)
        put("secure", secure)
        put("httpOnly", httpOnly)
        put("hostOnly", hostOnly)
        sameSite?.let { put("sameSite", it) }
        if (isPasted) put("pasted", true)
    }

    private fun JsonObject.toCookie(): Cookie? = try {
        Cookie.Builder()
            .name(getValue("name").jsonPrimitive.content)
            .value(getValue("value").jsonPrimitive.content)
            .apply {
                val domain = getValue("domain").jsonPrimitive.content
                if (get("hostOnly")?.jsonPrimitive?.booleanOrNull == true) hostOnlyDomain(domain) else domain(domain)
            }
            .path(getValue("path").jsonPrimitive.content)
            .expiresAt(getValue("expiresAt").jsonPrimitive.long)
            .apply {
                if (get("secure")?.jsonPrimitive?.boolean == true) secure()
                if (get("httpOnly")?.jsonPrimitive?.boolean == true) httpOnly()
                get("sameSite")?.jsonPrimitive?.content?.let(::sameSite)
            }
            .build()
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val KEY_VERSION = "version"
        const val KEY_COOKIES = "cookies"
        const val WRITE_DELAY_MS = 1_000L

        val json = Json { prettyPrint = true }

        val writer = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "android-compat-cookie-writer").apply { isDaemon = true }
        }
    }
}
