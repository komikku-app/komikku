package app.komikku.desktop.reader

import androidx.compose.ui.input.key.Key
import app.komikku.desktop.ui.reader.ReaderAction
import app.komikku.desktop.ui.reader.ReaderKeyMap
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** 7.5: keyboard map and click zones respect the reading direction. */
class ReaderKeyMapTest {

    @Test
    fun `arrows follow the reading direction`() {
        ReaderKeyMap.actionFor(Key.DirectionRight, false, ReadingMode.LEFT_TO_RIGHT) shouldBe ReaderAction.Next
        ReaderKeyMap.actionFor(Key.DirectionRight, false, ReadingMode.RIGHT_TO_LEFT) shouldBe ReaderAction.Previous
        ReaderKeyMap.actionFor(Key.DirectionLeft, false, ReadingMode.RIGHT_TO_LEFT) shouldBe ReaderAction.Next
        ReaderKeyMap.actionFor(Key.DirectionDown, false, ReadingMode.VERTICAL) shouldBe ReaderAction.Next
        ReaderKeyMap.actionFor(Key.DirectionDown, false, ReadingMode.WEBTOON) shouldBe ReaderAction.ScrollDown
        ReaderKeyMap.actionFor(Key.DirectionRight, false, ReadingMode.WEBTOON) shouldBe null
    }

    @Test
    fun `paging, chapter, zoom and window keys`() {
        ReaderKeyMap.actionFor(Key.Spacebar, false, ReadingMode.LEFT_TO_RIGHT) shouldBe ReaderAction.Next
        ReaderKeyMap.actionFor(Key.Spacebar, true, ReadingMode.LEFT_TO_RIGHT) shouldBe ReaderAction.Previous
        ReaderKeyMap.actionFor(Key.Spacebar, true, ReadingMode.CONTINUOUS_VERTICAL) shouldBe ReaderAction.PageUp
        ReaderKeyMap.actionFor(Key.PageDown, false, ReadingMode.RIGHT_TO_LEFT) shouldBe ReaderAction.Next
        ReaderKeyMap.actionFor(Key.MoveHome, false, ReadingMode.RIGHT_TO_LEFT) shouldBe ReaderAction.First
        ReaderKeyMap.actionFor(Key.MoveEnd, false, ReadingMode.WEBTOON) shouldBe ReaderAction.Last
        ReaderKeyMap.actionFor(Key.N, false, ReadingMode.WEBTOON) shouldBe ReaderAction.NextChapter
        ReaderKeyMap.actionFor(Key.P, false, ReadingMode.WEBTOON) shouldBe ReaderAction.PreviousChapter
        ReaderKeyMap.actionFor(Key.Plus, false, ReadingMode.WEBTOON) shouldBe ReaderAction.ZoomIn
        ReaderKeyMap.actionFor(Key.Minus, false, ReadingMode.WEBTOON) shouldBe ReaderAction.ZoomOut
        ReaderKeyMap.actionFor(Key.Zero, false, ReadingMode.WEBTOON) shouldBe ReaderAction.ZoomReset
        ReaderKeyMap.actionFor(Key.F11, false, ReadingMode.WEBTOON) shouldBe ReaderAction.ToggleFullscreen
        ReaderKeyMap.actionFor(Key.F, false, ReadingMode.WEBTOON) shouldBe ReaderAction.ToggleFullscreen
        ReaderKeyMap.actionFor(Key.Escape, false, ReadingMode.WEBTOON) shouldBe ReaderAction.Back
        ReaderKeyMap.actionFor(Key.Q, false, ReadingMode.WEBTOON) shouldBe null
    }

    @Test
    fun `click zones`() {
        ReaderKeyMap.clickAction(0.1f, 0.5f, ReadingMode.LEFT_TO_RIGHT) shouldBe ReaderAction.Previous
        ReaderKeyMap.clickAction(0.9f, 0.5f, ReadingMode.LEFT_TO_RIGHT) shouldBe ReaderAction.Next
        ReaderKeyMap.clickAction(0.1f, 0.5f, ReadingMode.RIGHT_TO_LEFT) shouldBe ReaderAction.Next
        ReaderKeyMap.clickAction(0.5f, 0.5f, ReadingMode.RIGHT_TO_LEFT) shouldBe ReaderAction.ToggleMenu
        ReaderKeyMap.clickAction(0.1f, 0.9f, ReadingMode.VERTICAL) shouldBe ReaderAction.Next
        ReaderKeyMap.clickAction(0.9f, 0.1f, ReadingMode.VERTICAL) shouldBe ReaderAction.Previous
    }
}
