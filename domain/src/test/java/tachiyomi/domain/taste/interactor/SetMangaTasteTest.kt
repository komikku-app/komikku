package tachiyomi.domain.taste.interactor

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.model.MangaRating
import tachiyomi.domain.taste.model.MangaTaste
import tachiyomi.domain.taste.repository.TasteRepository

class SetMangaTasteTest {

    private val repository = mockk<TasteRepository>()

    @Test
    fun `changing a rating preserves its creation time`() = runTest {
        val existing = MangaTaste(
            mangaId = 4,
            source = 8,
            url = "/manga/example",
            title = "Example",
            rating = MangaRating.LIKE.value,
            createdAt = 100,
            updatedAt = 200,
        )
        coEvery { repository.getMangaTaste(existing.source, existing.url) } returns existing
        coEvery { repository.upsertMangaTaste(any()) } returns Unit

        SetMangaTaste(repository, now = { 300 }).await(
            mangaId = existing.mangaId,
            source = existing.source,
            url = existing.url,
            title = existing.title,
            rating = MangaRating.LOVE,
        )

        coVerify {
            repository.upsertMangaTaste(
                existing.copy(
                    rating = MangaRating.LOVE.value,
                    createdAt = 100,
                    updatedAt = 300,
                ),
            )
        }
    }
}
