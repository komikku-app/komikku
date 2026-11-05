package eu.kanade.domain.chapter.interactor

import tachiyomi.core.metadata.comicinfo.ComicInfo
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.service.ChapterRecognition

/**
 * Extracts match keys from on-disk chapter download folder names and links them to catalog
 * [Chapter] rows.
 *
 * Match contract for [matchCatalogChapter] (first unique hit wins; otherwise `null`):
 * 1. **Exact folder name** — basename (without `.cbz`) equals `chapter.name`, or
 *    `{scanlator}_{chapter.name}` when [Chapter.scanlator] is set. A trailing `_[a-f0-9]{6}` URL
 *    hash suffix is ignored for this comparison.
 * 2. **ComicInfo** — `<Number>` parses to a single catalog chapter number; when `<Translator>` is
 *    present, [Chapter.scanlator] must match (case-insensitive).
 * 3. **Folder basename** — chapter number parsed via [parseChapterNumberFromFolder];
 *    when a scanlator prefix is detected, [Chapter.scanlator] must match. Multiple catalog hits
 *    without a disambiguating scanlator yield `null`. A digit-bearing `{prefix}_Chapter N`
 *    folder is numbered from `Chapter N` only. That prefix is not a scanlator.
 *
 * Orphan rows (`url` starting with `orphaned://`) are never returned.
 *
 * @see `roadmap/downloaded manga not showing up/download_registry_implementation.md`
 */
object DownloadFolderMatcher {

    private val urlHashSuffix = Regex("_[a-f0-9]{6}$", RegexOption.IGNORE_CASE)

    /**
     * Removes a trailing URL-hash suffix (`_` + 6 hex chars) from a folder or file basename.
     */
    fun stripUrlHashSuffix(name: String): String = name.replace(urlHashSuffix, "")

    /**
     * Returns the scanlator group prefix when the folder follows `{scanlator}_{chapterName}` naming.
     *
     * Example: `unofficial` from `unofficial_Ch. 61_c7c283`.
     * Segments that contain digits (e.g. `Chapter 5.2` from chapter titles with underscores) are
     * not treated as scanlator prefixes.
     */
    fun parseScanlatorPrefix(name: String): String? {
        val withoutHash = stripUrlHashSuffix(name)
        val underscoreIndex = withoutHash.indexOf('_')
        if (underscoreIndex <= 0) return null

        val prefix = withoutHash.substring(0, underscoreIndex)
        if (prefix.isBlank() || prefix.any { it.isDigit() }) return null
        return prefix
    }

    /**
     * Parses a chapter number from a download folder basename.
     *
     * When the name is `{prefix}_{body}` and [prefix] contains a digit while [body] starts with
     * `ch.` or `chapter`, only [body] is parsed. `Fallguy69420_Chapter 10` is chapter 10.
     * Every other name is parsed in full, so global [ChapterRecognition] behavior is unchanged.
     */
    fun parseChapterNumberFromFolder(mangaTitle: String, folderName: String): Double {
        val withoutCbz = folderName.removeSuffix(".cbz")
        val withoutHash = stripUrlHashSuffix(withoutCbz)
        val nameToParse = chapterBodyAfterDigitPrefix(withoutHash) ?: withoutHash
        return ChapterRecognition.parseChapterNumber(mangaTitle, nameToParse, -1.0)
    }

    /**
     * Returns the chapter-title body when [basename] is `{digit-bearing prefix}_{ch.|chapter ...}`.
     * The prefix is not a scanlator. See [parseScanlatorPrefix].
     */
    private fun chapterBodyAfterDigitPrefix(basename: String): String? {
        val separator = basename.indexOf('_')
        if (separator <= 0 || separator >= basename.lastIndex) return null

        val prefix = basename.substring(0, separator)
        if (!prefix.any { it.isDigit() }) return null

        val body = basename.substring(separator + 1)
        val isChapterBody = body.startsWith("ch.", ignoreCase = true) ||
            body.startsWith("chapter", ignoreCase = true)
        if (!isChapterBody) return null
        return body
    }

    /**
     * Returns the single catalog chapter matching [folderName], or `null` when there is no unique
     * match. See class KDoc for rule priority and disambiguation behavior.
     */
    fun matchCatalogChapter(
        catalogChapters: List<Chapter>,
        folderName: String,
        mangaTitle: String,
        comicInfo: ComicInfo? = null,
    ): Chapter? {
        val catalog = catalogChapters.filter { !it.url.startsWith("orphaned://", ignoreCase = true) }
        if (catalog.isEmpty()) return null

        val basename = folderBasename(folderName)

        matchByExactFolderName(basename, catalog)?.let { return it }
        matchByComicInfo(comicInfo, catalog)?.let { return it }
        return matchByFolderBasename(basename, mangaTitle, catalog)
    }

    private fun folderBasename(folderName: String): String =
        stripUrlHashSuffix(folderName.removeSuffix(".cbz"))

    private fun matchByExactFolderName(basename: String, catalog: List<Chapter>): Chapter? {
        val matched = catalog.filter { chapter ->
            basename == chapter.name ||
                (!chapter.scanlator.isNullOrBlank() && basename == "${chapter.scanlator}_${chapter.name}")
        }
        return matched.singleOrNull()
    }

    private fun matchByComicInfo(comicInfo: ComicInfo?, catalog: List<Chapter>): Chapter? {
        val numberValue = comicInfo?.number?.value?.trim().orEmpty()
        if (numberValue.isEmpty()) return null

        val parsedNumber = numberValue.toDoubleOrNull()
            ?: ChapterRecognition.parseChapterNumber("", numberValue, -1.0).takeIf { it >= 0.0 }
            ?: return null

        val byNumber = catalog.filter { it.isRecognizedNumber && it.chapterNumber == parsedNumber }
        if (byNumber.isEmpty()) return null

        val translator = comicInfo?.translator?.value?.trim()?.takeIf { it.isNotEmpty() }
        val matched = if (translator != null) {
            byNumber.filter { chapter ->
                chapter.scanlator?.equals(translator, ignoreCase = true) == true
            }
        } else {
            byNumber
        }

        return matched.singleOrNull()
    }

    private fun matchByFolderBasename(
        basename: String,
        mangaTitle: String,
        catalog: List<Chapter>,
    ): Chapter? {
        val chapterNumber = parseChapterNumberFromFolder(mangaTitle, basename)
        if (chapterNumber < 0.0) return null

        val byNumber = catalog.filter { it.isRecognizedNumber && it.chapterNumber == chapterNumber }
        if (byNumber.isEmpty()) return null
        if (byNumber.size == 1) return byNumber.first()

        val scanlator = parseScanlatorPrefix(basename) ?: return null
        return byNumber
            .filter { chapter -> chapter.scanlator?.equals(scanlator, ignoreCase = true) == true }
            .singleOrNull()
    }
}
