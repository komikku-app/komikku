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
        synchronized(lock) {
            val key = cookie.key()
            // An already expired cookie (e.g. `Max-Age=0`) deletes the stored one.
            if (cookie.expiresAt <= clock()) cookies.remove(key) else cookies[key] = cookie
        }
        changed(cookie.persistent)
        callback?.onReceiveValue(true)
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
            matching.forEach { cookies.remove(it) }
            matching.size
        }
        if (removed > 0) changed(persistent = true)
        return removed
    }

    override fun removeSessionCookies(callback: ValueCallback<Boolean>?) {
        val removed = synchronized(lock) { cookies.values.removeAll { !it.persistent } }
        callback?.onReceiveValue(removed)
    }

    override fun removeAllCookies(callback: ValueCallback<Boolean>?) {
        val removed = synchronized(lock) {
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
            cookies.values.filter { it.persistent }
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
                if (cookie.expiresAt > now) cookies[cookie.key()] = cookie
            }
        } catch (e: Exception) {
            DesktopLog.log(DesktopLog.ERROR, "CookieManager", "Ignoring unreadable ${file.path}", e)
        }
    }

    private fun write(target: File, snapshot: List<Cookie>) {
        val root = buildJsonObject {
            put(KEY_VERSION, 1)
            put(KEY_COOKIES, buildJsonArray { snapshot.forEach { add(it.toJson()) } })
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

    private fun Cookie.toJson() = buildJsonObject {
        put("name", name)
        put("value", value)
        put("domain", domain)
        put("path", path)
        put("expiresAt", expiresAt)
        put("secure", secure)
        put("httpOnly", httpOnly)
        put("hostOnly", hostOnly)
        sameSite?.let { put("sameSite", it) }
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
