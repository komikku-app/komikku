package tachiyomi.data.manga

import tachiyomi.domain.manga.model.FavoriteEntry

val favoriteEntryMapper: (String, String, String, Long, String?, String?) -> FavoriteEntry =
    { gid, token, title, category, otherGid, otherToken ->
        FavoriteEntry(
            gid = gid,
            token = token,
            title = title,
            category = category.toInt(),
            otherGid = otherGid,
            otherToken = otherToken,
        )
    }
