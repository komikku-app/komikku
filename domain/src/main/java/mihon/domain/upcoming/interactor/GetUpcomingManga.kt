package mihon.domain.upcoming.interactor

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository
import kotlinx.coroutines.flow.map
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MANGA_HAS_UNREAD
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MANGA_NON_COMPLETED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MANGA_NON_READ
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
        SManga.COMPLETED.toLong(),
    )

    suspend fun subscribe(): Flow<List<Manga>> {
        return mangaRepository.getUpcomingManga(includedStatuses)
    }

    suspend fun subscribeLibrary(): Flow<List<LibraryManga>> {
        val libraryPreferences: LibraryPreferences = Injekt.get()
        val restrictions = libraryPreferences.autoUpdateMangaRestrictions().get()

        return mangaRepository.getUpcomingManga(includedStatuses)
            .map { mangaList ->
                mangaList.filter { manga ->
                    !(MANGA_NON_COMPLETED in restrictions && manga.status.toInt() == SManga.COMPLETED)
                        (MANGA_HAS_UNREAD !in restrictions || libraryManga.unreadCount == 0L) &&
                        (MANGA_NON_READ !in restrictions || libraryManga.totalChapters == 0L || libraryManga.hasStarted)
                }
            }
    }
}
