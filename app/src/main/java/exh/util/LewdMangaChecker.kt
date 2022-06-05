package exh.util

import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.source.SourceManager
import exh.source.isEhBasedManga
import exh.source.nHentaiSourceIds
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

fun Manga.isLewd(): Boolean {
    val sourceName = Injekt.get<SourceManager>().get(source)?.name
    val currentTags = getGenres().orEmpty()

    if (isEhBasedManga() || source in nHentaiSourceIds) {
        return currentTags.none { tag -> isNonHentaiTag(tag) }
    }

    return source in 6905L..6913L ||
        (sourceName != null && isHentaiSource(sourceName)) ||
        currentTags.any { tag -> isHentaiTag(tag) }
}

private fun isNonHentaiTag(tag: String): Boolean {
    return tag.contains("non-h", true)
}

private fun isHentaiTag(tag: String): Boolean {
    return tag.contains("hentai", true) ||
        tag.contains("adult", true) ||
        tag.contains("smut", true) ||
        tag.contains("lewd", true) ||
        tag.contains("nsfw", true) ||
        tag.contains("erotica", true) ||
        tag.contains("pornographic", true) ||
        tag.contains("18+", true)
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
        source.contains("hitomi.la", true) ||
        source.contains("8muses", true) ||
        source.contains("hbrowse", true) ||
        source.contains("nhentai", true) ||
        source.contains("erofus", true) ||
        source.contains("multporn", true) ||
        source.contains("vcp", true) ||
        source.contains("vmp", true) ||
        source.contains("hentai", true)
}
