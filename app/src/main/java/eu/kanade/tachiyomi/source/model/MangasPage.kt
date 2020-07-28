package eu.kanade.tachiyomi.source.model

import exh.metadata.metadata.base.RaisedSearchMetadata

/* SY --> */ open /* SY <-- */ class MangasPage(val mangas: List<SManga>, val hasNextPage: Boolean)

// SY -->
class MetadataMangasPage(mangas: List<SManga>, hasNextPage: Boolean, val mangasMetadata: List<RaisedSearchMetadata>) : MangasPage(mangas, hasNextPage)
// SY <--
