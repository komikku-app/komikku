package tachiyomi.domain.taste.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class MangaRatingTest {
    @Test
    fun `stored values map to their rating`() {
        MangaRating.entries.forEach { rating ->
            MangaRating.fromValue(rating.value) shouldBe rating
        }
    }

    @Test
    fun `unknown stored values do not produce a rating`() {
        MangaRating.fromValue(0) shouldBe null
        MangaRating.fromValue(10) shouldBe null
    }
}
