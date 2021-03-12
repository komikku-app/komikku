package eu.kanade.tachiyomi.source.model

import exh.metadata.metadata.base.RaisedSearchMetadata

/* SY --> */ open /* SY <-- */ class MangasPage(open val mangas: List<SManga>, open val hasNextPage: Boolean) {
    // SY -->
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MangasPage) return false

        if (mangas != other.mangas) return false
        if (hasNextPage != other.hasNextPage) return false

        return true
    }

    override fun hashCode(): Int {
        var result = mangas.hashCode()
        result = 31 * result + hasNextPage.hashCode()
        return result
    }
    // SY <--
}

// SY -->
data class MetadataMangasPage(override val mangas: List<SManga>, override val hasNextPage: Boolean, val mangasMetadata: List<RaisedSearchMetadata>) : MangasPage(mangas, hasNextPage)
// SY <--
