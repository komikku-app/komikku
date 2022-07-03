package eu.kanade.data.exh

import exh.favorites.sql.models.FavoriteEntry

val favoriteEntryMapper: (Long, String, String, String, Long) -> FavoriteEntry =
    { id, title, gid, token, category ->
        FavoriteEntry(
            id = id,
            title = title,
            gid = gid,
            token = token,
            category = category.toInt(),
        )
    }
