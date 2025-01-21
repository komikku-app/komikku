package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.SAnime

/**
 * A source that may handle opening an SAnime or SEpisode for a given URI.
 *
 * @since extensions-lib 1.5
 */
interface ResolvableSource : Source {

    /**
     * Returns what the given URI may open.
     * Returns [UriType.Unknown] if the source is not able to resolve the URI.
     *
     * @since extensions-lib 1.5
     */
    fun getUriType(uri: String): UriType

    /**
     * Called if [getUriType] is [UriType.Manga].
     * Returns the corresponding SAnime, if possible.
     *
     * @since extensions-lib 1.5
     */
    suspend fun getManga(uri: String): SAnime?

    /**
     * Called if [getUriType] is [UriType.Chapter].
     * Returns the corresponding SEpisode, if possible.
     *
     * @since extensions-lib 1.5
     */
    suspend fun getChapter(uri: String): SEpisode?
}

sealed interface UriType {
    data object Manga : UriType
    data object Chapter : UriType
    data object Unknown : UriType
}
