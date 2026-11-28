package app.komikku.desktop.smoke

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SmokeScriptTextTest {

    @Test
    fun `a text shows on its own or first in a merged node's accessible name`() {
        // The window's names on the details screen of CI run 46143782567 and of a local run after the reader
        val window = listOf("Chapters", "Chapter 2, Jan 1, 2024", "Chapter 1, Jan 1, 2024 • Page: 2", "Resume · Chapter 1")
        SmokeScript.shows(window, "Chapter 2") shouldBe true
        SmokeScript.shows(window, "Chapter 1") shouldBe true
        // The headless test's unmerged texts
        SmokeScript.shows(listOf("Chapter 2", "Jan 1, 2024"), "Chapter 2") shouldBe true
    }

    @Test
    fun `part of another text does not show it`() {
        SmokeScript.shows(listOf("Resume · Chapter 1", "Chapter 10, Jan 1, 2024", "Chapter 1 extra"), "Chapter 1") shouldBe false
        SmokeScript.shows(emptyList(), "Chapter 1") shouldBe false
    }
}
