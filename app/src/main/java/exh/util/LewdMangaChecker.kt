package exh.util

import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.source.SourceManager
import exh.EH_SOURCE_ID
import exh.EXH_SOURCE_ID
import exh.nHentaiSourceIds
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

fun Manga.isLewd(): Boolean {
    val sourceName = Injekt.get<SourceManager>().get(source)?.name
    val currentTags = getGenres() ?: emptyList()

    if (source == EH_SOURCE_ID || source == EXH_SOURCE_ID || source in nHentaiSourceIds) {
        return !currentTags.any { tag -> isNonHentaiTag(tag) }
    }

    return source in 6905L..6913L ||
        // source in lewdDelegatedSourceIds ||
        (sourceName != null && isHentaiSource(sourceName)) ||
        currentTags.any { tag -> isHentaiTag(tag) }
}

private fun isNonHentaiTag(tag: String): Boolean {
    return tag.contains("non-h", true)
}

private fun isHentaiTag(tag: String): Boolean {
    return tag.contains("hentai", true) ||
        tag.contains("adult", true)
}

private fun isHentaiSource(source: String): Boolean {
    return source.contains("allporncomic", true) ||
        source.contains("hentai cafe", true) ||
        source.contains("hentai2read", true) ||
        source.contains("hentaifox", true) ||
        source.contains("hentainexus", true) ||
        source.contains("manhwahentai.me", true) ||
        source.contains("milftoon", true) ||
        source.contains("myhentaicomics", true) ||
        source.contains("myhentaigallery", true) ||
        source.contains("ninehentai", true) ||
        source.contains("pururin", true) ||
        source.contains("simply hentai", true) ||
        source.contains("tsumino", true) ||
        source.contains("hentai", true)
}
