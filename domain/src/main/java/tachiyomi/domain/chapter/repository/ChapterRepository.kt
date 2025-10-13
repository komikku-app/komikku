package tachiyomi.domain.chapter.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterUpdate

interface ChapterRepository {

    suspend fun addAll(chapters: List<Chapter>): List<Chapter>

    suspend fun update(chapterUpdate: ChapterUpdate)

    suspend fun updateAll(chapterUpdates: List<ChapterUpdate>)

    suspend fun removeChaptersWithIds(chapterIds: List<Long>)

    suspend fun softDeleteChaptersWithIds(chapterIds: List<Long>)

    suspend fun getChapterByMangaId(mangaId: Long, applyFilter: Boolean = false, includeDeleted: Boolean = false): List<Chapter>

    suspend fun getScanlatorsByMangaId(mangaId: Long): List<String>

    fun getScanlatorsByMangaIdAsFlow(mangaId: Long): Flow<List<String>>

    suspend fun getBookmarkedChaptersByMangaId(mangaId: Long): List<Chapter>

    suspend fun getChapterById(id: Long): Chapter?

    suspend fun getChapterByMangaIdAsFlow(mangaId: Long, applyFilter: Boolean = false, includeDeleted: Boolean = false): Flow<List<Chapter>>

    suspend fun getChapterByUrlAndMangaId(url: String, mangaId: Long): Chapter?

    // SY -->
    suspend fun getChapterByUrl(url: String): List<Chapter>

    suspend fun getMergedChapterByMangaId(mangaId: Long, applyFilter: Boolean = false): List<Chapter>

    suspend fun getMergedChapterByMangaIdAsFlow(
        mangaId: Long,
        applyFilter: Boolean = false,
    ): Flow<List<Chapter>>

    suspend fun getScanlatorsByMergeId(mangaId: Long): List<String>

    fun getScanlatorsByMergeIdAsFlow(mangaId: Long): Flow<List<String>>
    // SY <--
}
