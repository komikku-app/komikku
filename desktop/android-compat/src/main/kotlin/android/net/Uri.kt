package android.net

import java.io.ByteArrayOutputStream
import java.io.File

/**
 * android-compat: `android.net.Uri`, written from its documented behaviour.
 *
 * Like Android, parsing is lenient (no validation, [toString] returns the parsed string as is),
 * the URI is either hierarchical (`scheme://authority/path?query#fragment`, or relative) or opaque
 * (`scheme:opaque-part#fragment`), getters without `Encoded` return percent-decoded values, and
 * `getQueryParameter` decodes `+` as a space.
 */
abstract class Uri internal constructor() : Comparable<Uri> {

    abstract val isHierarchical: Boolean

    val isOpaque: Boolean get() = !isHierarchical

    val isRelative: Boolean get() = scheme == null

    val isAbsolute: Boolean get() = !isRelative

    abstract val scheme: String?

    abstract val encodedSchemeSpecificPart: String

    val schemeSpecificPart: String get() = decode(encodedSchemeSpecificPart)

    abstract val encodedAuthority: String?

    val authority: String? get() = encodedAuthority?.let { decode(it) }

    val encodedUserInfo: String?
        get() = encodedAuthority?.let { authority ->
            authority.lastIndexOf('@').takeIf { it >= 0 }?.let { authority.substring(0, it) }
        }

    val userInfo: String? get() = encodedUserInfo?.let { decode(it) }

    val host: String?
        get() {
            val authority = encodedAuthority ?: return null
            val hostStart = authority.lastIndexOf('@') + 1
            val portSeparator = findPortSeparator(authority)
            val encodedHost = if (portSeparator == NOT_FOUND) {
                authority.substring(hostStart)
            } else {
                authority.substring(hostStart, portSeparator)
            }
            return decode(encodedHost)
        }

    val port: Int
        get() {
            val authority = encodedAuthority ?: return -1
            val portSeparator = findPortSeparator(authority)
            if (portSeparator == NOT_FOUND) return -1
            return decode(authority.substring(portSeparator + 1)).toIntOrNull() ?: -1
        }

    abstract val encodedPath: String?

    val path: String? get() = encodedPath?.let { decode(it) }

    abstract val encodedQuery: String?

    val query: String? get() = encodedQuery?.let { decode(it) }

    abstract val encodedFragment: String?

    val fragment: String? get() = encodedFragment?.let { decode(it) }

    val pathSegments: List<String>
        get() = encodedPath
            ?.split('/')
            ?.filter { it.isNotEmpty() }
            ?.map { decode(it) }
            .orEmpty()

    val lastPathSegment: String? get() = pathSegments.lastOrNull()

    /** Decoded names of the query parameters, in order of first appearance. */
    val queryParameterNames: Set<String>
        get() {
            if (isOpaque) throw UnsupportedOperationException(NOT_HIERARCHICAL)
            return queryPairs().mapTo(LinkedHashSet()) { (name, _) -> decode(name) }
        }

    /** All decoded values for [key] (`+` is kept as is), or an empty list. */
    fun getQueryParameters(key: String): List<String> {
        if (isOpaque) throw UnsupportedOperationException(NOT_HIERARCHICAL)
        val encodedKey = encode(key, null)
        return queryPairs().filter { (name, _) -> name == encodedKey }.map { (_, value) -> decode(value) }
    }

    /** First decoded value for [key] (`+` decodes to a space), or `null`. */
    fun getQueryParameter(key: String): String? {
        if (isOpaque) throw UnsupportedOperationException(NOT_HIERARCHICAL)
        val encodedKey = encode(key, null)
        return queryPairs().firstOrNull { (name, _) -> name == encodedKey }?.let { (_, value) -> decodeComponent(value, convertPlus = true) }
    }

    fun getBooleanQueryParameter(key: String, defaultValue: Boolean): Boolean {
        val value = getQueryParameter(key)?.lowercase() ?: return defaultValue
        return value != "false" && value != "0"
    }

    /** Same URI with the scheme lowercased. */
    fun normalizeScheme(): Uri {
        val scheme = scheme ?: return this
        val lower = scheme.lowercase()
        return if (scheme == lower) this else buildUpon().scheme(lower).build()
    }

