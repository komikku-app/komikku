package eu.kanade.tachiyomi.source

import dev.icerock.moko.graphics.BuildConfig
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

/**
 * A basic interface for creating a source. It could be an online source, a local source, etc.
 *
 * Supposedly, it expects extensions to overwrite get...() methods while leaving those fetch...() alone.
 * Hence in extensions-lib, it will leave get...() methods as unimplemented
 * and fetch...() as IllegalStateException("Not used").
 *
 * Prior to extensions-lib 1.5, all extensions still using fetch...(). Because of this,
 * in extensions-lib all get...() methods will be implemented as Exception("Stub!") while
 * all fetch...() methods will leave unimplemented.
 * But if we want to migrate extensions to use get...() then those fetch...()
 * should still be implemented as IllegalStateException("Not used").
 */
interface CatalogueSource : Source {

    /**
     * An ISO 639-1 compliant language code (two letters in lower case).
     */
    override val lang: String

    /**
     * Whether the source has support for latest updates.
     */
    val supportsLatest: Boolean

    /**
     * Get a page with a list of manga.
     *
     * @since extensions-lib 1.5
     * @param page the page number to retrieve.
     */
    suspend fun getPopularManga(page: Int): MangasPage

    /**
     * Get a page with a list of manga.
     *
     * @since extensions-lib 1.5
     * @param page the page number to retrieve.
     * @param query the search query.
     * @param filters the list of filters to apply.
     */
    suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage

    /**
     * Get a page with a list of latest manga updates.
     *
     * @since extensions-lib 1.5
     * @param page the page number to retrieve.
     */
    suspend fun getLatestUpdates(page: Int): MangasPage

    /**
     * Returns the list of filters for the source.
     */
    fun getFilterList(): FilterList

    // KMK -->
    /**
     * Whether parsing related mangas in manga page or extension provide custom related mangas request.
     * @default false
     * @since komikku/extensions-lib 1.6
     */
    val supportsRelatedMangas: Boolean get() = false

    /**
     * Extensions doesn't want to use App's [getRelatedMangaListBySearch].
     * @default false
     * @since komikku/extensions-lib 1.6
     */
    val disableRelatedMangasBySearch: Boolean get() = false

    /**
     * Disable showing any related titles.
     * @default false
     * @since komikku/extensions-lib 1.6
     */
    val disableRelatedMangas: Boolean get() = false

    /**
     * Get all the available related mangas for a manga.
     * Normally it's not needed to override this method.
     *
     * @since komikku/extensions-lib 1.6
     * @param manga the current manga to get related mangas.
     * @return a list of <keyword, related mangas>
     * @throws UnsupportedOperationException if a source doesn't support related mangas.
     */
    override suspend fun getRelatedMangaList(
        manga: SManga,
        exceptionHandler: (Throwable) -> Unit,
        pushResults: suspend (relatedManga: Pair<String, List<SManga>>, completed: Boolean) -> Unit,
    ) {
        val handler = CoroutineExceptionHandler { _, e -> exceptionHandler(e) }
        if (!disableRelatedMangas) {
            supervisorScope {
                if (supportsRelatedMangas) launch(handler) { getRelatedMangaListByExtension(manga, pushResults) }
                if (!disableRelatedMangasBySearch) launch(handler) { getRelatedMangaListBySearch(manga, pushResults) }
            }
        }
    }

    /**
     * Get related mangas provided by extension
     *
     * @return a list of <keyword, related mangas>
     * @since komikku/extensions-lib 1.6
     */
    suspend fun getRelatedMangaListByExtension(
        manga: SManga,
        pushResults: suspend (relatedManga: Pair<String, List<SManga>>, completed: Boolean) -> Unit,
    ) {
        runCatching { fetchRelatedMangaList(manga) }
            .onSuccess { if (it.isNotEmpty()) pushResults(Pair("", it), false) }
            .onFailure { e ->
                @Suppress("KotlinConstantConditions")
                if (BuildConfig.BUILD_TYPE == "release") {
                    logcat(LogPriority.ERROR, e) { "## getRelatedMangaListByExtension: $e" }
                } else {
                    throw UnsupportedOperationException(
                        "Extension doesn't support site's related entries," +
                            " please report an issue to Komikku."
                    )
                }
            }
    }

    /**
     * Fetch related mangas for a manga from source/site.
     *
     * @since komikku/extensions-lib 1.6
     * @param manga the current manga to get related mangas.
     * @return the related mangas for the current manga.
     * @throws UnsupportedOperationException if a source doesn't support related mangas.
     */
    suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = throw UnsupportedOperationException("Unsupported!")

    /**
     * Slit & strip manga's title into separate searchable keywords.
     * Used for searching related mangas.
     *
     * @since komikku/extensions-lib 1.6
     * @return List of keywords.
     */
    fun String.stripKeywordForRelatedMangas(): List<String> {
        val regexWhitespace = Regex("\\s+")
        val regexSpecialCharacters =
            Regex("([!~#$%^&*+_|/\\\\,?:;'“”‘’\"<>(){}\\[\\]。・～：—！？、―«»《》〘〙【】「」｜]|\\s-|-\\s|\\s\\.|\\.\\s)")
        val regexNumberOnly = Regex("^\\d+$")

        return replace(regexSpecialCharacters, " ")
            .split(regexWhitespace)
            .map {
                // remove number only
                it.replace(regexNumberOnly, "")
                    .lowercase()
            }
            // exclude single character
            .filter { it.length > 1 }
    }

    /**
     * Get related mangas by searching for each keywords from manga's title.
     *
     * @return a list of <keyword, related mangas>
     * @since komikku/extensions-lib 1.6
     */
    suspend fun getRelatedMangaListBySearch(
        manga: SManga,
        pushResults: suspend (relatedManga: Pair<String, List<SManga>>, completed: Boolean) -> Unit,
    ) {
        val words = HashSet<String>()
        words.add(manga.title)
        if (manga.title.lowercase() != manga.originalTitle.lowercase()) words.add(manga.originalTitle)
        manga.title.stripKeywordForRelatedMangas()
            .filterNot { word -> words.any { it.lowercase() == word } }
            .onEach { words.add(it) }
        manga.originalTitle.stripKeywordForRelatedMangas()
            .filterNot { word -> words.any { it.lowercase() == word } }
            .onEach { words.add(it) }
        if (words.isEmpty()) return

        coroutineScope {
            words.map { keyword ->
                launch {
                    runCatching {
                        getSearchManga(1, keyword, FilterList()).mangas
                    }
                        .onSuccess { if (it.isNotEmpty()) pushResults(Pair(keyword, it), false) }
                        .onFailure { e ->
                            logcat(LogPriority.ERROR, e) { "## getRelatedMangaListBySearch: $e" }
                        }
                }
            }
        }
    }
    // KMK <--
}
