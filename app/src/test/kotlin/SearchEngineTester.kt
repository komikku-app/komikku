
import exh.search.SearchEngine.Companion.isMatch
import exh.search.SearchEngine.Companion.wildcardToRegex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SearchEngineTester {

    @Test
    fun `escape wildcard patterns`() {
        assertEquals("Solo\\*Leveling",
            wildcardToRegex("Solo*Leveling"))

        assertEquals("Solo\\*Leveling.Arise",
            wildcardToRegex("Solo*Leveling$?Arise"))

        assertEquals("Solo\\*Leveling.*Arise",
            wildcardToRegex("Solo*Leveling$*Arise"))

        assertEquals("Solo\\*Leveling.Arise\\.\\[colored\\]\\.\\(english\\)\\.\\(translated.*\\)",
            wildcardToRegex("Solo*Leveling$?Arise.[colored].(english).(translated$*)"))
    }

    @Test
    fun `match queries`() {
        assertTrue("Solo Leveling: Arise".isMatch("Solo$*Leveling$?$?Arise"))
    }
}
