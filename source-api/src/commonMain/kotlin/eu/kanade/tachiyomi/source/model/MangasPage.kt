package eu.kanade.tachiyomi.source.model

import exh.metadata.metadata.RaisedSearchMetadata

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

    fun copy(mangas: List<SManga> = this.mangas, hasNextPage: Boolean = this.hasNextPage): MangasPage {
        return MangasPage(mangas, hasNextPage)
    }

    override fun toString(): String {
        return "MangasPage(mangas=$mangas, hasNextPage=$hasNextPage)"
    }

    // KMK -->
    // Additional methods to mimic data class behavior
    operator fun component1() = mangas
    operator fun component2() = hasNextPage
    // KMK <--
}

// SY -->
class MetadataMangasPage(
    override val mangas: List<SManga>,
    override val hasNextPage: Boolean,
    val mangasMetadata: List<RaisedSearchMetadata>,
    val nextKey: Long? = null,
) : MangasPage(mangas, hasNextPage) {
    fun copy(
        mangas: List<SManga> = this.mangas,
        hasNextPage: Boolean = this.hasNextPage,
        mangasMetadata: List<RaisedSearchMetadata> = this.mangasMetadata,
        nextKey: Long? = this.nextKey,
    ): MangasPage {
        return MetadataMangasPage(mangas, hasNextPage, mangasMetadata, nextKey)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as MetadataMangasPage

        if (mangas != other.mangas) return false
        if (hasNextPage != other.hasNextPage) return false
        if (mangasMetadata != other.mangasMetadata) return false
        if (nextKey != other.nextKey) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + mangas.hashCode()
        result = 31 * result + hasNextPage.hashCode()
        result = 31 * result + mangasMetadata.hashCode()
        result = 31 * result + nextKey.hashCode()
        return result
    }

    override fun toString(): String {
        return "MetadataMangasPage(" +
            "mangas=$mangas, " +
            "hasNextPage=$hasNextPage, " +
            "mangasMetadata=$mangasMetadata, " +
            "nextKey=$nextKey" +
            ")"
    }
}
// SY <--
