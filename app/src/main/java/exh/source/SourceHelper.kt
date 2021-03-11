package exh.source

import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.online.all.Hitomi
import eu.kanade.tachiyomi.source.online.all.MangaDex
import eu.kanade.tachiyomi.source.online.all.NHentai
import eu.kanade.tachiyomi.source.online.all.PervEden
import eu.kanade.tachiyomi.source.online.english.EightMuses
import eu.kanade.tachiyomi.source.online.english.HBrowse
import eu.kanade.tachiyomi.source.online.english.Pururin
import eu.kanade.tachiyomi.source.online.english.Tsumino

/**
 * Source helpers
 */

// Lewd source IDs
const val LEWD_SOURCE_SERIES = 6900L
const val EH_SOURCE_ID = LEWD_SOURCE_SERIES + 1
const val EXH_SOURCE_ID = LEWD_SOURCE_SERIES + 2
const val PERV_EDEN_EN_SOURCE_ID = 4673633799850248749
const val PERV_EDEN_IT_SOURCE_ID = 1433898225963724122
const val PURURIN_SOURCE_ID = 2221515250486218861
const val TSUMINO_SOURCE_ID = 6707338697138388238
const val EIGHTMUSES_SOURCE_ID = 1802675169972965535
const val HBROWSE_SOURCE_ID = 1401584337232758222
const val MERGED_SOURCE_ID = LEWD_SOURCE_SERIES + 69

private val DELEGATED_METADATA_SOURCES by lazy {
    listOf(
        Pururin::class,
        Tsumino::class,
        HBrowse::class,
        EightMuses::class,
        Hitomi::class,
        PervEden::class,
        NHentai::class
    )
}

// Used to speed up isLewdSource
var metadataDelegatedSourceIds: List<Long> = emptyList()

var hitomiSourceIds: List<Long> = emptyList()

var nHentaiSourceIds: List<Long> = emptyList()

var mangaDexSourceIds: List<Long> = emptyList()

var LIBRARY_UPDATE_EXCLUDED_SOURCES = listOf(
    EH_SOURCE_ID,
    EXH_SOURCE_ID,
    TSUMINO_SOURCE_ID,
    PURURIN_SOURCE_ID
)

fun handleSourceLibrary() {
    metadataDelegatedSourceIds = SourceManager.currentDelegatedSources
        .filter {
            it.value.newSourceClass in DELEGATED_METADATA_SOURCES
        }
        .map { it.value.sourceId }
        .sorted()

    hitomiSourceIds = SourceManager.currentDelegatedSources
        .filter {
            it.value.newSourceClass == Hitomi::class
        }
        .map { it.value.sourceId }
        .sorted()

    nHentaiSourceIds = SourceManager.currentDelegatedSources
        .filter {
            it.value.newSourceClass == NHentai::class
        }
        .map { it.value.sourceId }
        .sorted()

    mangaDexSourceIds = SourceManager.currentDelegatedSources
        .filter {
            it.value.newSourceClass == MangaDex::class
        }
        .map { it.value.sourceId }
        .sorted()

    LIBRARY_UPDATE_EXCLUDED_SOURCES = listOf(
        EH_SOURCE_ID,
        EXH_SOURCE_ID,
        TSUMINO_SOURCE_ID,
        PURURIN_SOURCE_ID
    ) + hitomiSourceIds + nHentaiSourceIds
}

// This method MUST be fast!
fun isMetadataSource(source: Long) = source in 6900..6999 ||
    metadataDelegatedSourceIds.binarySearch(source) >= 0

fun Source.isEhBasedSource() = id == EH_SOURCE_ID || id == EXH_SOURCE_ID

fun Manga.isEhBasedManga() = source == EH_SOURCE_ID || source == EXH_SOURCE_ID

fun Source.getMainSource(): Source = if (this is EnhancedHttpSource) {
    this.source()
} else {
    this
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
