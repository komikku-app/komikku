package mihon.domain.upcoming.interactor

import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.AnimeUpdateStrategy
import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.anime.interactor.GetLibraryAnime
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.repository.AnimeRepository
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MANGA_HAS_UNREAD
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MANGA_NON_COMPLETED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MANGA_NON_READ
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MANGA_OUTSIDE_RELEASE_PERIOD
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
        return animeRepository.getUpcomingManga(includedStatuses)
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

        val restrictions = libraryPreferences.autoUpdateMangaRestrictions().get()
        val today = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toEpochSecond() * 1000

        return listToUpdate
            .distinctBy { it.anime.id }
            .filter {
                when {
                    it.anime.updateStrategy != AnimeUpdateStrategy.ALWAYS_UPDATE -> false

                    MANGA_NON_COMPLETED in restrictions && it.anime.status.toInt() == SAnime.COMPLETED -> false

                    MANGA_HAS_UNREAD in restrictions && it.unreadCount != 0L -> false

                    MANGA_NON_READ in restrictions && it.totalChapters > 0L && !it.hasStarted -> false

                    MANGA_OUTSIDE_RELEASE_PERIOD in restrictions && it.anime.nextUpdate < today -> false

                    else -> true
                }
            }
            .map { it.anime }
            .sortedBy { it.nextUpdate }
    }
    // KMK <--
}
