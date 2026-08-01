package tachiyomi.data.chapterTag

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.chapterTag.model.ChapterTag
import tachiyomi.domain.chapterTag.repository.ChapterTagRepository

class ChapterTagRepositoryImpl(
    private val handler: DatabaseHandler,
) : ChapterTagRepository {

    override suspend fun getAll(): List<ChapterTag> = handler.awaitList {
        chapter_tagsQueries.getChapterTags(ChapterTagMapper::mapChapterTag)
    }

    override fun getAllAsFlow(): Flow<List<ChapterTag>> = handler.subscribeToList {
        chapter_tagsQueries.getChapterTags(ChapterTagMapper::mapChapterTag)
    }

    override suspend fun insert(name: String): Long = handler.awaitOneExecutable(true) {
        chapter_tagsQueries.insert(name = name)
        chapter_tagsQueries.selectLastInsertedRowId()
    }

    override suspend fun rename(tagId: Long, name: String) {
        handler.await { chapter_tagsQueries.rename(name = name, tagId = tagId) }
    }

    override suspend fun delete(tagId: Long) {
        // The FK cascade would clean both junction tables on its own, but SQLDelight only notifies
        // listeners of the tables named in the executed statement — queries watching the junctions
        // (chapter list filter, per-manga filter selection, library tags-per-manga) would keep
        // serving stale rows. Deleting them explicitly in the same transaction fixes that.
        handler.await(inTransaction = true) {
            chapter_tag_filtersQueries.deleteByTagId(tagId = tagId)
            chapters_chapter_tagsQueries.deleteByTagId(tagId = tagId)
            chapter_tagsQueries.delete(tagId = tagId)
        }
    }

    override suspend fun getTagIdsByChapterIds(chapterIds: List<Long>): Map<Long, List<Long>> {
        if (chapterIds.isEmpty()) return emptyMap()
        return handler.awaitList {
            chapters_chapter_tagsQueries.getTagIdsByChapterIds(chapterIds) { chapterId, tagId -> chapterId to tagId }
        }
            .groupBy({ it.first }, { it.second })
    }

    override suspend fun getTagsByMangaId(mangaId: Long): Map<Long, List<ChapterTag>> = handler
        .awaitList {
            chapter_tagsQueries.getChapterTagsByMangaId(
                mangaId,
                ChapterTagMapper::mapChapterTagWithChapterId,
            )
        }
        .groupBy({ it.first }, { it.second })

    override fun getTagsByMangaIdAsFlow(mangaId: Long): Flow<Map<Long, List<ChapterTag>>> = handler
        .subscribeToList {
            chapter_tagsQueries.getChapterTagsByMangaId(
                mangaId,
                ChapterTagMapper::mapChapterTagWithChapterId,
            )
        }
        .map { rows -> rows.groupBy({ it.first }, { it.second }) }

    override suspend fun setChapterTags(chapterId: Long, tagIds: List<Long>) {
        handler.await(inTransaction = true) {
            chapters_chapter_tagsQueries.deleteByChapterId(chapterId)
            tagIds.forEach { tagId -> chapters_chapter_tagsQueries.insert(chapterId, tagId) }
        }
    }

    override fun getTagIdsPerMangaAsFlow(): Flow<Map<Long, Set<Long>>> = handler
        .subscribeToList {
            chapters_chapter_tagsQueries.getTagIdsPerManga { mangaId, tagId -> mangaId to tagId }
        }
        .map { rows -> rows.groupBy({ it.first }, { it.second }).mapValues { it.value.toSet() } }

    override suspend fun getFilterTagIds(mangaId: Long): Set<Long> = handler
        .awaitList { chapter_tag_filtersQueries.getFilterTagIdsByMangaId(mangaId) }
        .toSet()

    override fun getFilterTagIdsAsFlow(mangaId: Long): Flow<Set<Long>> = handler
        .subscribeToList { chapter_tag_filtersQueries.getFilterTagIdsByMangaId(mangaId) }
        .map { it.toSet() }

    override suspend fun setFilterTagIds(mangaId: Long, tagIds: Set<Long>) {
        handler.await(inTransaction = true) {
            chapter_tag_filtersQueries.deleteByMangaId(mangaId)
            tagIds.forEach { tagId -> chapter_tag_filtersQueries.insert(mangaId, tagId) }
        }
    }
}
