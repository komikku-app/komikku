package tachiyomi.domain.chapterTag.model

import java.io.Serializable

data class ChapterTag(
    val id: Long,
    val name: String,
) : Serializable
