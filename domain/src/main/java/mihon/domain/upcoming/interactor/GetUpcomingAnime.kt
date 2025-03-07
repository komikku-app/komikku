package mihon.domain.upcoming.interactor

import eu.kanade.tachiyomi.source.model.SAnime
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.anime.interactor.GetLibraryAnime
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.repository.AnimeRepository
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ANIME_HAS_UNSEEN
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ANIME_NON_COMPLETED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ANIME_NON_SEEN
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ANIME_OUTSIDE_RELEASE_PERIOD
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.LocalDate
import java.time.ZoneId

class GetUpcomingAnime(
    private val animeRepository: AnimeRepository,
) {
    // KMK -->
    private val libraryPreferences: LibraryPreferences = Injekt.get()
    private val getLibraryAnime: GetLibraryAnime = Injekt.get()
    // KMK <--

    private val includedStatuses = setOf(
        SAnime.ONGOING.toLong(),
        SAnime.PUBLISHING_FINISHED.toLong(),
    )

    suspend fun subscribe(): Flow<List<Anime>> {
        return animeRepository.getUpcomingAnime(includedStatuses)
    }

    // KMK -->
    suspend fun updatingMangas(): List<Anime> {
        val libraryManga = getLibraryAnime.await()

        val categoriesToUpdate = libraryPreferences.updateCategories().get().map(String::toLong)
        val includedManga = if (categoriesToUpdate.isNotEmpty()) {
            libraryManga.filter { it.category in categoriesToUpdate }
        } else {
            libraryManga
        }

        val categoriesToExclude = libraryPreferences.updateCategoriesExclude().get().map { it.toLong() }
        val excludedMangaIds = if (categoriesToExclude.isNotEmpty()) {
            libraryManga.filter { it.category in categoriesToExclude }.map { it.anime.id }
        } else {
            emptyList()
        }

        val listToUpdate = includedManga
            .filterNot { it.anime.id in excludedMangaIds }

        val restrictions = libraryPreferences.autoUpdateAnimeRestrictions().get()
        val today = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toEpochSecond() * 1000

        return listToUpdate
            .distinctBy { it.anime.id }
            .filter {
                when {
                    it.anime.updateStrategy != UpdateStrategy.ALWAYS_UPDATE -> false

                    ANIME_NON_COMPLETED in restrictions && it.anime.status.toInt() == SAnime.COMPLETED -> false

                    ANIME_HAS_UNSEEN in restrictions && it.unseenCount != 0L -> false

                    ANIME_NON_SEEN in restrictions && it.totalEpisodes > 0L && !it.hasStarted -> false

                    ANIME_OUTSIDE_RELEASE_PERIOD in restrictions && it.anime.nextUpdate < today -> false

                    else -> true
                }
            }
            .map { it.anime }
            .sortedBy { it.nextUpdate }
    }
    // KMK <--
}
