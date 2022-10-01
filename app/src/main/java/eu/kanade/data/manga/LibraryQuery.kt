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
        unreadCount = cursor.getLong(20)!!,
        readCount = cursor.getLong(21)!!,
        category = cursor.getLong(22)!!,
    )
}

class LibraryQuery(val driver: SqlDriver) : Query<LibraryManga>(copyOnWriteList(), mapper) {
    override fun execute(): SqlCursor {
        return driver.executeQuery(
            null,
            """
            SELECT M.*, COALESCE(MC.category_id, 0) AS category
            FROM (
                SELECT mangas.*, COALESCE(UR.unreadCount, 0) AS unread_count, COALESCE(R.readCount, 0) AS read_count
                    FROM mangas
                    LEFT JOIN (
                        SELECT chapters.manga_id, COUNT(*) AS unreadCount
                        FROM chapters
                        WHERE chapters.read = 0
                        GROUP BY chapters.manga_id
                    ) AS UR
                    ON mangas._id = UR.manga_id
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
                SELECT mangas.*, COALESCE(UR.unreadCount, 0) AS unread_count, COALESCE(R.readCount, 0) AS read_count
                    FROM mangas
                    LEFT JOIN (
                        SELECT merged.merge_id, COUNT(*) as unreadCount
                        FROM merged
                        JOIN chapters
                        ON chapters.manga_id = merged.manga_id
                        WHERE chapters.read = 0
                        GROUP BY merged.merge_id
                    ) AS UR
                    ON mangas._id = UR.merge_id
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
