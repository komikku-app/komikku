package eu.kanade.tachiyomi.ui.reader

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import java.io.File

class ReadingModeToastPolicyTest {

    @Test
    fun `auto webtoon toast is hidden when show reading mode is disabled`() {
        shouldShowAutoWebtoonToast(showReadingMode = false, isAutoWebtoon = true) shouldBe false
    }

    @Test
    fun `auto webtoon toast is shown when show reading mode is enabled`() {
        shouldShowAutoWebtoonToast(showReadingMode = true, isAutoWebtoon = true) shouldBe true
    }

    @Test
    fun `reader activity passes the show reading mode preference to the auto webtoon toast`() {
        // Unit tests run with the module directory (app/) as working directory.
        val source = File("src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt").readText()

        source shouldContain "shouldShowAutoWebtoonToast(readerPreferences.showReadingMode().get(), isAutoWebtoon)"
    }
}
