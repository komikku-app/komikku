package eu.kanade.data.manga

import com.squareup.sqldelight.Query
import com.squareup.sqldelight.db.SqlCursor
import com.squareup.sqldelight.db.SqlDriver
import com.squareup.sqldelight.internal.copyOnWriteList
import eu.kanade.data.listOfStringsAdapter
import eu.kanade.data.listOfStringsAndAdapter
import eu.kanade.data.updateStrategyAdapter
import eu.kanade.domain.library.model.LibraryManga
import exh.source.MERGED_SOURCE_ID

private val mapper = { cursor: SqlCursor ->
    LibraryManga(
        manga = mangaMapper(
            cursor.getLong(0)!!,
            cursor.getLong(1)!!,
            cursor.getString(2)!!,
            cursor.getString(3),
            cursor.getString(4),
            cursor.getString(5),
            cursor.getString(6)?.let(listOfStringsAdapter::decode),
            cursor.getString(7)!!,
            cursor.getLong(8)!!,
            cursor.getString(9),
            cursor.getLong(10)!! == 1L,
            cursor.getLong(11) ?: 0,
            null,
            cursor.getLong(13)!! == 1L,
            cursor.getLong(14)!!,
            cursor.getLong(15)!!,
            cursor.getLong(16)!!,
            cursor.getLong(17)!!,
            cursor.getString(18)?.let(listOfStringsAndAdapter::decode),
            updateStrategyAdapter.decode(cursor.getLong(19)!!),
        ),
        totalChapters = cursor.getLong(20)!!,
        readCount = cursor.getLong(21)!!,
        latestUpload = cursor.getLong(22)!!,
        chapterFetchedAt = cursor.getLong(23)!!,
        lastRead = cursor.getLong(24)!!,
        bookmarkCount = cursor.getLong(25)!!,
        category = cursor.getLong(26)!!,
    )
}

class LibraryQuery(val driver: SqlDriver) : Query<LibraryManga>(copyOnWriteList(), mapper) {
    override fun execute(): SqlCursor {
        return driver.executeQuery(
            null,
            """
            SELECT
                M.*,
                coalesce(C.total, 0) AS totalCount,
                coalesce(C.readCount, 0) AS readCount,
                coalesce(C.latestUpload, 0) AS latestUpload,
                coalesce(C.fetchedAt, 0) AS chapterFetchedAt,
                coalesce(C.lastRead, 0) AS lastRead,
                coalesce(C.bookmarkCount, 0) AS bookmarkCount,
                coalesce(MC.category_id, 0) AS category
            FROM mangas M
            LEFT JOIN(
                SELECT
                    chapters.manga_id,
                    count(*) AS total,
                    sum(read) AS readCount,
                    coalesce(max(chapters.date_upload), 0) AS latestUpload,
                    coalesce(max(history.last_read), 0) AS lastRead,
                    coalesce(max(chapters.date_fetch), 0) AS fetchedAt,
                    sum(chapters.bookmark) AS bookmarkCount
                FROM chapters
                LEFT JOIN history
                ON chapters._id = history.chapter_id
                GROUP BY chapters.manga_id
            ) AS C
            ON M._id = C.manga_id
            LEFT JOIN mangas_categories AS MC
            ON MC.manga_id = M._id
            WHERE M.favorite = 1 AND M.source <> $MERGED_SOURCE_ID
            UNION
            SELECT
                M.*,
                coalesce(C.total, 0) AS totalCount,
                coalesce(C.readCount, 0) AS readCount,
                coalesce(C.latestUpload, 0) AS latestUpload,
                coalesce(C.fetchedAt, 0) AS chapterFetchedAt,
                coalesce(C.lastRead, 0) AS lastRead,
                coalesce(C.bookmarkCount, 0) AS bookmarkCount,
                coalesce(MC.category_id, 0) AS category
            FROM mangas M
            LEFT JOIN (
                SELECT merged.manga_id,merged.merge_id
                FROM merged
                GROUP BY merged.merge_id
            ) as ME
            ON ME.merge_id = M._id
            LEFT JOIN(
                SELECT
                    ME.merge_id,
                    count(*) AS total,
                    sum(read) AS readCount,
                    coalesce(max(chapters.date_upload), 0) AS latestUpload,
                    coalesce(max(history.last_read), 0) AS lastRead,
                    coalesce(max(chapters.date_fetch), 0) AS fetchedAt,
                    sum(chapters.bookmark) AS bookmarkCount
                FROM chapters
                LEFT JOIN history
                ON chapters._id = history.chapter_id
                LEFT JOIN merged as ME
                ON ME.manga_id = chapters.manga_id
                GROUP BY ME.merge_id
            ) AS C
            ON ME.merge_id = C.merge_id
            LEFT JOIN mangas_categories AS MC
            ON MC.manga_id = M._id
            WHERE M.favorite = 1 AND M.source = $MERGED_SOURCE_ID;
            """.trimIndent(),
            1,
        )
    }

    override fun toString(): String = "LibraryQuery.sq:get"
}
