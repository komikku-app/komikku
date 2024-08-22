package exh.favorites

import eu.kanade.domain.manga.model.toDomainManga
import eu.kanade.tachiyomi.source.online.all.EHentai
import exh.metadata.metadata.EHentaiSearchMetadata
import exh.source.EXH_SOURCE_ID
import exh.source.isEhBasedManga
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.manga.interactor.DeleteFavoriteEntries
import tachiyomi.domain.manga.interactor.GetFavoriteEntries
import tachiyomi.domain.manga.interactor.GetFavorites
import tachiyomi.domain.manga.interactor.InsertFavoriteEntries
import tachiyomi.domain.manga.model.FavoriteEntry
import tachiyomi.domain.manga.model.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class LocalFavoritesStorage(
    private val getFavorites: GetFavorites = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val deleteFavoriteEntries: DeleteFavoriteEntries = Injekt.get(),
    private val getFavoriteEntries: GetFavoriteEntries = Injekt.get(),
    private val insertFavoriteEntries: InsertFavoriteEntries = Injekt.get(),
) {

    suspend fun getChangedDbEntries() = getFavorites.await()
        .asFlow()
        .loadDbCategories()
        .parseToFavoriteEntries()
        .getChangedEntries()

    suspend fun getChangedRemoteEntries(entries: List<EHentai.ParsedManga>) = entries
        .asFlow()
        .map {
            it.fav to it.manga.toDomainManga(EXH_SOURCE_ID).copy(
                favorite = true,
                dateAdded = System.currentTimeMillis(),
            )
        }
        .parseToFavoriteEntries()
        .getChangedEntries()

    suspend fun snapshotEntries() {
        val dbMangas = getFavorites.await()
            .asFlow()
            .loadDbCategories()
            .parseToFavoriteEntries()

        // Delete old snapshot
        deleteFavoriteEntries.await()

        // Insert new snapshots
        insertFavoriteEntries.await(dbMangas.toList())
    }

    suspend fun clearSnapshots() {
        deleteFavoriteEntries.await()
    }

    private suspend fun Flow<FavoriteEntry>.getChangedEntries(): ChangeSet {
        val terminated = toList()

        val databaseEntries = getFavoriteEntries.await()

        val added = terminated.groupBy { it.gid to it.token }
            .filter { (_, values) ->
                values.all { queryListForEntry(databaseEntries, it) == null }
            }
            .map { it.value.first() }

        val removed = databaseEntries
            .groupBy { it.gid to it.token }
            .filter { (_, values) ->
                values.all { queryListForEntry(terminated, it) == null }
            }
            .map { it.value.first() }

        return ChangeSet(added, removed)
    }

    private fun FavoriteEntry.urlEquals(other: FavoriteEntry) = (gid == other.gid && token == other.token) ||
        (otherGid != null && otherToken != null && (otherGid == other.gid && otherToken == other.token)) ||
        (other.otherGid != null && other.otherToken != null && (gid == other.otherGid && token == other.otherToken)) ||
        (
            otherGid != null &&
                otherToken != null &&
                other.otherGid != null &&
                other.otherToken != null &&
                otherGid == other.otherGid &&
                otherToken == other.otherToken
            )

    private fun queryListForEntry(list: List<FavoriteEntry>, entry: FavoriteEntry) =
        list.find { it.urlEquals(entry) && it.category == entry.category }

    private suspend fun Flow<Manga>.loadDbCategories(): Flow<Pair<Int, Manga>> {
        val dbCategories = getCategories.await()
            .filterNot(Category::isSystemCategory)

        return filter(::validateDbManga).mapNotNull {
            val category = getCategories.await(it.id)

            dbCategories.indexOf(
                category.firstOrNull()
                    ?: return@mapNotNull null,
            ) to it
        }
    }

    private fun Flow<Pair<Int, Manga>>.parseToFavoriteEntries() =
        filter { (_, manga) ->
            validateDbManga(manga)
        }.mapNotNull { (categoryId, manga) ->
            FavoriteEntry(
                title = manga.ogTitle,
                gid = EHentaiSearchMetadata.galleryId(manga.url),
                token = EHentaiSearchMetadata.galleryToken(manga.url),
                category = categoryId,
            ).also {
                if (it.category > MAX_CATEGORIES) {
                    return@mapNotNull null
                }
            }
        }

    private fun validateDbManga(manga: Manga) =
        manga.favorite && manga.isEhBasedManga()

    companion object {
        const val MAX_CATEGORIES = 9
    }
}

data class ChangeSet(
    val added: List<FavoriteEntry>,
    val removed: List<FavoriteEntry>,
)
