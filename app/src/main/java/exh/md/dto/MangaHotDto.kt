package exh.md.dto

import kotlinx.serialization.Serializable

@Serializable
data class MangaHotPageList(val content: MangaHotContent)

@Serializable
data class MangaHotContent(val contentUrls: List<String>)
