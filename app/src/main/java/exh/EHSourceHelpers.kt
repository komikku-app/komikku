package exh

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.online.all.Hitomi
import eu.kanade.tachiyomi.source.online.all.MangaDex
import eu.kanade.tachiyomi.source.online.all.NHentai
import eu.kanade.tachiyomi.source.online.all.PervEden
import eu.kanade.tachiyomi.source.online.english.EightMuses
import eu.kanade.tachiyomi.source.online.english.HBrowse
import eu.kanade.tachiyomi.source.online.english.HentaiCafe
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
const val HENTAI_CAFE_SOURCE_ID = 260868874183818481
const val PURURIN_SOURCE_ID = 2221515250486218861
const val TSUMINO_SOURCE_ID = 6707338697138388238
const val EIGHTMUSES_SOURCE_ID = 1802675169972965535
const val HBROWSE_SOURCE_ID = 1401584337232758222
const val MERGED_SOURCE_ID = LEWD_SOURCE_SERIES + 69

private val DELEGATED_LEWD_SOURCES = listOf(
    HentaiCafe::class,
    Pururin::class,
    Tsumino::class,
    HBrowse::class,
    EightMuses::class,
    Hitomi::class,
    PervEden::class,
    NHentai::class
)

private val hitomiClass = listOf(Hitomi::class)
private val nHentaiClass = listOf(NHentai::class)
private val mangaDexClass = listOf(MangaDex::class)

// Used to speed up isLewdSource
val lewdDelegatedSourceIds by lazy {
    SourceManager.currentDelegatedSources.filter {
        it.value.newSourceClass in DELEGATED_LEWD_SOURCES
    }.map { it.value.sourceId }.sorted()
}

val hitomiSourceIds by lazy {
    SourceManager.currentDelegatedSources.filter {
        it.value.newSourceClass in hitomiClass
    }.map { it.value.sourceId }.sorted()
}

val nHentaiSourceIds by lazy {
    SourceManager.currentDelegatedSources.filter {
        it.value.newSourceClass in nHentaiClass
    }.map { it.value.sourceId }.sorted()
}

val mangaDexSourceIds by lazy {
    SourceManager.currentDelegatedSources.filter {
        it.value.newSourceClass in mangaDexClass
    }.map { it.value.sourceId }.sorted()
}

// This method MUST be fast!
fun isLewdSource(source: Long) = source in 6900..6999 ||
    lewdDelegatedSourceIds.binarySearch(source) >= 0

val LIBRARY_UPDATE_EXCLUDED_SOURCES by lazy {
    listOf(
        EH_SOURCE_ID,
        EXH_SOURCE_ID,
        HENTAI_CAFE_SOURCE_ID,
        TSUMINO_SOURCE_ID,
        PURURIN_SOURCE_ID,
        *hitomiSourceIds.toTypedArray(),
        *nHentaiSourceIds.toTypedArray()
    )
}

fun Source.isEhBasedSource() = id == EH_SOURCE_ID || id == EXH_SOURCE_ID

fun Source.isNamespaceSource() = id == EH_SOURCE_ID || id == EXH_SOURCE_ID || id in nHentaiSourceIds || id in hitomiSourceIds || id == PURURIN_SOURCE_ID || id == TSUMINO_SOURCE_ID || id == EIGHTMUSES_SOURCE_ID || id == HBROWSE_SOURCE_ID
