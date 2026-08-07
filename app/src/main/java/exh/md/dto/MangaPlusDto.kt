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
    fun langPopup(langCode: Int): Popup? = when (langCode) {
        LANGUAGE_SPANISH -> spanishPopup ?: englishPopup
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

const val LANGUAGE_ENGLISH = 0
const val LANGUAGE_SPANISH = 1
const val LANGUAGE_FRENCH = 2
const val LANGUAGE_INDONESIAN = 3
const val LANGUAGE_PORTUGUESE_BR = 4
const val LANGUAGE_RUSSIAN = 5
const val LANGUAGE_THAI = 6
const val LANGUAGE_GERMAN = 7
const val LANGUAGE_VIETNAMESE = 9
