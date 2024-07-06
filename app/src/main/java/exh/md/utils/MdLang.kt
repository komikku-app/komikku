package exh.md.utils

@Suppress("unused")
enum class MdLang(val lang: String, val extLang: String = lang) {
    ENGLISH("en"),
    JAPANESE("ja"),
    POLISH("pl"),
    SERBIAN("sh"),
    DUTCH("nl"),
    ITALIAN("it"),
    RUSSIAN("ru"),
    GERMAN("de"),
    HUNGARIAN("hu"),
    FRENCH("fr"),
    FINNISH("fi"),
    VIETNAMESE("vi"),
    GREEK("el"),
    BULGARIAN("bg"),
    SPANISH_ES("es"),
    PORTUGUESE_BR("pt-br", "pt-BR"),
    PORTUGUESE("pt"),
    SWEDISH("sv"),
    ARABIC("ar"),
    DANISH("da"),
    CHINESE_SIMPLIFIED("zh", "zh-Hans"),
    BENGALI("bn"),
    ROMANIAN("ro"),
    CZECH("cs"),
    MONGOLIAN("mn"),
    TURKISH("tr"),
    INDONESIAN("id"),
    KOREAN("ko"),
    SPANISH_LATAM("es-la", "es-419"),
    PERSIAN("fa"),
    MALAY("ms"),
    THAI("th"),
    CATALAN("ca"),
    FILIPINO("tl", "fil"),
    CHINESE_TRAD("zh-hk", "zh-Hant"),
    UKRAINIAN("uk"),
    BURMESE("my"),
    LINTHUANIAN("lt"),
    HEBREW("he"),
    HINDI("hi"),
    NORWEGIAN("no"),
    NEPALI("ne"),
    LATIN("la"),
    TAMIL("ta"),
    KAZAKH("kk"),
    ;

    companion object {
        fun fromIsoCode(isoCode: String): MdLang? =
            entries.firstOrNull {
                it.lang == isoCode
            }

        fun fromExt(extLang: String): MdLang? =
            entries.firstOrNull {
                it.extLang == extLang
            }
    }
}
