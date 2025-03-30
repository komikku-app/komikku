package mihon.domain.episode.interactor

import exh.source.MERGED_SOURCE_ID
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.episode.interactor.GetMergedEpisodesByAnimeId
import tachiyomi.domain.episode.model.Episode

/**
 * Interactor responsible for determining which episodes of a anime should be downloaded.
 *
 * @property getEpisodesByAnimeId Interactor for retrieving episodes by anime ID.
 * @property downloadPreferences User preferences related to episode downloads.
 * @property getCategories Interactor for retrieving categories associated with a anime.
 */
class FilterEpisodesForDownload(
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId,
    private val getMergedEpisodesByAnimeId: GetMergedEpisodesByAnimeId,
    private val downloadPreferences: DownloadPreferences,
    private val getCategories: GetCategories,
) {

    /**
     * Determines which episodes of a anime should be downloaded based on user preferences.
     * This should check if user preferences & anime's categories allowed to download
     *
     * @param anime The anime for which episodes may be downloaded.
     * @param newEpisodes The list of new episodes available for the anime.
     * @return A list of episodes that should be downloaded
     */
    suspend fun await(anime: Anime, newEpisodes: List<Episode>): List<Episode> {
        if (
            newEpisodes.isEmpty() ||
            !downloadPreferences.downloadNewEpisodes().get() ||
            !anime.shouldDownloadNewEpisodes()
        ) {
            return emptyList()
        }

        if (!downloadPreferences.downloadNewUnseenEpisodesOnly().get()) return newEpisodes

        // SY -->
        val existingEpisodes = if (anime.source == MERGED_SOURCE_ID) {
            getMergedEpisodesByAnimeId.await(anime.id)
        } else {
            getEpisodesByAnimeId.await(anime.id)
        }

        val seenEpisodeNumbers = existingEpisodes
            // SY <--
            .asSequence()
            .filter { it.seen && it.isRecognizedNumber }
            .map { it.episodeNumber }
            .toSet()

        return newEpisodes.filterNot { it.episodeNumber in seenEpisodeNumbers }
    }

    /**
     * Determines whether new episodes should be downloaded for the anime based on user preferences and the
     * categories to which the anime belongs.
     *
     * @return `true` if episodes of the anime should be downloaded
     */
    private suspend fun Anime.shouldDownloadNewEpisodes(): Boolean {
        if (!favorite) return false

        val categories = getCategories.await(id).map { it.id }.ifEmpty { listOf(DEFAULT_CATEGORY_ID) }
        val includedCategories = downloadPreferences.downloadNewEpisodeCategories().get().map { it.toLong() }
        val excludedCategories = downloadPreferences.downloadNewEpisodeCategoriesExclude().get().map { it.toLong() }

        return when {
            // Default Download from all categories
            includedCategories.isEmpty() && excludedCategories.isEmpty() -> true
            // In excluded category
            categories.any { it in excludedCategories } -> false
            // Included category not selected
            includedCategories.isEmpty() -> true
            // In included category
            else -> categories.any { it in includedCategories }
        }
    }

    companion object {
        private const val DEFAULT_CATEGORY_ID = 0L
    }
}
