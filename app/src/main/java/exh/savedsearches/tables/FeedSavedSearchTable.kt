package exh.savedsearches.tables

object FeedSavedSearchTable {

    const val TABLE = "feed_saved_search"

    const val COL_ID = "_id"

    const val COL_SOURCE = "source"

    const val COL_SAVED_SEARCH_ID = "saved_search"

    const val COL_GLOBAL = "global"

    val createTableQuery: String
        get() =
            """CREATE TABLE $TABLE(
            $COL_ID INTEGER NOT NULL PRIMARY KEY,
            $COL_SOURCE INTEGER NOT NULL,
            $COL_SAVED_SEARCH_ID INTEGER,
            $COL_GLOBAL BOOLEAN NOT NULL,
            FOREIGN KEY($COL_SAVED_SEARCH_ID) REFERENCES ${SavedSearchTable.TABLE} (${SavedSearchTable.COL_ID})
            ON DELETE CASCADE
            )"""

    val createSavedSearchIdIndexQuery: String
        get() = "CREATE INDEX ${TABLE}_${COL_SAVED_SEARCH_ID}_index ON $TABLE($COL_SAVED_SEARCH_ID)"
}
