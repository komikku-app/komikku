package tachiyomi.core.common.util

object QuerySanitizer {

    fun String.sanitize(removePrefix: String = ""): String {
        return trim()
            .removePrefix(removePrefix)
            .trim(*CHARACTER_TRIM_CHARS)
            .replaceSpecialChar()
    }

    // Replaces special characters with their standard equivalents.
    private fun String.replaceSpecialChar(): String {
        return replace("’", "'")
            .replace("‘", "'")
            .replace("“", "\"")
            .replace("”", "\"")
            .replace("–", "-")
            .replace("—", "-")
            .replace("…", "...")
    }

    private val CHARACTER_TRIM_CHARS = arrayOf(
        // Whitespace
        ' ',
        '\u0009',
        '\u000A',
        '\u000B',
        '\u000C',
        '\u000D',
        '\u0020',
        '\u0085',
        '\u00A0',
        '\u1680',
        '\u2000',
        '\u2001',
        '\u2002',
        '\u2003',
        '\u2004',
        '\u2005',
        '\u2006',
        '\u2007',
        '\u2008',
        '\u2009',
        '\u200A',
        '\u2028',
        '\u2029',
        '\u202F',
        '\u205F',
        '\u3000',

        // Separators
        '-',
        '_',
        ',',
        ':',
    ).toCharArray()
}
