package exh.search

import java.util.Locale

class SearchEngine {
    private val queryCache = mutableMapOf<String, List<QueryComponent>>()

    /**
     * Each query string will be split into a list of [QueryComponent], either [Text] or [Namespace].
     * Each `Text` can have multiple [Text.components].
     * A component can be a word, a wildcard, or a string of quoted words.
     */
    fun parseQuery(query: String) = queryCache.getOrPut(query) {
        val res = mutableListOf<QueryComponent>()

        var inQuotes = false
        val queuedRawText = StringBuilder()
        val queuedText = mutableListOf<TextComponent>()
        var namespace: Namespace? = null

        var nextIsExcluded = false
        var nextIsExact = false

        // Put a word into queued, wildcard is treated as a word
        fun flushText() {
            if (queuedRawText.isNotEmpty()) {
                queuedText += StringTextComponent(queuedRawText.toString())
                queuedRawText.setLength(0)
            }
        }

        // Create a new Text, put all words (queued) into it, clear queued
        fun flushToText() = Text().apply {
            components += queuedText
            queuedText.clear()
        }

        fun flushAll() {
            flushText()
            if (queuedText.isNotEmpty() || namespace != null) {
                val component = namespace?.apply {
                    tag = flushToText()
                    namespace = null
                } ?: flushToText()
                component.excluded = nextIsExcluded
                nextIsExcluded = false
                component.exact = nextIsExact
                nextIsExact = false
                res += component
            }
        }

        query.lowercase(Locale.getDefault()).forEach { char ->
            if (char == '"') {
                inQuotes = !inQuotes
            } else if (char == '-' && !inQuotes && (queuedRawText.isBlank() || queuedRawText.last() == ' ')) {
                nextIsExcluded = true
            } else if (char == '$' && !inQuotes) {
                nextIsExact = true
            } else if (char == ':') {
                flushText()
                var flushed = flushToText().rawTextOnly()
                // Map tag aliases
                flushed = when (flushed) {
                    "a" -> "artist"
                    "c", "char" -> "character"
                    "f" -> "female"
                    "g", "creator", "circle" -> "group"
                    "l", "lang" -> "language"
                    "m" -> "male"
                    "p", "series" -> "parody"
                    "r" -> "reclass"
                    else -> flushed
                }
                namespace = Namespace(flushed, null)
            } else if (arrayOf(' ', ',').contains(char) && !inQuotes) {
                flushAll()
            } else {
                queuedRawText.append(char)
            }
        }
        flushAll()

        res
    }

    companion object {
        fun escapeLike(string: String): String {
            return string.replace("\\", "\\\\")
                .replace("_", "\\_")
                .replace("%", "\\%")
        }
    }
}

fun wildcardToRegex(pattern: String): String {
    // Escape all regex special characters
    val escapedPattern = pattern
        .replace("\\", "\\\\")  // Escape `\` first to avoid double escaping
        .replace(".", "\\.")
        .replace("^", "\\^")
        .replace("$", "\\$")
        .replace("{", "\\{")
        .replace("}", "\\}")
        .replace("(", "\\(")
        .replace(")", "\\)")
        .replace("[", "\\[")
        .replace("]", "\\]")
        .replace("|", "\\|")
        .replace("+", "\\+")

    // Replace `*` with `.*` and `?` with `.` to handle wildcards
    return escapedPattern
        .replace("*", ".*")    // `*` matches any sequence of characters
        .replace("?", ".")     // `?` matches any single character
}

fun String.isMatch(pattern: String, ignoreCase: Boolean = true, enableWildcard: Boolean = true): Boolean {
    if (!enableWildcard) {
        return contains(pattern, ignoreCase)
    }
    // Convert the wildcard pattern to a regex pattern
    val regexPattern = if (ignoreCase) {
        wildcardToRegex(pattern).toRegex(RegexOption.IGNORE_CASE)
    } else {
        wildcardToRegex(pattern).toRegex()
    }

    // Use `containsMatchIn` to allow substring matching
    return regexPattern.containsMatchIn(this)
}
