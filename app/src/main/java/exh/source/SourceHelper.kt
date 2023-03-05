package exh.source

import eu.kanade.tachiyomi.source.AndroidSourceManager
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.all.MangaDex
import eu.kanade.tachiyomi.source.online.all.NHentai
import eu.kanade.tachiyomi.source.online.english.EightMuses
import eu.kanade.tachiyomi.source.online.english.HBrowse
import eu.kanade.tachiyomi.source.online.english.Pururin
import eu.kanade.tachiyomi.source.online.english.Tsumino
import tachiyomi.domain.manga.model.Manga

/**
 * Source helpers
 */

private val DELEGATED_METADATA_SOURCES by lazy {
    listOf(
        Pururin::class,
        Tsumino::class,
        HBrowse::class,
        EightMuses::class,
        NHentai::class,
    )
}

// Used to speed up isLewdSource
var metadataDelegatedSourceIds: List<Long> = emptyList()

var nHentaiSourceIds: List<Long> = emptyList()

var mangaDexSourceIds: List<Long> = emptyList()

var LIBRARY_UPDATE_EXCLUDED_SOURCES = listOf(
    EH_SOURCE_ID,
    EXH_SOURCE_ID,
    PURURIN_SOURCE_ID,
)

fun handleSourceLibrary() {
    metadataDelegatedSourceIds = AndroidSourceManager.currentDelegatedSources
        .filter {
            it.value.newSourceClass in DELEGATED_METADATA_SOURCES
        }
        .map { it.value.sourceId }
        .sorted()

    nHentaiSourceIds = AndroidSourceManager.currentDelegatedSources
        .filter {
            it.value.newSourceClass == NHentai::class
        }
        .map { it.value.sourceId }
        .sorted()

    mangaDexSourceIds = AndroidSourceManager.currentDelegatedSources
        .filter {
            it.value.newSourceClass == MangaDex::class
        }
        .map { it.value.sourceId }
        .sorted()

    LIBRARY_UPDATE_EXCLUDED_SOURCES = listOf(
        EH_SOURCE_ID,
        EXH_SOURCE_ID,
        PURURIN_SOURCE_ID,
    ) + nHentaiSourceIds
}

// This method MUST be fast!
fun isMetadataSource(source: Long) = source in 6900..6999 ||
    metadataDelegatedSourceIds.binarySearch(source) >= 0

fun Source.isEhBasedSource() = id == EH_SOURCE_ID || id == EXH_SOURCE_ID

fun Source.isMdBasedSource() = id in mangaDexSourceIds

fun Manga.isEhBasedManga() = source == EH_SOURCE_ID || source == EXH_SOURCE_ID

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
