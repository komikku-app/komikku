package tachiyomi.data.manga

import logcat.LogPriority
import tachiyomi.core.util.system.logcat
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.manga.model.FavoriteEntry
import tachiyomi.domain.manga.model.FavoriteEntryAlternative
import tachiyomi.domain.manga.repository.FavoritesEntryRepository

class FavoritesEntryRepositoryImpl(
    private val handler: DatabaseHandler,
) : FavoritesEntryRepository {
    override suspend fun deleteAll() {
        handler.await { eh_favoritesQueries.deleteAll() }
    }

    override suspend fun insertAll(favoriteEntries: List<FavoriteEntry>) {
        handler.await(true) {
            favoriteEntries.forEach {
                eh_favoritesQueries.insertEhFavorites(
                    title = it.title,
                    gid = it.gid,
                    token = it.token,
                    category = it.category.toLong(),
                )
            }
        }
    }

    override suspend fun selectAll(): List<FavoriteEntry> {
        return handler.awaitList { eh_favoritesQueries.selectAll(::mapFavoriteEntry) }
    }

    override suspend fun addAlternative(favoriteEntryAlternative: FavoriteEntryAlternative) {
        try {
            handler.await {
                eh_favoritesQueries.addAlternative(
                    otherGid = favoriteEntryAlternative.otherGid,
                    otherToken = favoriteEntryAlternative.otherToken,
                    gid = favoriteEntryAlternative.gid,
                    token = favoriteEntryAlternative.token,
                )
            }
        } catch (e: Exception) {
            logcat(LogPriority.INFO, e)
        }
    }

    private fun mapFavoriteEntry(
        gid: String,
        token: String,
        title: String,
        category: Long,
        otherGid: String?,
        otherToken: String?,
    ): FavoriteEntry {
        return FavoriteEntry(
            gid = gid,
            token = token,
            title = title,
            category = category.toInt(),
            otherGid = otherGid,
            otherToken = otherToken,
        )
    }
}
