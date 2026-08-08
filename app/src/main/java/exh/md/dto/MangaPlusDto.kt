package exh.md.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class MangaPlusResponse(
    @ProtoNumber(1) val success: SuccessResult? = null,
    @ProtoNumber(2) val error: ErrorResult? = null,
)

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

@Serializable
data class SuccessResult(
    @ProtoNumber(10) val mangaViewer: MangaViewer? = null,
)

@Serializable
data class MangaViewer(
    @ProtoNumber(1) val pages: List<MangaPlusPage> = emptyList(),
    @ProtoNumber(19) val viewToken: String? = null,
)

@Serializable
data class MangaPlusPage(
    @ProtoNumber(1) val mangaPage: MangaPage? = null,
)

@Serializable
data class MangaPage(
    @ProtoNumber(1) val imageUrl: String,
    @ProtoNumber(5) val encryptionKey: String? = null,
)

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
    else -> throw IllegalStateException("Unsupported lang: $this")
}
