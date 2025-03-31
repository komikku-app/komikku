
import eu.kanade.tachiyomi.data.backup.models.Backup
import exh.favorites.LocalFavoritesStorage
import exh.source.EXH_SOURCE_ID
import io.kotest.inspectors.shouldForAll
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.protobuf.ProtoBuf
import okio.buffer
import okio.gzip
import okio.sink
import okio.source
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.manga.interactor.GetCustomMangaInfo
import tachiyomi.domain.manga.model.CustomMangaInfo
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.CustomMangaRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingletonFactory
import java.io.File

class Tester {

    @Disabled
    @Test
    fun stripBackup() {
        val bytes = File("D:\\Downloads\\pacthiyomi_2023-05-08_13-30.proto (1).gz")
            .inputStream().source().buffer()
            .gzip().buffer()
            .readByteArray()
        val backup = ProtoBuf.decodeFromByteArray(Backup.serializer(), bytes)
        val newBytes = ProtoBuf.encodeToByteArray(
            Backup.serializer(),
            backup.copy(
                backupAnime = backup.backupAnime.filter { it.favorite },
            ),
        )
        File("D:\\Downloads\\pacthiyomi_2023-05-08_13-30 (2).proto.gz").outputStream().sink().gzip().buffer().use {
            it.write(newBytes)
        }
    }

    @Test
    fun localFavoritesStorageTester(): Unit = runBlocking {
        val favorites = listOf(
            Manga.create().copy(
                id = 1,
                favorite = true,
                source = EXH_SOURCE_ID,
                url = "/g/gid/token",
            ),
            // an alias for gid2/token2
            Manga.create().copy(
                id = 3,
                favorite = true,
                source = EXH_SOURCE_ID,
                url = "/g/gid3/token3",
            ),
            // add this one to library
            Manga.create().copy(
                id = 3,
                favorite = true,
                source = EXH_SOURCE_ID,
                url = "/g/gid4/token4",
            ),
        )
        val categories = listOf(
            Category(
                id = 1,
                name = "a",
                order = 1,
                flags = 0,
                // KMK -->
                hidden = false,
                // KMK <--
            ),
        )
        val favoriteEntries = listOf(
            FavoriteEntry(
                gid = "gid",
                token = "token",
                title = "a",
                category = 0,
            ),
            FavoriteEntry(
                gid = "gid2",
                token = "token2",
                title = "a",
                category = 0,
            ),
            // the alias for gid2/token2
            FavoriteEntry(
                gid = "gid2",
                token = "token2",
                title = "a",
                category = 0,
                otherGid = "gid3",
                otherToken = "token3",
            ),
            // removed on remote and local
            FavoriteEntry(
                gid = "gid6",
                token = "token6",
                title = "a",
                category = 0,
            ),
        )

        val getFavorites = mockk<GetFavorites>()
        coEvery { getFavorites.await() } returns favorites

        val getCategories = mockk<GetCategories>()
        coEvery { getCategories.await() } returns categories
        coEvery { getCategories.await(any()) } returns categories

        val getFavoriteEntries = mockk<GetFavoriteEntries>()
        coEvery { getFavoriteEntries.await() } returns favoriteEntries

        val storage = LocalFavoritesStorage(
            getFavorites = getFavorites,
            getCategories = getCategories,
            deleteFavoriteEntries = mockk(),
            getFavoriteEntries = getFavoriteEntries,
            insertFavoriteEntries = mockk(),
        )

        val (added, removed) = storage.getChangedDbEntries()
        added.shouldForAll { it.gid == "gid4" && it.token == "token4" }
        removed.shouldForAll { it.gid == "gid6" && it.token == "token6" }
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun before() {
            Injekt.addSingletonFactory {
                GetCustomMangaInfo(
                    object : CustomMangaRepository {
                        override fun get(mangaId: Long) = null
                        override fun set(mangaInfo: CustomMangaInfo) = Unit
                    },
                )
            }
        }
    }
}
