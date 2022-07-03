package eu.kanade.tachiyomi.data.database.queries

import com.pushtorefresh.storio.sqlite.queries.Query
import eu.kanade.tachiyomi.data.database.DbProvider
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.resolvers.ChapterProgressPutResolver
import eu.kanade.tachiyomi.data.database.tables.ChapterTable

interface ChapterQueries : DbProvider {
    // SY -->
    fun getChapters(manga: Manga) = getChapters(manga.id)

    fun getChapters(mangaId: Long?) = db.get()
        .listOfObjects(Chapter::class.java)
        .withQuery(
            Query.builder()
                .table(ChapterTable.TABLE)
                .where("${ChapterTable.COL_MANGA_ID} = ?")
                .whereArgs(mangaId)
                .build(),
        )
        .prepare()
    // SY <--

    fun deleteChapters(chapters: List<Chapter>) = db.delete().objects(chapters).prepare()

    fun updateChapterProgress(chapter: Chapter) = db.put()
        .`object`(chapter)
        .withPutResolver(ChapterProgressPutResolver())
        .prepare()

    fun updateChaptersProgress(chapters: List<Chapter>) = db.put()
        .objects(chapters)
        .withPutResolver(ChapterProgressPutResolver())
        .prepare()
}
