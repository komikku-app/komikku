package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupMangaTaste
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.taste.interactor.GetMangaTaste
import tachiyomi.domain.taste.model.MangaRating
import tachiyomi.domain.taste.model.MangaTaste
import tachiyomi.domain.taste.repository.TasteRepository

class TasteRestorerTest {
    private val getManga = mockk<GetManga>()
    private val getMangaTaste = mockk<GetMangaTaste>()
    private val tasteRepository = mockk<TasteRepository>(relaxed = true)
    private val restorer = TasteRestorer(getManga, getMangaTaste, tasteRepository)

    @Test
    fun `restore resolves stable identity before device-local id`() = runTest {
        val manga = manga(id = 42)
        val backup = backup(mangaId = 7, updatedAt = 200)
        coEvery { getManga.await(backup.url, backup.source) } returns manga
        coEvery { getMangaTaste.await(backup.source, backup.url) } returns null

        restorer(listOf(backup)).shouldBeEmpty()

        coVerify(exactly = 0) { getManga.await(backup.mangaId) }
        coVerify {
            tasteRepository.upsertMangaTaste(
                match {
                    it.mangaId == 42L &&
                        it.rating == MangaRating.LOVE.value &&
                        it.createdAt == 100L &&
                        it.updatedAt == 200L
                },
            )
        }
    }

    @Test
    fun `newer local rating wins over backup`() = runTest {
        val manga = manga()
        val backup = backup(updatedAt = 200)
        coEvery { getManga.await(backup.url, backup.source) } returns manga
        coEvery { getMangaTaste.await(backup.source, backup.url) } returns taste(updatedAt = 300)

        restorer(listOf(backup)).shouldBeEmpty()

        coVerify(exactly = 0) { tasteRepository.upsertMangaTaste(any()) }
    }

    @Test
    fun `invalid rating is reported and ignored`() = runTest {
        val backup = backup(rating = 99)

        restorer(listOf(backup)).size shouldBe 1

        coVerify(exactly = 0) { tasteRepository.upsertMangaTaste(any()) }
    }

    @Test
    fun `missing stable identity is reported without creating a manga`() = runTest {
        val backup = backup()
        coEvery { getManga.await(backup.url, backup.source) } returns null
        coEvery { getManga.await(backup.mangaId) } returns manga().copy(url = "/different")

        restorer(listOf(backup)).size shouldBe 1

        coVerify(exactly = 0) { tasteRepository.upsertMangaTaste(any()) }
    }

    private fun backup(
        mangaId: Long = 7,
        rating: Int = MangaRating.LOVE.value,
        updatedAt: Long = 200,
    ) = BackupMangaTaste(
        mangaId = mangaId,
        source = 10,
        url = "/series/example",
        title = "Example",
        rating = rating,
        createdAt = 100,
        updatedAt = updatedAt,
    )

    private fun manga(id: Long = 7) = Manga.create().copy(
        id = id,
        source = 10,
        url = "/series/example",
        ogTitle = "Example",
    )

    private fun taste(updatedAt: Long) = MangaTaste(
        mangaId = 7,
        source = 10,
        url = "/series/example",
        title = "Example",
        rating = MangaRating.LIKE.value,
        createdAt = 100,
        updatedAt = updatedAt,
    )
}
