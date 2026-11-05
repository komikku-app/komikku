package exh.md.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Protobuf models for the MangaPlus viewer.
 *
 * Field numbers match Keiyoushi `src/all/mangaplus/.../Dto.kt` on `main` as of 2026-11-04.
 * They are not the retired JSON models in [MangaPlusResponse].
 *
 * Viewer request used by that extension: `GET /api/manga_viewer_v3` with `chapter_id`, `split`,
 * `img_quality`, and `clang`. The image URL is [MangaPlusViewerImage.imageUrl]. When
 * [MangaPlusViewerImage.encryptionKey] is set, the image bytes are XOR-decoded with that key.
 * [MangaPlusViewer.viewToken] is sent as the `Plus-Vw-Token` header on image requests.
 */
@Serializable
data class MangaPlusViewerResponse(
    @ProtoNumber(1) val success: MangaPlusViewerSuccess? = null,
    @ProtoNumber(2) val error: MangaPlusViewerError? = null,
)

@Serializable
data class MangaPlusViewerError(
    @ProtoNumber(2) val englishPopup: MangaPlusViewerPopup? = null,
    @ProtoNumber(3) val spanishPopup: MangaPlusViewerPopup? = null,
)

@Serializable
data class MangaPlusViewerPopup(
    @ProtoNumber(1) val subject: String = "",
    @ProtoNumber(2) val body: String = "",
)

@Serializable
data class MangaPlusViewerSuccess(
    @ProtoNumber(10) val mangaViewer: MangaPlusViewer? = null,
)

@Serializable
data class MangaPlusViewer(
    @ProtoNumber(1) val pages: List<MangaPlusViewerPage> = emptyList(),
    @ProtoNumber(9) val titleId: Int? = null,
    @ProtoNumber(19) val viewToken: String? = null,
)

@Serializable
data class MangaPlusViewerPage(
    @ProtoNumber(1) val mangaPage: MangaPlusViewerImage? = null,
)

@Serializable
data class MangaPlusViewerImage(
    @ProtoNumber(1) val imageUrl: String = "",
    @ProtoNumber(5) val encryptionKey: String? = null,
)

data class MangaPlusDecodedPage(
    val imageUrl: String,
    val encryptionKey: String?,
)

data class MangaPlusDecodedViewer(
    val pages: List<MangaPlusDecodedPage>,
    val viewToken: String?,
    val errorMessage: String?,
)

object MangaPlusViewerPages {
    const val API_URL = "https://jumpg-webapi.tokyo-cdn.com/api"
    private const val NOT_FOUND_SUBJECT = "Not Found"

    /**
     * English `clang` matches the MangaDex external chapters this handler serves. The standalone
     * extension sends the source language; this in-app path has no language argument.
     */
    fun viewerUrl(chapterId: String, dataSaver: Boolean, language: String = "eng"): String {
        return "$API_URL/manga_viewer_v3".toHttpUrl().newBuilder()
            .addQueryParameter("chapter_id", chapterId)
            .addQueryParameter("split", "yes")
            .addQueryParameter("img_quality", if (dataSaver) "low" else "super_high")
            .addQueryParameter("clang", language)
            .build()
            .toString()
    }

    fun decode(protoBuf: ProtoBuf, bytes: ByteArray): MangaPlusDecodedViewer {
        val response = protoBuf.decodeFromByteArray(MangaPlusViewerResponse.serializer(), bytes)
        val viewer = response.success?.mangaViewer
        if (viewer == null) {
            return MangaPlusDecodedViewer(
                pages = emptyList(),
                viewToken = null,
                errorMessage = errorMessage(response.error?.englishPopup),
            )
        }
        val pages = viewer.pages.mapNotNull { it.mangaPage }.map { page ->
            MangaPlusDecodedPage(
                imageUrl = page.imageUrl,
                encryptionKey = page.encryptionKey?.takeIf { it.isNotEmpty() },
            )
        }
        return MangaPlusDecodedViewer(
            pages = pages,
            viewToken = viewer.viewToken?.takeIf { it.isNotEmpty() },
            errorMessage = null,
        )
    }

    private fun errorMessage(popup: MangaPlusViewerPopup?): String {
        if (popup == null) return "error getting images"
        if (popup.subject == NOT_FOUND_SUBJECT) {
            return popup.body.ifBlank { "Chapter expired" }
        }
        if (popup.body.isNotBlank()) return popup.body
        return "error getting images"
    }
}
