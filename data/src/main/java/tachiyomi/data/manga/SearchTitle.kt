package tachiyomi.data.manga

import exh.metadata.sql.models.SearchTitle

val searchTitleMapper: (Long, Long, String, Long) -> SearchTitle =
    { id, mangaId, title, type ->
        SearchTitle(
            id = id,
            mangaId = mangaId,
            title = title,
            type = type.toInt(),
        )
    }
