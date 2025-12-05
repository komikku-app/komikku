@file:Suppress("PropertyName")

package exh.source

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.all.EhBasedSource
import tachiyomi.domain.manga.model.Manga

// Used to speed up isLewdSource
var metadataDelegatedSourceIds: List<Long> = emptyList()

var nHentaiSourceIds: List<Long> = emptyList()

var lanraragiSourceIds: List<Long> = emptyList()

var mangaDexSourceIds: List<Long> = emptyList()

var LIBRARY_UPDATE_EXCLUDED_SOURCES = listOf(
    EH_SOURCE_ID,
    EXH_SOURCE_ID,
    PURURIN_SOURCE_ID,
)

// This method MUST be fast!
fun isMetadataSource(source: Long) = source in 6900..6999 ||
    // KMK -->
    source == EH_SOURCE_ID ||
    source == EXH_SOURCE_ID ||
    // KMK <--
    metadataDelegatedSourceIds.binarySearch(source) >= 0

// KMK -->
fun Source.isEhBasedSource() = this is EhBasedSource && id in hentaiSourceIds
// KMK <--

fun Source.isMdBasedSource() = id in mangaDexSourceIds

// KMK -->
fun Manga.isEhBasedManga() = source in hentaiSourceIds
// KMK <--

fun Source.getMainSource(): Source = if (this is EnhancedHttpSource) {
    this.source()
} else {
    this
}

@JvmName("getMainSourceInline")
inline fun <reified T : Source> Source.getMainSource(): T? = if (this is EnhancedHttpSource) {
    this.source() as? T
} else {
    this as? T
}

fun Source.getOriginalSource(): Source = if (this is EnhancedHttpSource) {
    this.originalSource
} else {
    this
}

fun Source.getEnhancedSource(): Source = if (this is EnhancedHttpSource) {
    this.enhancedSource
} else {
    this
}

inline fun <reified T> Source.anyIs(): Boolean {
    return if (this is EnhancedHttpSource) {
        originalSource is T || enhancedSource is T
    } else {
        this is T
    }
}
