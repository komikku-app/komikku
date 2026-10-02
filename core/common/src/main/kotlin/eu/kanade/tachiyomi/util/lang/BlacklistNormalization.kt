package eu.kanade.tachiyomi.util.lang

import java.text.Normalizer
import java.util.Locale

fun String.toBlacklistNormalizedTitle(): String {
    if (isBlank()) return ""

    val standardized = trim()
        .replace("’", "'")
        .replace("‘", "'")
        .replace("“", "\"")
        .replace("”", "\"")
        .replace("–", "-")
        .replace("—", "-")
        .replace("…", "...")

    val normalized = Normalizer.normalize(standardized, Normalizer.Form.NFKD)
        .lowercase(Locale.ROOT)

    return buildString(normalized.length) {
        normalized.forEach { char ->
            when (Character.getType(char)) {
                Character.NON_SPACING_MARK.toInt(),
                Character.COMBINING_SPACING_MARK.toInt(),
                Character.ENCLOSING_MARK.toInt(),
                -> Unit
                else -> if (char.isLetterOrDigit()) append(char)
            }
        }
    }
}
