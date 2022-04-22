package exh.merged.sql.tables

import eu.kanade.tachiyomi.data.database.tables.MangaTable

object MergedTable {

    const val TABLE = "merged"

    const val COL_ID = "_id"

    const val COL_IS_INFO_MANGA = "info_manga"

    const val COL_GET_CHAPTER_UPDATES = "get_chapter_updates"

    const val COL_CHAPTER_SORT_MODE = "chapter_sort_mode"

    const val COL_CHAPTER_PRIORITY = "chapter_priority"

    const val COL_DOWNLOAD_CHAPTERS = "download_chapters"

    const val COL_MERGE_ID = "merge_id"

    const val COL_MERGE_URL = "merge_url"

    const val COL_MANGA_ID = "manga_id"

    const val COL_MANGA_URL = "manga_url"

    const val COL_MANGA_SOURCE = "manga_source"

    val createTableQuery: String
        get() =
            """CREATE TABLE $TABLE(
            $COL_ID INTEGER NOT NULL PRIMARY KEY,
            $COL_IS_INFO_MANGA BOOLEAN NOT NULL,
            $COL_GET_CHAPTER_UPDATES BOOLEAN NOT NULL,
            $COL_CHAPTER_SORT_MODE INTEGER NOT NULL,
            $COL_CHAPTER_PRIORITY INTEGER NOT NULL,
            $COL_DOWNLOAD_CHAPTERS BOOLEAN NOT NULL,
            $COL_MERGE_ID INTEGER NOT NULL,
            $COL_MERGE_URL TEXT NOT NULL,
            $COL_MANGA_ID INTEGER,
            $COL_MANGA_URL TEXT NOT NULL,
            $COL_MANGA_SOURCE INTEGER NOT NULL,
            FOREIGN KEY($COL_MANGA_ID) REFERENCES ${MangaTable.TABLE} (${MangaTable.COL_ID})
            ON DELETE SET NULL,
            FOREIGN KEY($COL_MERGE_ID) REFERENCES ${MangaTable.TABLE} (${MangaTable.COL_ID})
            ON DELETE CASCADE
            )"""

    val dropTableQuery: String
        get() = "DROP TABLE $TABLE"

    val createIndexQuery: String
        get() = "CREATE INDEX ${TABLE}_${COL_MERGE_ID}_index ON $TABLE($COL_MERGE_ID)"
}
