package exh.util

fun List<String>.trimAll() = map { it.trim() }
fun List<String>.dropBlank() = filter { it.isNotBlank() }
fun List<String>.dropEmpty() = filter { it.isNotEmpty() }

fun String.removeArticles(): String {
    return this.replace(Regex("^(an|a|the) ", RegexOption.IGNORE_CASE), "")
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
