package exh.util

fun List<String>.trimAll() = map { it.trim() }
fun List<String>.dropBlank() = filter { it.isNotBlank() }
fun List<String>.dropEmpty() = filter { it.isNotEmpty() }

private val articleRegex by lazy { "^(an|a|the) ".toRegex(RegexOption.IGNORE_CASE) }

fun String.removeArticles(): String {
    return replace(articleRegex, "")
}

fun String.trimOrNull(): String? {
    val trimmed = trim()
    return if (trimmed.isBlank()) null else trimmed
}

fun String?.nullIfBlank(): String? = if (isNullOrBlank()) {
    null
} else {
    this
}
