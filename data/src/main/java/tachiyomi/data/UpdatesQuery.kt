package tachiyomi.data

import app.cash.sqldelight.ExecutableQuery
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import exh.source.MERGED_SOURCE_ID
import tachiyomi.view.UpdatesView

private val mapper = { cursor: SqlCursor ->
    UpdatesView(
        cursor.getLong(0)!!,
        cursor.getString(1)!!,
        cursor.getLong(2)!!,
        cursor.getString(3)!!,
        cursor.getString(4),
        cursor.getLong(5)!! == 1L,
        cursor.getLong(6)!! == 1L,
        cursor.getLong(7)!!,
        cursor.getLong(8)!!,
        cursor.getLong(9)!! == 1L,
        cursor.getString(10),
        cursor.getLong(11)!!,
        cursor.getLong(12)!!,
        cursor.getLong(13)!!,
    )
}

class UpdatesQuery(val driver: SqlDriver, val after: Long, val limit: Long) : ExecutableQuery<UpdatesView>(mapper) {
    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> {
        return driver.executeQuery(
            null,
            """
            SELECT
                mangas._id AS mangaId,
                mangas.title AS mangaTitle,
                chapters._id AS chapterId,
                chapters.name AS chapterName,
                chapters.scanlator,
                chapters.read,
                chapters.bookmark,
                chapters.last_page_read,
                mangas.source,
                mangas.favorite,
                mangas.thumbnail_url AS thumbnailUrl,
                mangas.cover_last_modified AS coverLastModified,
                chapters.date_upload AS dateUpload,
                chapters.date_fetch AS datefetch
            FROM mangas JOIN chapters
            ON mangas._id = chapters.manga_id
            WHERE favorite = 1 AND source <> $MERGED_SOURCE_ID
            AND date_fetch > date_added
            AND dateUpload > :after
            UNION
            SELECT
                mangas._id AS mangaId,
                mangas.title AS mangaTitle,
                chapters._id AS chapterId,
                chapters.name AS chapterName,
                chapters.scanlator,
                chapters.read,
                chapters.bookmark,
                chapters.last_page_read,
                mangas.source,
                mangas.favorite,
                mangas.thumbnail_url AS thumbnailUrl,
                mangas.cover_last_modified AS coverLastModified,
                chapters.date_upload AS dateUpload,
                chapters.date_fetch AS datefetch
            FROM mangas
            LEFT JOIN (
                SELECT merged.manga_id,merged.merge_id
                FROM merged
                GROUP BY merged.merge_id
            ) as ME
            ON ME.merge_id = mangas._id
            JOIN chapters
            ON ME.manga_id = chapters.manga_id
            WHERE favorite = 1 AND source = $MERGED_SOURCE_ID
            AND date_fetch > date_added
            AND dateUpload > :after
            ORDER BY datefetch DESC
            LIMIT :limit;
            """.trimIndent(),
            mapper,
            2,
            binders = {
                bindLong(0, after)
                bindLong(1, limit)
            },
        )
    }

    override fun toString(): String = "LibraryQuery.sq:get"
}
