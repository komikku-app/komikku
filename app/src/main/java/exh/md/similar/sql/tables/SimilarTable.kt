package exh.md.similar.sql.tables

object SimilarTable {

    const val TABLE = "manga_related"

    const val COL_ID = "_id"

    const val COL_MANGA_ID = "manga_id"

    const val COL_MANGA_SIMILAR_MATCHED_IDS = "matched_ids"

    const val COL_MANGA_SIMILAR_MATCHED_TITLES = "matched_titles"

    val createTableQuery: String
        get() =
            """CREATE TABLE $TABLE(
            $COL_ID INTEGER NOT NULL PRIMARY KEY,
            $COL_MANGA_ID INTEGER NOT NULL,
            $COL_MANGA_SIMILAR_MATCHED_IDS TEXT NOT NULL,
            $COL_MANGA_SIMILAR_MATCHED_TITLES TEXT NOT NULL,
            UNIQUE ($COL_ID) ON CONFLICT REPLACE
            )"""

    val createMangaIdIndexQuery: String
        get() = "CREATE INDEX ${TABLE}_${COL_MANGA_SIMILAR_MATCHED_IDS}_index ON $TABLE($COL_MANGA_SIMILAR_MATCHED_IDS)"
}
