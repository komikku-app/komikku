package eu.kanade.data.exh

import tachiyomi.domain.manga.model.FavoriteEntry

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
