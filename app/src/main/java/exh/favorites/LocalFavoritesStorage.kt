package exh.favorites

import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.source.online.all.EHentai
import exh.favorites.sql.models.FavoriteEntry
import exh.metadata.metadata.EHentaiSearchMetadata
import exh.source.isEhBasedManga
import uy.kohesive.injekt.injectLazy

class LocalFavoritesStorage {
    private val db: DatabaseHelper by injectLazy()

    fun getChangedDbEntries() = db.getFavoriteMangas()
        .executeAsBlocking()
        .asSequence()
        .loadDbCategories()
        .parseToFavoriteEntries()
        .getChangedEntries()

    fun getChangedRemoteEntries(entries: List<EHentai.ParsedManga>) = entries
        .asSequence()
        .map {
            it.fav to it.manga.apply {
                favorite = true
                date_added = System.currentTimeMillis()
            }
        }
        .parseToFavoriteEntries()
        .getChangedEntries()

    fun snapshotEntries() {
        val dbMangas = db.getFavoriteMangas()
            .executeAsBlocking()
            .asSequence()
            .loadDbCategories()
            .parseToFavoriteEntries()

        // Delete old snapshot
        db.deleteAllFavoriteEntries().executeAsBlocking()

        // Insert new snapshots
        db.insertFavoriteEntries(dbMangas.toList()).executeAsBlocking()
    }

    fun clearSnapshots() {
        db.deleteAllFavoriteEntries().executeAsBlocking()
    }

    private fun Sequence<FavoriteEntry>.getChangedEntries(): ChangeSet {
        val terminated = toList()

        val databaseEntries = db.getFavoriteEntries().executeAsBlocking()

        val added = terminated.filter {
            queryListForEntry(databaseEntries, it) == null
        }

        val removed = databaseEntries
            .filter {
                queryListForEntry(terminated, it) == null
            } /*.map {
                todo see what this does
                realm.copyFromRealm(it)
            }*/

        return ChangeSet(added, removed)
    }

    private fun queryListForEntry(list: List<FavoriteEntry>, entry: FavoriteEntry) =
        list.find {
            it.gid == entry.gid &&
                it.token == entry.token &&
                it.category == entry.category
        }

    private fun Sequence<Manga>.loadDbCategories(): Sequence<Pair<Int, Manga>> {
        val dbCategories = db.getCategories().executeAsBlocking()

        return filter(::validateDbManga).mapNotNull {
            val category = db.getCategoriesForManga(it).executeAsBlocking()

            dbCategories.indexOf(
                category.firstOrNull()
                    ?: return@mapNotNull null
            ) to it
        }
    }

    private fun Sequence<Pair<Int, Manga>>.parseToFavoriteEntries() =
        filter { (_, manga) ->
            validateDbManga(manga)
        }.mapNotNull { (categoryId, manga) ->
            FavoriteEntry(
                title = manga.originalTitle,
                gid = EHentaiSearchMetadata.galleryId(manga.url),
                token = EHentaiSearchMetadata.galleryToken(manga.url),
                category = categoryId
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
    val removed: List<FavoriteEntry>
)
