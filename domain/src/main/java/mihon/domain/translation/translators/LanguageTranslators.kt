package mihon.domain.translation.translators

enum class LanguageTranslators(var label: String) {
    MLKIT("MlKit (On Device)"),
    GOOGLE("Google Translate"),
    GEMINI("Gemini AI [API KEY]"),
    OPENROUTER("OpenRouter [API KEY] [MODEL]"),
}
