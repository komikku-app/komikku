package exh.favorites.sql.tables

object FavoriteEntryTable {

    const val TABLE = "eh_favorites"

    const val COL_ID = "_id"

    const val COL_TITLE = "title"

    const val COL_GID = "gid"

    const val COL_TOKEN = "token"

    const val COL_CATEGORY = "category"

    val createTableQuery: String
        get() =
            """CREATE TABLE $TABLE(
            $COL_ID INTEGER NOT NULL PRIMARY KEY,
            $COL_TITLE TEXT NOT NULL,
            $COL_GID TEXT NOT NULL,
            $COL_TOKEN TEXT NOT NULL,
            $COL_CATEGORY INTEGER NOT NULL
            )"""
}
