package tachiyomi.domain.chapterTag.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.chapterTag.model.ChapterTag

interface ChapterTagRepository {

    // Tag definitions
    suspend fun getAll(): List<ChapterTag>

    fun getAllAsFlow(): Flow<List<ChapterTag>>

    suspend fun insert(name: String): Long

    suspend fun rename(tagId: Long, name: String)

    suspend fun delete(tagId: Long)

    // Assignments
    suspend fun getTagIdsByChapterIds(chapterIds: List<Long>): Map<Long, List<Long>>

    fun getTagsByMangaIdAsFlow(mangaId: Long): Flow<Map<Long, List<ChapterTag>>>

    suspend fun setChapterTags(chapterId: Long, tagIds: List<Long>)

    fun getTagIdsPerMangaAsFlow(): Flow<Map<Long, Set<Long>>>

    // Per-manga filter selection
    fun getFilterTagIdsAsFlow(mangaId: Long): Flow<Set<Long>>

    suspend fun setFilterTagIds(mangaId: Long, tagIds: Set<Long>)
}
