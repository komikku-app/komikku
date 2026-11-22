package eu.kanade.tachiyomi.ui.reader

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ReadingModeToastPolicyTest {

    // KMK -->
    @Test
    fun `auto webtoon toast is hidden when show reading mode is disabled`() {
        shouldShowAutoWebtoonToast(showReadingMode = false, isAutoWebtoon = true) shouldBe false
    }

    @Test
    fun `auto webtoon toast is shown when show reading mode is enabled`() {
        shouldShowAutoWebtoonToast(showReadingMode = true, isAutoWebtoon = true) shouldBe true
    }

    @Test
    fun `auto webtoon toast is hidden for non-webtoon chapters`() {
        shouldShowAutoWebtoonToast(showReadingMode = true, isAutoWebtoon = false) shouldBe false
    }

    @Test
    fun `auto webtoon toast callback only runs when policy allows it`() {
        var toastShown = false

        val didShowToast = showAutoWebtoonToastIfNeeded(
            showReadingMode = false,
            isAutoWebtoon = true,
        ) {
            toastShown = true
        }

        didShowToast shouldBe false
        toastShown shouldBe false
    }
    // KMK <--
}
