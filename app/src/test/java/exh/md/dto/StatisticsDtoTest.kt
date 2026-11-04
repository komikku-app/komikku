package exh.md.dto

import exh.md.utils.MdUtil
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class StatisticsDtoTest {

    private val json: Json = MdUtil.jsonParser

    @Test
    fun `parses follows and comments from statistics payload`() {
        val mangaId = "10de26cf-f750-44b2-a504-7bb9a53e0419"
        val payload = """
            {
              "result": "ok",
              "statistics": {
                "$mangaId": {
                  "comments": { "threadId": 2575607, "repliesCount": 18 },
                  "follows": 30185,
                  "rating": {
                    "average": 8.9421,
                    "bayesian": 8.882660961907254
                  },
                  "unavailableChaptersCount": 0
                }
              }
            }
        """.trimIndent()

        val dto = json.decodeFromString<StatisticsDto>(payload)
        val stats = dto.statistics.getValue(mangaId)

        assertEquals(30185, stats.follows)
        assertEquals(2575607L, stats.comments?.threadId)
        assertEquals(18, stats.comments?.repliesCount)
        assertEquals(8.882660961907254, stats.rating.bayesian)
    }

    @Test
    fun `parses statistics when comments and follows are absent`() {
        val mangaId = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
        val payload = """
            {
              "statistics": {
                "$mangaId": {
                  "rating": { "average": null, "bayesian": 7.5 }
                }
              }
            }
        """.trimIndent()

        val stats = json.decodeFromString<StatisticsDto>(payload).statistics.getValue(mangaId)

        assertNull(stats.follows)
        assertNull(stats.comments)
        assertEquals(7.5, stats.rating.bayesian)
    }

    @Test
    fun `parses null comments object`() {
        val mangaId = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
        val payload = """
            {
              "statistics": {
                "$mangaId": {
                  "comments": null,
                  "follows": 12,
                  "rating": { "average": 9.0, "bayesian": 8.5 }
                }
              }
            }
        """.trimIndent()

        val stats = json.decodeFromString<StatisticsDto>(payload).statistics.getValue(mangaId)

        assertEquals(12, stats.follows)
        assertNull(stats.comments)
    }
}
