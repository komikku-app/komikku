package exh.md.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class MangaPlusResponse(
    // KMK -->
    @ProtoNumber(1) val success: SuccessResult? = null,
    @ProtoNumber(2) val error: ErrorResult? = null,
    // KMK <--
)

// KMK -->
@Serializable
data class ErrorResult(
    @ProtoNumber(2) val englishPopup: Popup? = null,
    @ProtoNumber(3) val spanishPopup: Popup? = null,
) {
    fun langPopup(lang: MangaPlusLanguage): Popup? = when (lang) {
        MangaPlusLanguage.SPANISH -> spanishPopup ?: englishPopup
        else -> englishPopup
    }
}

@Serializable
data class Popup(
    @ProtoNumber(2) val body: String = "",
)
// KMK <--

@Serializable
data class SuccessResult(
    // KMK -->
    @ProtoNumber(10) val mangaViewer: MangaViewer? = null,
    // KMK <--
)

@Serializable
data class MangaViewer(
    // KMK -->
    @ProtoNumber(1) val pages: List<MangaPlusPage> = emptyList(),
    @ProtoNumber(19) val viewToken: String? = null,
    // KMK <--
)

@Serializable
data class MangaPlusPage(
    // KMK -->
    @ProtoNumber(1) val mangaPage: MangaPage? = null,
    // KMK <--
)

@Serializable
data class MangaPage(
    // KMK -->
    @ProtoNumber(1) val imageUrl: String,
    @ProtoNumber(5) val encryptionKey: String? = null,
    // KMK <--
)

// KMK -->
enum class MangaPlusLanguage(val code: Int, val clang: String) {
    ENGLISH(0, "eng"),
    SPANISH(1, "esp"),
    FRENCH(2, "fra"),
    INDONESIAN(3, "ind"),
    PORTUGESE_BR(4, "ptb"),
    RUSSIAN(5, "rus"),
    THAI(6, "tha"),
    GERMAN(7, "deu"),
    VIETNAMESE(9, "vie"),
}

fun String.toMangaPlusLanguage(): MangaPlusLanguage = when (this) {
    "en" -> MangaPlusLanguage.ENGLISH
    "es" -> MangaPlusLanguage.SPANISH
    "fr" -> MangaPlusLanguage.FRENCH
    "id" -> MangaPlusLanguage.INDONESIAN
    "pt-BR" -> MangaPlusLanguage.PORTUGESE_BR
    "ru" -> MangaPlusLanguage.RUSSIAN
    "th" -> MangaPlusLanguage.THAI
    "de" -> MangaPlusLanguage.GERMAN
    "vi" -> MangaPlusLanguage.VIETNAMESE
    // Default to English
    else -> MangaPlusLanguage.ENGLISH
}
// KMK <--
