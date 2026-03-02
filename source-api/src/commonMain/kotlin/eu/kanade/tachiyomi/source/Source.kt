package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga

/**
 * A basic interface for creating a source. It could be an online source, a local source, stub source, etc.
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
interface Source {

    /**
     * ID for the source. Must be unique.
     */
    val id: Long

    /**
     * Name of the source.
     */
    val name: String

    val lang: String
        get() = ""

    /**
     * Get the updated details for a manga.
     *
     * @since extensions-lib 1.4
     * @param manga the manga to update.
     * @return the updated manga.
     */
    suspend fun getMangaDetails(manga: SManga): SManga

    /**
     * Get all the available chapters for a manga.
     *
     * @since extensions-lib 1.4
     * @param manga the manga to update.
     * @return the chapters for the manga.
     */
    suspend fun getChapterList(manga: SManga): List<SChapter>

    /**
     * Get the list of pages a chapter has. Pages should be returned
     * in the expected order; the index is ignored.
     *
     * @since komikku/extensions-lib 1.7
     * @param chapter the chapter.
     * @return the pages for the chapter.
     */
    suspend fun getPageList(chapter: SChapter): List<Page>

    // KMK -->

    /**
     * Get all the available related mangas for a manga.
     *
     * @since komikku/extensions-lib 1.6
     * @param manga the current manga to get related mangas.
     * @return a list of <keyword, related mangas>
     */
    suspend fun getRelatedMangaList(
        manga: SManga,
        exceptionHandler: (Throwable) -> Unit,
        pushResults: suspend (relatedManga: Pair<String, List<SManga>>, completed: Boolean) -> Unit,
    )
    // KMK <--
}
