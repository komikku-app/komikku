package exh.md.utils

@Suppress("unused")
enum class MdLang(val lang: String, val prettyPrint: String, val extLang: String = lang) {
    ENGLISH("en", "English"),
    JAPANESE("ja", "Japanese"),
    POLISH("pl", "Polish"),
    SERBO_CROATIAN("rs", "Serbo-Croatian", "sh"),
    DUTCH("nl", "Dutch"),
    ITALIAN("it", "IT"),
    RUSSIAN("ru", "Russian"),
    GERMAN("de", "German"),
    HUNGARIAN("hu", "Hungarian"),
    FRENCH("fr", "French"),
    FINNISH("fi", "Finnish"),
    VIETNAMESE("vi", "Vietnamese"),
    GREEK("el", "Greek"),
    BULGARIAN("bg", "BULGARIN"),
    SPANISH_ES("es", "Spanish (Es)"),
    PORTUGUESE_BR("pt-br", "Portuguese (Br)", "pt-BR"),
    PORTUGUESE("pt", "Portuguese (Pt)"),
    SWEDISH("sv", "Swedish"),
    ARABIC("ar", "Arabic"),
    DANISH("da", "Danish"),
    CHINESE_SIMPLIFIED("zh", "Chinese (Simp)", "zh-Hans"),
    BENGALI("bn", "Bengali"),
    ROMANIAN("ro", "Romanian"),
    CZECH("cs", "Czech"),
    MONGOLIAN("mn", "Mongolian"),
    TURKISH("tr", "Turkish"),
    INDONESIAN("id", "Indonesian"),
    KOREAN("kr", "Korean", "ko"),
    SPANISH_LATAM("es-la", "Spanish (LATAM)", "es-419"),
    PERSIAN("fa", "Persian"),
    MALAY("ms", "Malay"),
    THAI("th", "Thai"),
    CATALAN("ca", "Catalan"),
    FILIPINO("tl", "Filipino", "fil"),
    CHINESE_TRAD("zh-hk", "Chinese (Trad)", "zh-Hant"),
    UKRAINIAN("uk", "Ukrainian"),
    BURMESE("my", "Burmese"),
    LINTHUANIAN("lt", "Lithuanian"),
    HEBREW("he", "Hebrew"),
    HINDI("hi", "Hindi"),
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
