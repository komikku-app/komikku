package eu.kanade.data.exh

import exh.metadata.sql.models.SearchMetadata

val searchMetadataMapper: (Long, String?, String, String?, Int) -> SearchMetadata =
    { mangaId, uploader, extra, indexedExtra, extraVersion ->
        SearchMetadata(
            mangaId = mangaId,
            uploader = uploader,
            extra = extra,
            indexedExtra = indexedExtra,
            extraVersion = extraVersion,
        )
    }
