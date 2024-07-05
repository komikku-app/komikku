package eu.kanade.translation.translators

import com.google.mlkit.nl.translate.TranslateLanguage


enum class ScanLanguage(var code: String) {
    Chinese(TranslateLanguage.CHINESE),
    Japanese(TranslateLanguage.JAPANESE),
    Korean(TranslateLanguage.KOREAN),
    Latin(TranslateLanguage.ENGLISH)
}
