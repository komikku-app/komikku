package eu.kanade.translation.translators

import eu.kanade.translation.TextTranslation

interface LanguageTranslator {    suspend fun translate(pages:   HashMap<String, List<TextTranslation>>)

}
enum class LanguageTranslators(var label: String) {
    MLKIT("MlKit (Offline Version)"),
    GOOGLE("Google Translate"),
    GEMINI("Gemini AI (Key Needed)"),
    CHATGPT("ChatGPT (Key Needed) (Not Stable)")
}
