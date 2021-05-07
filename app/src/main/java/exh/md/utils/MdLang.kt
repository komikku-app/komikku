package exh.md.utils

enum class MdLang(val lang: String, val prettyPrint: String, val extLang: String = lang) {
    ENGLISH("en", "English"),
    JAPANESE("jp", "Japanese", "ja"),
    POLISH("pl", "Polish"),
    SERBO_CROATIAN("rs", "Serbo-Croatian", "sh"),
    DUTCH("nl", "Dutch"),
    ITALIAN("it", "IT"),
    RUSSIAN("ru", "Russian"),
    GERMAN("de", "German"),
    HUNGARIAN("hu", "Hungarian"),
    FRENCH("fr", "French"),
    FINNISH("fi", "Finnish"),
    VIETNAMESE("vn", "Vietnamese", "vi"),
    GREEK("gr", "Greek", "el"),
    BULGARIAN("bg", "BULGARIN"),
    SPANISH_ES("es", "Spanish (Es)"),
    PORTUGUESE_BR("br", "Portuguese (Br)", "pt-BR"),
    PORTUGUESE("pt", "Portuguese (Pt)"),
    SWEDISH("se", "Swedish", "sv"),
    ARABIC("sa", "Arabic", "ar"),
    DANISH("dk", "Danish", "da"),
    CHINESE_SIMPLIFIED("cn", "Chinese (Simp)", "zh"),
    BENGALI("bd", "Bengali", "bn"),
    ROMANIAN("ro", "Romanian"),
    CZECH("cz", "Czech", "cs"),
    MONGOLIAN("mn", "Mongolian"),
    TURKISH("tr", "Turkish"),
    INDONESIAN("id", "Indonesian"),
    KOREAN("kr", "Korean", "ko"),
    SPANISH_LATAM("mx", "Spanish (LATAM)", "es-la"),
    PERSIAN("ir", "Persian", "fa"),
    MALAY("my", "Malay", "ms"),
    THAI("th", "Thai"),
    CATALAN("ct", "Catalan", "ca"),
    FILIPINO("ph", "Filipino", "fi"),
    CHINESE_TRAD("hk", "Chinese (Trad)", "zh-hk"),
    UKRAINIAN("ua", "Ukrainian", "uk"),
    BURMESE("mm", "Burmese", "my"),
    LINTHUANIAN("lt", "Lithuanian"),
    HEBREW("il", "Hebrew", "he"),
    HINDI("in", "Hindi", "hi"),
    NORWEGIAN("no", "Norwegian")
    ;

    companion object {
        fun fromIsoCode(isoCode: String): MdLang? =
            values().firstOrNull {
                it.lang == isoCode
            }

        fun fromExt(extLang: String): MdLang? =
            values().firstOrNull {
                it.extLang == extLang
            }
    }
}
