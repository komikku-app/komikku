package eu.kanade.data.exh

import exh.metadata.sql.models.SearchTag

val searchTagMapper: (Long, Long, String?, String, Int) -> SearchTag =
    { id, mangaId, namespace, name, type ->
        SearchTag(
            id = id,
            mangaId = mangaId,
            namespace = namespace,
            name = name,
            type = type,
        )
    }
