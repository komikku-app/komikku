package eu.kanade.translation.translators

import mihon.domain.translation.TextTranslations

interface LanguageTranslator {
    suspend fun translate(pages: HashMap<String, TextTranslations>)
}