    abstract fun buildUpon(): Builder

    override fun equals(other: Any?): Boolean = other is Uri && toString() == other.toString()

    override fun hashCode(): Int = toString().hashCode()

    override fun compareTo(other: Uri): Int = toString().compareTo(other.toString())

    private fun queryPairs(): List<Pair<String, String>> {
        val query = encodedQuery ?: return emptyList()
        if (query.isEmpty()) return emptyList()
        return query.split('&').map { pair ->
            val separator = pair.indexOf('=')
            if (separator == NOT_FOUND) pair to "" else pair.substring(0, separator) to pair.substring(separator + 1)
        }
    }

    /** Builder for hierarchical or (with [opaquePart]) opaque URIs. Decoded setters encode. */
    class Builder {
        private var scheme: String? = null
        private var opaquePart: String? = null
        private var authority: String? = null
        private var path: String? = null
        private var query: String? = null
        private var fragment: String? = null

        fun scheme(scheme: String?) = apply { this.scheme = scheme }

        fun opaquePart(opaquePart: String?) = apply { this.opaquePart = opaquePart?.let { encode(it, null) } }

        fun encodedOpaquePart(opaquePart: String?) = apply { this.opaquePart = opaquePart }

        fun authority(authority: String?) = apply {
            opaquePart = null
            this.authority = authority?.let { encode(it, null) }
        }

        fun encodedAuthority(authority: String?) = apply {
            opaquePart = null
            this.authority = authority
        }

        fun path(path: String?) = apply {
            opaquePart = null
            this.path = path?.let { encode(it, "/") }
        }

        fun encodedPath(path: String?) = apply {
            opaquePart = null
            this.path = path
        }

        fun appendPath(newSegment: String) = appendEncodedPath(encode(newSegment, null))

        fun appendEncodedPath(newSegment: String) = apply {
            opaquePart = null
            val old = path.orEmpty()
            path = when {
                old.isEmpty() -> "/$newSegment"
                old.endsWith('/') -> old + newSegment
                else -> "$old/$newSegment"
            }
        }

        fun query(query: String?) = apply {
            opaquePart = null
            this.query = query?.let { encode(it, null) }
        }

        fun encodedQuery(query: String?) = apply {
            opaquePart = null
            this.query = query
        }

        fun appendQueryParameter(key: String, value: String?) = apply {
            opaquePart = null
            val pair = encode(key, null) + "=" + (value?.let { encode(it, null) } ?: "")
            val old = query
            query = if (old.isNullOrEmpty()) pair else "$old&$pair"
        }

        fun clearQuery() = apply { query = null }

        fun fragment(fragment: String?) = apply { this.fragment = fragment?.let { encode(it, null) } }

        fun encodedFragment(fragment: String?) = apply { this.fragment = fragment }

        fun build(): Uri {
            val opaque = opaquePart
            if (opaque != null) {
                if (scheme == null) throw UnsupportedOperationException("An opaque URI must have a scheme.")
                return OpaqueUri(scheme, opaque, fragment)
            }
            var path = path.orEmpty()
            // With a scheme or an authority the path must be absolute.
            if ((scheme != null || authority != null) && path.isNotEmpty() && !path.startsWith('/')) path = "/$path"
            return HierarchicalUri(scheme, authority, path, query, fragment)
        }

        override fun toString(): String = build().toString()
    }

