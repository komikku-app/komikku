package tachiyomi.domain.source.model

import io.kotest.matchers.collections.shouldBeUnique
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SourceTest {

    private val source = Source(id = 1L, lang = "en", name = "Source", supportsLatest = true, isStub = false)

    @Test
    fun `each copy of a source in the sources list has its own key`() {
        val keys = listOf(
            source,
            source.copy(isUsedLast = true),
            // A user's source category may be named like the last used copy's suffix
            source.copy(category = "lastused"),
            source.copy(category = "Favorites"),
        ).map { it.key() }

        keys.shouldBeUnique()
    }

    @Test
    fun `the plain and last used keys are unchanged`() {
        source.key() shouldBe "1"
        source.copy(isUsedLast = true).key() shouldBe "1-lastused"
    }
}
