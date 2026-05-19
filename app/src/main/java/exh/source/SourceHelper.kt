package exh.source

import eu.kanade.tachiyomi.source.AndroidSourceManager
import eu.kanade.tachiyomi.source.online.all.Lanraragi
import eu.kanade.tachiyomi.source.online.all.MangaDex
import eu.kanade.tachiyomi.source.online.all.NHentai
import eu.kanade.tachiyomi.source.online.english.EightMuses
import eu.kanade.tachiyomi.source.online.english.HBrowse
import eu.kanade.tachiyomi.source.online.english.Pururin
import eu.kanade.tachiyomi.source.online.english.Tsumino

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
        Lanraragi::class,
    )
}

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

    lanraragiSourceIds = AndroidSourceManager.currentDelegatedSources
        .filter {
            it.value.newSourceClass == Lanraragi::class
        }
        .map { it.value.sourceId }
        .sorted()

    LIBRARY_UPDATE_EXCLUDED_SOURCES = listOf(
        EH_SOURCE_ID,
        EXH_SOURCE_ID,
        PURURIN_SOURCE_ID,
    ) + nHentaiSourceIds
}
