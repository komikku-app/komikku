package exh.util

fun Collection<String>.trimAll() = map { it.trim() }
fun Collection<String>.dropBlank() = filter { it.isNotBlank() }
fun Collection<String>.dropEmpty() = filter { it.isNotEmpty() }

private val articleRegex by lazy { "^(an|a|the) ".toRegex(RegexOption.IGNORE_CASE) }

fun String.removeArticles(): String {
    return replace(articleRegex, "")
}

fun String.trimOrNull(): String? {
    val trimmed = trim()
    return trimmed.ifBlank { null }
}

fun String?.nullIfBlank(): String? = if (isNullOrBlank()) {
    null
} else {
    this
}