    companion object {
        private const val NOT_FOUND = -1
        private const val NOT_HIERARCHICAL = "This isn't a hierarchical URI."
        private const val HEX = "0123456789ABCDEF"

        @JvmField
        val EMPTY: Uri = HierarchicalUri(null, null, "", null, null)

        /** Parses [uriString] without validating it. */
        @JvmStatic
        fun parse(uriString: String): Uri = StringUri.parse(uriString)

        /** `file://` URI of the absolute path of [file]. */
        @JvmStatic
        fun fromFile(file: File): Uri = HierarchicalUri("file", "", encode(file.absolutePath, "/"), null, null)

        /** Opaque URI from decoded parts. */
        @JvmStatic
        fun fromParts(scheme: String, ssp: String, fragment: String?): Uri =
            OpaqueUri(scheme, encode(ssp, null), fragment?.let { encode(it, null) })

        @JvmStatic
        fun withAppendedPath(baseUri: Uri, pathSegment: String): Uri =
            baseUri.buildUpon().appendEncodedPath(pathSegment).build()

        @JvmStatic
        fun encode(s: String): String = encode(s, null)

        /**
         * Percent-encodes [s] as UTF-8, leaving letters, digits, `_-!.~'()*` and the characters in
         * [allow] intact.
         */
        @JvmStatic
        fun encode(s: String, allow: String?): String {
            if (s.all { it.isAllowed(allow) }) return s
            val out = StringBuilder(s.length + 16)
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c.isAllowed(allow)) {
                    out.append(c)
                    i++
                    continue
                }
                var end = i + 1
                while (end < s.length && !s[end].isAllowed(allow)) end++
                s.substring(i, end).toByteArray(Charsets.UTF_8).forEach { byte ->
                    val b = byte.toInt() and 0xff
                    out.append('%').append(HEX[b shr 4]).append(HEX[b and 0x0f])
                }
                i = end
            }
            return out.toString()
        }

        /** Decodes `%xx` escapes as UTF-8; `+` is kept. Invalid escapes become U+FFFD. */
        @JvmStatic
        fun decode(s: String): String = decodeComponent(s, convertPlus = false)

        private fun Char.isAllowed(allow: String?): Boolean =
            this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9' || this in "_-!.~'()*" || (allow != null && this in allow)

        internal fun decodeComponent(s: String, convertPlus: Boolean): String {
            if (s.indexOf('%') < 0 && !(convertPlus && s.indexOf('+') >= 0)) return s
            val out = StringBuilder(s.length)
            val bytes = ByteArrayOutputStream()
            fun flushBytes() {
                if (bytes.size() > 0) {
                    out.append(bytes.toByteArray().toString(Charsets.UTF_8))
                    bytes.reset()
                }
            }
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '%') {
                    val hi = s.getOrNull(i + 1)?.digitToIntOrNull(16)
                    val lo = s.getOrNull(i + 2)?.digitToIntOrNull(16)
                    if (hi != null && lo != null) {
                        bytes.write(hi shl 4 or lo)
                        i += 3
                    } else {
                        flushBytes()
                        out.append('�')
                        i = minOf(i + 3, s.length)
                    }
                    continue
                }
                flushBytes()
                out.append(if (convertPlus && c == '+') ' ' else c)
                i++
            }
            flushBytes()
            return out.toString()
        }

        /** Index of the ':' before a port, searching backwards over digits only. */
        private fun findPortSeparator(authority: String): Int {
            for (i in authority.indices.reversed()) {
                val c = authority[i]
                if (c == ':') return i
                if (c !in '0'..'9') return NOT_FOUND
            }
            return NOT_FOUND
        }
    }
}

/** Hierarchical URI from encoded parts. */
private class HierarchicalUri(
    override val scheme: String?,
    override val encodedAuthority: String?,
    override val encodedPath: String,
    override val encodedQuery: String?,
    override val encodedFragment: String?,
) : Uri() {

    override val isHierarchical: Boolean get() = true

    override val encodedSchemeSpecificPart: String
        get() = buildString { appendSsp() }

    private val string by lazy {
        buildString {
            if (scheme != null) append(scheme).append(':')
            appendSsp()
            if (!encodedFragment.isNullOrEmpty()) append('#').append(encodedFragment)
        }
    }

    private fun StringBuilder.appendSsp() {
        if (encodedAuthority != null) append("//").append(encodedAuthority)
        append(encodedPath)
        if (!encodedQuery.isNullOrEmpty()) append('?').append(encodedQuery)
    }

    override fun buildUpon(): Builder = Builder()
        .scheme(scheme)
        .encodedAuthority(encodedAuthority)
        .encodedPath(encodedPath)
        .encodedQuery(encodedQuery)
        .encodedFragment(encodedFragment)

    override fun toString(): String = string
}

