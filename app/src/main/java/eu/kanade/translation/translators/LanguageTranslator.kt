package eu.kanade.translation.translators

import eu.kanade.translation.TextTranslations

interface LanguageTranslator {
    suspend fun translate(pages: HashMap<String, TextTranslations>)
}
