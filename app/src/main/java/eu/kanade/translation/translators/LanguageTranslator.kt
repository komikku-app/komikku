package eu.kanade.translation.translators

import eu.kanade.translation.TextTranslation

interface LanguageTranslator {    suspend fun translate(pages:   HashMap<String, List<TextTranslation>>)

}
enum class LanguageTranslators(var label: String) {
    MLKIT("MlKit (On Device)"),
    GOOGLE("Google Translate"),
    GEMINI("Gemini AI [API KEY]"),
    OPENROUTER("OpenRouter [API KEY] [MODEL]")
}
