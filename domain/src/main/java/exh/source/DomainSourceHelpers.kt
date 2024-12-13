package exh.source

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.all.EhBasedSource
import tachiyomi.domain.manga.model.Manga

// Used to speed up isLewdSource
var metadataDelegatedSourceIds: List<Long> = emptyList()

var nHentaiSourceIds: List<Long> = emptyList()

var mangaDexSourceIds: List<Long> = emptyList()

// KMK -->
var LIBRARY_UPDATE_EXCLUDED_SOURCES = listOf(
    PURURIN_SOURCE_ID,
) + EHENTAI_EXT_SOURCES.keys + EXHENTAI_EXT_SOURCES.keys
// KMK <--

// This method MUST be fast!
fun isMetadataSource(source: Long) = source in 6900..6999 ||
    metadataDelegatedSourceIds.binarySearch(source) >= 0

// KMK -->
fun Source.isEhBasedSource() = this is EhBasedSource && id in EHENTAI_EXT_SOURCES || id in EXHENTAI_EXT_SOURCES
// KMK <--

fun Source.isMdBasedSource() = id in mangaDexSourceIds

// KMK -->
fun Manga.isEhBasedManga() = source in EHENTAI_EXT_SOURCES || source in EXHENTAI_EXT_SOURCES
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
