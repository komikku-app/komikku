package eu.kanade.tachiyomi.ui.reader

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class ReaderRecordingPolicyTest {

    @Test
    fun `incognito still saves progress`() {
        val decision = ReaderRecordingPolicy.decidePage(
            incognitoMode = true,
            isErrorPage = false,
            isLastPage = false,
        )

        decision.saveProgress shouldBe true
        decision.markRead shouldBe false
    }

    @Test
    fun `incognito still marks the chapter read on the last page`() {
        val decision = ReaderRecordingPolicy.decidePage(
            incognitoMode = true,
            isErrorPage = false,
            isLastPage = true,
        )

        decision.saveProgress shouldBe true
        decision.markRead shouldBe true
    }

    @Test
    fun `incognito skips completion side effects and sync`() {
        val decision = ReaderRecordingPolicy.decidePage(
            incognitoMode = true,
            isErrorPage = false,
            isLastPage = true,
        )

        decision.runCompletionSideEffects shouldBe false
        decision.triggerSync shouldBe false
    }

    @Test
    fun `normal mode runs completion side effects and sync on the last page`() {
        val decision = ReaderRecordingPolicy.decidePage(
            incognitoMode = false,
            isErrorPage = false,
            isLastPage = true,
        )

        decision.saveProgress shouldBe true
        decision.markRead shouldBe true
        decision.runCompletionSideEffects shouldBe true
        decision.triggerSync shouldBe true
    }

    @Test
    fun `non-last normal pages only save progress and allow open sync`() {
        val decision = ReaderRecordingPolicy.decidePage(
            incognitoMode = false,
            isErrorPage = false,
            isLastPage = false,
        )

        decision.saveProgress shouldBe true
        decision.markRead shouldBe false
        decision.runCompletionSideEffects shouldBe false
        decision.triggerSync shouldBe true
    }

    @Test
    fun `error pages record nothing`() {
        for (incognitoMode in listOf(false, true)) {
            for (isLastPage in listOf(false, true)) {
                val decision = ReaderRecordingPolicy.decidePage(
                    incognitoMode = incognitoMode,
                    isErrorPage = true,
                    isLastPage = isLastPage,
                )

                decision.saveProgress shouldBe false
                decision.markRead shouldBe false
                decision.runCompletionSideEffects shouldBe false
                decision.triggerSync shouldBe false
            }
        }
    }

    @Test
    fun `incognito does not record history`() {
        ReaderRecordingPolicy.recordsHistory(incognitoMode = true) shouldBe false
        ReaderRecordingPolicy.recordsHistory(incognitoMode = false) shouldBe true
    }
}
