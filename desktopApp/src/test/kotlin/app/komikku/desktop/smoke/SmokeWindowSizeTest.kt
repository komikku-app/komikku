package app.komikku.desktop.smoke

import app.komikku.desktop.smoke.SmokeWindowSize.Choice
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class SmokeWindowSizeTest {

    @Test
    fun `a screen with room gets the preferred window and no reason`() {
        SmokeWindowSize.choose(1920, 1050) shouldBe Choice.Fits(1280, 800, null)
        SmokeWindowSize.choose(1280, 800) shouldBe Choice.Fits(1280, 800, null)
    }

    @Test
    fun `the macOS runner's screen gets the whole usable area, with the reason`() {
        val choice = SmokeWindowSize.choose(1024, 684).shouldBeInstanceOf<Choice.Fits>()
        choice.width shouldBe 1024
        choice.height shouldBe 684
        choice.toString() shouldBe "1024x684, not 1280x800: the usable screen area is 1024x684"
    }

    @Test
    fun `the aspect stays between 5 to 4 and 16 to 10`() {
        SmokeWindowSize.choose(2560, 700) shouldBe Choice.Fits(1120, 700, "not 1280x800: the usable screen area is 2560x700")
        SmokeWindowSize.choose(1000, 1400) shouldBe Choice.Fits(1000, 800, "not 1280x800: the usable screen area is 1000x1400")
    }

    @Test
    fun `the minimum window is accepted`() {
        SmokeWindowSize.choose(SmokeWindowSize.MIN_WIDTH, SmokeWindowSize.MIN_HEIGHT) shouldBe
            Choice.Fits(960, 600, "not 1280x800: the usable screen area is 960x600")
    }

    @Test
    fun `a screen below the minimum is refused, with the reason`() {
        listOf(959 to 700, 1024 to 599, 800 to 600, 1280 to 590).forEach { (width, height) ->
            SmokeWindowSize.choose(width, height).shouldBeInstanceOf<Choice.TooSmall>().reason shouldContain
                "the usable screen area is ${width}x$height; the smoke window needs at least 960x600"
        }
    }

    @Test
    fun `a session on a screen below the minimum ends refused, with the reason in both reports`(
        @TempDir reportFolder: File,
        @TempDir dataFolder: File,
    ) {
        val choice = SmokeWindowSize.choose(800, 540)
        val session = UiSmokeSession(reportFolder, dataFolder, keepDataFolder = true, trap = NetworkTrap(), exit = { throw Exited(it) })

        shouldThrow<Exited> { session.onInjektReady(choice) }.code shouldBe UiSmoke.EXIT_REFUSED

        val text = File(reportFolder, SmokeReport.TEXT_FILE).readText()
        text shouldContain "window size: refused: the usable screen area is 800x540; the smoke window needs at least 960x600"
        text shouldContain SmokeReport.Outcome.REFUSED.line
        File(reportFolder, SmokeReport.JSON_FILE).readText() shouldContain "the smoke window needs at least 960x600"
    }

    private class Exited(val code: Int) : RuntimeException()
}
