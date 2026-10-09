package tachiyomi.domain.manga.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.model.MangaRating

class PersonalTasteProfileTest {

    @Test
    fun `love contributes more than like`() {
        val loved = ratedManga(1, "Fantasy", "Action")
        val liked = ratedManga(2, "Fantasy", "Comedy")
        val profile = buildPersonalTasteProfile(
            manga = listOf(loved, liked),
            ratings = mapOf(loved.id to MangaRating.LOVE, liked.id to MangaRating.LIKE),
        )!!

        profile.preferredGenres shouldBe listOf("fantasy", "action", "comedy")
        profile.genreWeights shouldBe mapOf("fantasy" to 3, "action" to 2, "comedy" to 1)
    }

    @Test
    fun `negative choices reduce matching genre scores`() {
        val loved = ratedManga(1, "Fantasy", "Action")
        val ignored = ratedManga(2, "Action")
        val profile = buildPersonalTasteProfile(
            manga = listOf(loved, ignored),
            ratings = mapOf(loved.id to MangaRating.LOVE, ignored.id to MangaRating.NOT_INTERESTED),
        )!!

        profile.genreWeights shouldBe mapOf("fantasy" to 2)
        profile.score(listOf("Fantasy", "Action")) shouldBe 2
    }

    @Test
    fun `negative choices alone do not start recommendations`() {
        val disliked = ratedManga(1, "Fantasy")
        val ignored = ratedManga(2, "Action")
        buildPersonalTasteProfile(
            manga = listOf(disliked, ignored),
            ratings = mapOf(disliked.id to MangaRating.DISLIKE, ignored.id to MangaRating.NOT_INTERESTED),
        ) shouldBe null
    }

    private fun ratedManga(id: Long, vararg genres: String): Manga =
        Manga.create().copy(id = id, ogGenre = genres.toList())
}
