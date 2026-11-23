package app.komikku.sourcejs.mihon

import app.komikku.sourcejs.bridge.CommonHostCrypto

/**
 * Identity of a Mangayomi JavaScript source inside Komikku (PLAN.md 8.3, I-D4).
 *
 * Komikku source ids are derived from the index id (a Dart `String.hashCode` chosen by the index generator, contract
 * 1.4) under a fixed namespace, so they are the same on every device and do not depend on the source's name or
 * language. They follow the shape of Mihon's generated ids (first 64 bits of an MD5, sign bit cleared), but hash a
 * different text (`mangayomi-js/<id>` instead of `<name>/<lang>/<versionId>`), so a JavaScript source and a Mihon
 * extension never share an id unless MD5 collides on its first 63 bits.
 */
object JsSourceIds {

    /** Prefix of the hashed text; never change it (every stored manga refers to the derived ids). */
    const val NAMESPACE = "mangayomi-js/"

    /** The Komikku source id of the Mangayomi source whose index id is [mangayomiId]. */
    fun komikkuId(mangayomiId: Long): Long {
        val digest = CommonHostCrypto.md5("$NAMESPACE$mangayomiId".encodeToByteArray())
        var id = 0L
        for (index in 0 until 8) {
            id = (id shl 8) or (digest[index].toLong() and 0xff)
        }
        return id and Long.MAX_VALUE
    }

    /**
     * The name of the preference file of the source whose Komikku id is [komikkuId]: the same name
     * `ConfigurableSource.preferenceKey()` gives (`source_<id>`), so the app's source settings screen, backups and the
     * script's `SharedPreferences` use one store.
     */
    fun preferencesName(komikkuId: Long): String = "source_$komikkuId"

    /**
     * [lang] as a BCP 47-style tag in the casing Mihon extensions use (`pt-BR`, `zh-Hans`, `es-419`), so the language
     * lists and filters of the app group JavaScript and Mihon sources of one language together. Blank, `all`, `multi`
     * and `*` become `all`; `_` separators become `-`.
     */
    fun normalizeLang(lang: String): String {
        val tag = lang.trim().replace('_', '-')
        if (tag.isEmpty() || tag == "*" || tag.equals("all", ignoreCase = true) || tag.equals("multi", ignoreCase = true)) {
            return "all"
        }
        return tag.split('-')
            .filter { it.isNotEmpty() }
            .mapIndexed { index, part ->
                when {
                    index == 0 -> part.lowercase()
                    part.length == 2 && part.all { it.isLetter() } -> part.uppercase()
                    part.length == 4 && part.all { it.isLetter() } -> part.lowercase().replaceFirstChar { it.uppercaseChar() }
                    else -> part.lowercase()
                }
            }
            .joinToString("-")
    }
}
