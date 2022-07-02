package eu.kanade.data.manga

import com.squareup.sqldelight.Query
import com.squareup.sqldelight.db.SqlCursor
import com.squareup.sqldelight.db.SqlDriver
import com.squareup.sqldelight.internal.copyOnWriteList
import eu.kanade.tachiyomi.data.database.models.LibraryManga
import exh.source.MERGED_SOURCE_ID

private val mapper = { cursor: SqlCursor ->
    LibraryManga().apply {
        id = cursor.getLong(0)!!
        source = cursor.getLong(1)!!
        url = cursor.getString(2)!!
        artist = cursor.getString(3)
        author = cursor.getString(4)
        description = cursor.getString(5)
        genre = cursor.getString(6)
        title = cursor.getString(7)!!
        status = cursor.getLong(8)!!.toInt()
        thumbnail_url = cursor.getString(9)
        favorite = cursor.getLong(10)!! == 1L
        last_update = cursor.getLong(11) ?: 0
        initialized = cursor.getLong(13)!! == 1L
        viewer_flags = cursor.getLong(14)!!.toInt()
        chapter_flags = cursor.getLong(15)!!.toInt()
        cover_last_modified = cursor.getLong(16)!!
        date_added = cursor.getLong(17)!!
        filtered_scanlators = cursor.getString(18)
        unreadCount = cursor.getLong(19)!!.toInt()
        readCount = cursor.getLong(20)!!.toInt()
        category = cursor.getLong(21)!!.toInt()
    }
}

class LibraryQuery(val driver: SqlDriver) : Query<LibraryManga>(copyOnWriteList(), mapper) {
    override fun execute(): SqlCursor {
        return driver.executeQuery(
            null,
            """
            SELECT M.*, COALESCE(MC.category_id, 0) AS category
            FROM (
                SELECT mangas.*, COALESCE(C.unreadCount, 0) AS unread_count, COALESCE(R.readCount, 0) AS read_count
                    FROM mangas
                    LEFT JOIN (
                        SELECT chapters.manga_id, COUNT(*) AS unreadCount
                        FROM chapters
                        WHERE chapters.read = 0
                        GROUP BY chapters.manga_id
                    ) AS C
                    ON mangas._id = C.manga_id
                    LEFT JOIN (
                        SELECT chapters.manga_id, COUNT(*) AS readCount
                        FROM chapters
                        WHERE chapters.read = 1
                        GROUP BY chapters.manga_id
                    ) AS R
                    ON mangas._id = R.manga_id
                    WHERE mangas.favorite = 1 AND mangas.source <> $MERGED_SOURCE_ID
                    GROUP BY mangas._id
                UNION
                SELECT mangas.*, COALESCE(C.unreadCount, 0) AS unread_count, COALESCE(R.readCount, 0) AS read_count
                    FROM mangas
                    LEFT JOIN (
                        SELECT merged.merge_id, COUNT(*) as unreadCount
                        FROM merged
                        JOIN chapters
                        ON chapters.manga_id = merged.manga_id
                        WHERE chapters.read = 0
                        GROUP BY merged.merge_id
                    ) AS C
                    ON mangas._id = C.merge_id
                    LEFT JOIN (
                        SELECT merged.merge_id, COUNT(*) as readCount
                        FROM merged
                        JOIN chapters
                        ON chapters.manga_id = merged.manga_id
                        WHERE chapters.read = 1
                        GROUP BY merged.merge_id
                    ) AS R
                    ON mangas._id = R.merge_id
                    WHERE mangas.favorite = 1 AND mangas.source = $MERGED_SOURCE_ID
                    GROUP BY mangas._id
                ORDER BY mangas.title
            ) AS M
            LEFT JOIN (
                SELECT *
                FROM mangas_categories
            ) AS MC
            ON M._id = MC.manga_id;
            """.trimIndent(),
            1,
        )
    }

    override fun toString(): String = "LibraryQuery.sq:get"
}
