package tachiyomi.domain.manga.model

import exh.metadata.metadata.EHentaiSearchMetadata

data class FavoriteEntry(
    val id: Long? = null,

    val title: String,

    val gid: String,

    val token: String,

    val category: Int = -1,
) {
    fun getUrl() = EHentaiSearchMetadata.idAndTokenToUrl(gid, token)
}
