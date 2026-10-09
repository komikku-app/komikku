package eu.kanade.tachiyomi.data.track.kitsu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The score list, the index -> score mapping and the displayed score have to agree with each other.
 * `BaseTracker.setRemoteScore` looks a picked string up with `getScoreList().indexOf(...)`, so if
 * `displayScore` ever returns something outside the list the lookup yields -1, which maps to 0.0
 * and clears the rating on the user's Kitsu account without any error.
 */
class KitsuScoreTest {

    private val ratingSystems = listOf(Kitsu.SIMPLE, Kitsu.REGULAR, Kitsu.ADVANCED)

    @Test
    fun `every score in the list survives a round trip`() {
        ratingSystems.forEach { ratingSystem ->
            val scoreList = kitsuScoreList(ratingSystem)
            scoreList.forEachIndexed { index, entry ->
                val score = kitsuIndexToScore(ratingSystem, index)
                assertEquals(
                    entry,
                    kitsuDisplayScore(ratingSystem, score),
                    "$ratingSystem index $index did not round trip",
                )
                assertEquals(
                    index,
                    scoreList.indexOf(kitsuDisplayScore(ratingSystem, score)),
                    "$ratingSystem index $index does not resolve back to its own position",
                )
            }
        }
    }

    @Test
    fun `unrated resolves to zero for every rating system`() {
        ratingSystems.forEach { ratingSystem ->
            assertEquals(0.0, kitsuIndexToScore(ratingSystem, 0))
            assertEquals(
                kitsuScoreList(ratingSystem).first(),
                kitsuDisplayScore(ratingSystem, 0.0),
                "$ratingSystem should display an unrated entry as its first list item",
            )
        }
    }

    @Test
    fun `an unknown rating system falls back to advanced`() {
        assertEquals(kitsuScoreList(Kitsu.ADVANCED), kitsuScoreList("something-new"))
        assertEquals(kitsuIndexToScore(Kitsu.ADVANCED, 7), kitsuIndexToScore("something-new", 7))
        assertEquals(kitsuDisplayScore(Kitsu.ADVANCED, 4.5), kitsuDisplayScore("something-new", 4.5))
    }

    @Test
    fun `simple maps to Kitsu's four labels`() {
        assertEquals(listOf("-", "Awful", "Meh", "Good", "Great"), kitsuScoreList(Kitsu.SIMPLE))

        // Stored score is ratingTwenty / 2, and Kitsu writes 2, 8, 14 and 20 for the four labels.
        assertEquals(1.0, kitsuIndexToScore(Kitsu.SIMPLE, 1))
        assertEquals(4.0, kitsuIndexToScore(Kitsu.SIMPLE, 2))
        assertEquals(7.0, kitsuIndexToScore(Kitsu.SIMPLE, 3))
        assertEquals(10.0, kitsuIndexToScore(Kitsu.SIMPLE, 4))
    }

    @Test
    fun `simple buckets scores on Kitsu's thresholds`() {
        // Kitsu buckets ratingTwenty as < 6, < 10, < 16 and the rest.
        assertEquals("Awful", kitsuDisplayScore(Kitsu.SIMPLE, 2.5)) // ratingTwenty 5
        assertEquals("Meh", kitsuDisplayScore(Kitsu.SIMPLE, 3.0)) // ratingTwenty 6
        assertEquals("Meh", kitsuDisplayScore(Kitsu.SIMPLE, 4.5)) // ratingTwenty 9
        assertEquals("Good", kitsuDisplayScore(Kitsu.SIMPLE, 5.0)) // ratingTwenty 10
        assertEquals("Good", kitsuDisplayScore(Kitsu.SIMPLE, 7.5)) // ratingTwenty 15
        assertEquals("Great", kitsuDisplayScore(Kitsu.SIMPLE, 8.0)) // ratingTwenty 16
    }

    @Test
    fun `regular maps to half stars up to five`() {
        assertEquals("0.5 ★", kitsuScoreList(Kitsu.REGULAR)[1])
        assertEquals("5 ★", kitsuScoreList(Kitsu.REGULAR).last())
        assertEquals(11, kitsuScoreList(Kitsu.REGULAR).size)

        // ratingTwenty goes up in steps of 2, so the stored score is a whole number.
        assertEquals(1.0, kitsuIndexToScore(Kitsu.REGULAR, 1))
        assertEquals(10.0, kitsuIndexToScore(Kitsu.REGULAR, 10))
        assertEquals("2.5 ★", kitsuDisplayScore(Kitsu.REGULAR, 5.0))
    }

    @Test
    fun `advanced keeps the existing one to ten scale`() {
        val scoreList = kitsuScoreList(Kitsu.ADVANCED)
        assertEquals(20, scoreList.size)
        assertEquals("0", scoreList.first())
        assertEquals("1", scoreList[1])
        assertEquals("1.5", scoreList[2])
        assertEquals("10", scoreList.last())

        assertEquals(1.0, kitsuIndexToScore(Kitsu.ADVANCED, 1))
        assertEquals(10.0, kitsuIndexToScore(Kitsu.ADVANCED, 19))
        assertEquals("7.5", kitsuDisplayScore(Kitsu.ADVANCED, 7.5))
    }

    @Test
    fun `scores stay within the range Kitsu accepts`() {
        ratingSystems.forEach { ratingSystem ->
            kitsuScoreList(ratingSystem).indices.forEach { index ->
                val score = kitsuIndexToScore(ratingSystem, index)
                // toApiScore sends (score * 2), which Kitsu accepts as ratingTwenty 1..20.
                val ratingTwenty = score * 2
                assertTrue(
                    ratingTwenty >= 0 && ratingTwenty <= 20,
                    "$ratingSystem index $index produced ratingTwenty $ratingTwenty",
                )
                assertEquals(
                    ratingTwenty,
                    ratingTwenty.toInt().toDouble(),
                    "$ratingSystem index $index produced a fractional ratingTwenty",
                )
            }
        }
    }
}
