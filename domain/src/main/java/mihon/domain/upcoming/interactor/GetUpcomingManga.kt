package mihon.domain.upcoming.interactor

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository
import kotlinx.coroutines.flow.map
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MANGA_NON_COMPLETED
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.Injekt

class GetUpcomingManga(
    private val mangaRepository: MangaRepository,
) {

    private val includedStatuses = setOf(
        SManga.UNKNOWN.toLong(),
        SManga.ONGOING.toLong(),
        SManga.LICENSED.toLong(),
        SManga.PUBLISHING_FINISHED.toLong(),
        SManga.CANCELLED.toLong(),
        SManga.ON_HIATUS.toLong(),
    )

    suspend fun subscribe(): Flow<List<Manga>> {
        val libraryPreferences: LibraryPreferences = Injekt.get()

        return mangaRepository.getUpcomingManga(includedStatuses)
            .map { mangaList ->
                mangaList.filter { manga ->
                    val restrictions = libraryPreferences.autoUpdateMangaRestrictions().get()
                    !(MANGA_NON_COMPLETED in restrictions && manga.status.toInt() == SManga.COMPLETED)
                }
            }
    }
}
