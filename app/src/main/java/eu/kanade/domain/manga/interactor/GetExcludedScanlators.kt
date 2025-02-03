package eu.kanade.domain.manga.interactor

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.data.DatabaseHandler

class GetExcludedScanlators(
    private val handler: DatabaseHandler,
) {

    suspend fun await(mangaId: Long): Set<String> {
        return handler.awaitList {
            excluded_scanlatorsQueries.getExcludedScanlatorsByMangaId(mangaId)
        }
            .toSet()
    }

    suspend fun await(mangaIds: List<Long>): Map<Long, Set<String>> {
        return handler.awaitList {
            excluded_scanlatorsQueries.getExcludedScanlatorsByMangaIds(mangaIds)
        }
            .groupBy { it.manga_id }
            .mapValues { set -> set.value.map { it.scanlator }.toSet() }
    }

    fun subscribe(mangaId: Long): Flow<Set<String>> {
        return handler.subscribeToList {
            excluded_scanlatorsQueries.getExcludedScanlatorsByMangaId(mangaId)
        }
            .map { it.toSet() }
    }
}
