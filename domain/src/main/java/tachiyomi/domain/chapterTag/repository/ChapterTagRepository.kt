package tachiyomi.domain.chapterTag.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.chapterTag.model.ChapterTag
import tachiyomi.domain.chapterTag.model.ChapterTagFilter

interface ChapterTagRepository {

    // Tag definitions
    suspend fun getAll(): List<ChapterTag>

    fun getAllAsFlow(): Flow<List<ChapterTag>>

    suspend fun insert(name: String): Long

    suspend fun rename(tagId: Long, name: String)

    suspend fun delete(tagId: Long)

    // Assignments
    suspend fun getTagIdsByChapterIds(chapterIds: List<Long>): Map<Long, List<Long>>

    suspend fun getTagsByMangaId(mangaId: Long): Map<Long, List<ChapterTag>>

    fun getTagsByMangaIdAsFlow(mangaId: Long): Flow<Map<Long, List<ChapterTag>>>

    suspend fun setChapterTags(chapterId: Long, tagIds: List<Long>)

    /** Adds [tagIds] to a chapter without dropping tags it already carries. */
    suspend fun addChapterTags(chapterId: Long, tagIds: List<Long>)

    fun getTagIdsPerMangaAsFlow(): Flow<Map<Long, Set<Long>>>

    // Per-manga filter selection
    suspend fun getFilter(mangaId: Long): ChapterTagFilter

    fun getFilterAsFlow(mangaId: Long): Flow<ChapterTagFilter>

    suspend fun setFilter(mangaId: Long, filter: ChapterTagFilter)
}
