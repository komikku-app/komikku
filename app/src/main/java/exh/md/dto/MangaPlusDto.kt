package exh.md.dto

import kotlinx.serialization.Serializable

@Serializable
data class MangaPlusResponse(
    val success: SuccessResult? = null,
)

@Serializable
data class SuccessResult(
    val mangaViewer: MangaViewer? = null,
)

@Serializable
data class MangaViewer(val pages: List<MangaPlusPage> = emptyList())

@Serializable
data class MangaPlusPage(val mangaPage: MangaPage? = null)

@Serializable
data class MangaPage(
    val imageUrl: String,
    val width: Int,
    val height: Int,
    val encryptionKey: String? = null,
)