/** Opaque URI (`scheme:ssp#fragment`) from encoded parts. */
private class OpaqueUri(
    override val scheme: String?,
    override val encodedSchemeSpecificPart: String,
    override val encodedFragment: String?,
) : Uri() {

    override val isHierarchical: Boolean get() = false
    override val encodedAuthority: String? get() = null
    override val encodedPath: String? get() = null
    override val encodedQuery: String? get() = null

    private val string by lazy {
        buildString {
            append(scheme).append(':').append(encodedSchemeSpecificPart)
            if (!encodedFragment.isNullOrEmpty()) append('#').append(encodedFragment)
        }
    }

    override fun buildUpon(): Builder = Builder()
        .scheme(scheme)
        .encodedOpaquePart(encodedSchemeSpecificPart)
        .encodedFragment(encodedFragment)

    override fun toString(): String = string
}

/** URI parsed from a string; components are sliced from the original, which [toString] returns. */
private class StringUri private constructor(
    private val uriString: String,
    private val schemeSeparator: Int,
) : Uri() {

    private val fragmentSeparator: Int =
        uriString.indexOf('#', schemeSeparator.coerceAtLeast(0))

    private val sspEnd: Int get() = if (fragmentSeparator == -1) uriString.length else fragmentSeparator

    override val scheme: String? =
        if (schemeSeparator == -1) null else uriString.substring(0, schemeSeparator)

    override val isHierarchical: Boolean
        get() = when {
            schemeSeparator == -1 -> true
            uriString.length == schemeSeparator + 1 -> false
            else -> uriString[schemeSeparator + 1] == '/'
        }

    override val encodedSchemeSpecificPart: String
        get() = uriString.substring(schemeSeparator + 1, sspEnd)

    override val encodedFragment: String? =
        if (fragmentSeparator == -1) null else uriString.substring(fragmentSeparator + 1)

    // Start of "//authority", or -1.
    private val authorityStart: Int
        get() = if (isHierarchical &&
            uriString.length > schemeSeparator + 2 &&
            uriString[schemeSeparator + 1] == '/' &&
            uriString[schemeSeparator + 2] == '/'
        ) {
            schemeSeparator + 3
        } else {
            -1
        }

    private val authorityEnd: Int
        get() {
            val start = authorityStart
            var i = start
            while (i < uriString.length) {
                when (uriString[i]) {
                    '/', '\\', '?', '#' -> return i
                }
                i++
            }
            return uriString.length
        }

    override val encodedAuthority: String?
        get() = authorityStart.takeIf { it != -1 }?.let { uriString.substring(it, authorityEnd) }

    override val encodedPath: String?
        get() {
            if (!isHierarchical) return null
            val start = if (authorityStart != -1) authorityEnd else schemeSeparator + 1
            var end = start
            while (end < uriString.length && uriString[end] != '?' && uriString[end] != '#') end++
            return uriString.substring(start, end)
        }

    override val encodedQuery: String?
        get() {
            if (!isHierarchical) return null
            val querySeparator = uriString.indexOf('?', schemeSeparator.coerceAtLeast(0))
            if (querySeparator == -1) return null
            if (fragmentSeparator == -1) return uriString.substring(querySeparator + 1)
            if (fragmentSeparator < querySeparator) return null
            return uriString.substring(querySeparator + 1, fragmentSeparator)
        }

    override fun buildUpon(): Builder = if (isHierarchical) {
        Builder()
            .scheme(scheme)
            .encodedAuthority(encodedAuthority)
            .encodedPath(encodedPath)
            .encodedQuery(encodedQuery)
            .encodedFragment(encodedFragment)
    } else {
        Builder()
            .scheme(scheme)
            .encodedOpaquePart(encodedSchemeSpecificPart)
            .encodedFragment(encodedFragment)
    }

    override fun toString(): String = uriString

    companion object {
        fun parse(uriString: String): Uri {
            // The scheme ends at the first ':' that comes before any '/', '?' or '#'.
            var separator = -1
            for (i in uriString.indices) {
                when (uriString[i]) {
                    ':' -> {
                        separator = i
                        break
                    }
                    '/', '?', '#' -> break
                }
            }
            return StringUri(uriString, separator)
        }
    }
}
