package tachiyomi.data.chapterTag

import tachiyomi.domain.chapterTag.model.ChapterTag

object ChapterTagMapper {

    fun mapChapterTag(id: Long, name: String): ChapterTag = ChapterTag(
        id = id,
        name = name,
    )

    fun mapChapterTagWithChapterId(
        chapterId: Long,
        id: Long,
        name: String,
    ): Pair<Long, ChapterTag> = chapterId to ChapterTag(id = id, name = name)
}
