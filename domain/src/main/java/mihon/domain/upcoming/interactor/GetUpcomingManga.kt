package mihon.domain.upcoming.interactor

import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MANGA_HAS_UNREAD
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MANGA_NON_COMPLETED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MANGA_NON_READ
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MANGA_OUTSIDE_RELEASE_PERIOD
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.LocalDate
import java.time.ZoneId

class GetUpcomingManga(
    private val mangaRepository: MangaRepository,
) {
    // KMK -->
    private val libraryPreferences: LibraryPreferences = Injekt.get()
    private val getLibraryManga: GetLibraryManga = Injekt.get()
    // KMK <--

    private val includedStatuses = setOf(
        SManga.ONGOING.toLong(),
        SManga.PUBLISHING_FINISHED.toLong(),
    )

    suspend fun subscribe(): Flow<List<Manga>> {
        return mangaRepository.getUpcomingManga(includedStatuses)
    }

    // KMK -->
    suspend fun updatingMangas(): List<Manga> {
        val libraryManga = getLibraryManga.await()

        val includedCategories = libraryPreferences.updateCategories().get().map { it.toLong() }.toSet()
        val excludedCategories = libraryPreferences.updateCategoriesExclude().get().map { it.toLong() }.toSet()

        val listToUpdate = libraryManga.filter {
            val included = includedCategories.isEmpty() || it.categories.intersect(includedCategories).isNotEmpty()
            val excluded = it.categories.intersect(excludedCategories).isNotEmpty()
            included && !excluded
        }

        val restrictions = libraryPreferences.autoUpdateMangaRestrictions().get()
        val today = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toEpochSecond() * 1000

        return listToUpdate
            .distinctBy { it.manga.id }
            .filter {
                when {
                    it.manga.updateStrategy != UpdateStrategy.ALWAYS_UPDATE -> false

                    MANGA_NON_COMPLETED in restrictions && it.manga.status.toInt() == SManga.COMPLETED -> false

                    MANGA_HAS_UNREAD in restrictions && it.unreadCount != 0L -> false

                    MANGA_NON_READ in restrictions && it.totalChapters > 0L && !it.hasStarted -> false

                    MANGA_OUTSIDE_RELEASE_PERIOD in restrictions && it.manga.nextUpdate < today -> false

                    else -> true
                }
            }
            .map { it.manga }
            .sortedBy { it.nextUpdate }
    }
    // KMK <--
}
