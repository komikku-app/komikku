package exh.favorites

import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.source.online.all.EHentai
import exh.metadata.metadata.EHentaiSearchMetadata
import exh.source.isEhBasedManga
import io.realm.Realm
import io.realm.RealmConfiguration
import uy.kohesive.injekt.injectLazy

class LocalFavoritesStorage {
    private val db: DatabaseHelper by injectLazy()

    private val realmConfig = RealmConfiguration.Builder()
        .name("fav-sync")
        .deleteRealmIfMigrationNeeded()
        .build()

    fun getRealm(): Realm = Realm.getInstance(realmConfig)

    fun getChangedDbEntries(realm: Realm) =
        getChangedEntries(
            realm,
            parseToFavoriteEntries(
                loadDbCategories(
                    db.getFavoriteMangas()
                        .executeAsBlocking()
                        .asSequence()
                )
            )
        )

    fun getChangedRemoteEntries(realm: Realm, entries: List<EHentai.ParsedManga>) =
        getChangedEntries(
            realm,
            parseToFavoriteEntries(
                entries.asSequence().map {
                    it.fav to it.manga.apply {
                        favorite = true
                        date_added = System.currentTimeMillis()
                    }
                }
            )
        )

    fun snapshotEntries(realm: Realm) {
        val dbMangas = parseToFavoriteEntries(
            loadDbCategories(
                db.getFavoriteMangas()
                    .executeAsBlocking()
                    .asSequence()
            )
        )

        // Delete old snapshot
        realm.delete(FavoriteEntry::class.java)

        // Insert new snapshots
        realm.copyToRealm(dbMangas.toList())
    }

    fun clearSnapshots(realm: Realm) {
        realm.delete(FavoriteEntry::class.java)
    }

    private fun getChangedEntries(realm: Realm, entries: Sequence<FavoriteEntry>): ChangeSet {
        val terminated = entries.toList()

        val added = terminated.filter {
            realm.queryRealmForEntry(it) == null
        }

        val removed = realm.where(FavoriteEntry::class.java)
            .findAll()
            .filter {
                queryListForEntry(terminated, it) == null
            }.map {
                realm.copyFromRealm(it)
            }

        return ChangeSet(added, removed)
    }

    private fun Realm.queryRealmForEntry(entry: FavoriteEntry) =
        where(FavoriteEntry::class.java)
            .equalTo(FavoriteEntry::gid.name, entry.gid)
            .equalTo(FavoriteEntry::token.name, entry.token)
            .equalTo(FavoriteEntry::category.name, entry.category)
            .findFirst()

    private fun queryListForEntry(list: List<FavoriteEntry>, entry: FavoriteEntry) =
        list.find {
            it.gid == entry.gid &&
                it.token == entry.token &&
                it.category == entry.category
        }

    private fun loadDbCategories(manga: Sequence<Manga>): Sequence<Pair<Int, Manga>> {
        val dbCategories = db.getCategories().executeAsBlocking()

        return manga.filter(this::validateDbManga).mapNotNull {
            val category = db.getCategoriesForManga(it).executeAsBlocking()

            dbCategories.indexOf(
                category.firstOrNull()
                    ?: return@mapNotNull null
            ) to it
        }
    }

    private fun parseToFavoriteEntries(manga: Sequence<Pair<Int, Manga>>) =
        manga.filter {
            validateDbManga(it.second)
        }.mapNotNull {
            FavoriteEntry().apply {
                title = it.second.originalTitle
                gid = EHentaiSearchMetadata.galleryId(it.second.url)
                token = EHentaiSearchMetadata.galleryToken(it.second.url)
                category = it.first

                if (this.category > MAX_CATEGORIES) {
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
