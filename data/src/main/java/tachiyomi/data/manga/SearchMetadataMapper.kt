package tachiyomi.data.manga

import exh.metadata.sql.models.SearchMetadata

val searchMetadataMapper: (Long, String?, String, String?, Long) -> SearchMetadata =
    { mangaId, uploader, extra, indexedExtra, extraVersion ->
        SearchMetadata(
            mangaId = mangaId,
            uploader = uploader,
            extra = extra,
            indexedExtra = indexedExtra,
            extraVersion = extraVersion.toInt(),
        )
    }
