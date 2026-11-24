package app.komikku.desktop.ui.browse

import eu.kanade.tachiyomi.ui.browse.feed.MaxFeedItems
import eu.kanade.tachiyomi.ui.browse.feed.isFeedFull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** The shared feed logic (`FeedItems.kt`, also Android's): the global feed's row limit. */
class FeedItemsTest {

    @Test
    fun `the feed is full once it holds MaxFeedItems rows`() {
        MaxFeedItems shouldBe 20
        isFeedFull(0) shouldBe false
        isFeedFull(19) shouldBe false
        // Was `count > MaxFeedItems`: a 21st row could be added although the message says 20
        isFeedFull(20) shouldBe true
        isFeedFull(21) shouldBe true
    }
}
