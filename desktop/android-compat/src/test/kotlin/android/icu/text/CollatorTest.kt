package android.icu.text

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.Locale

class CollatorTest {

    @Test
    fun `orders by the locale's rules, not by code points`() {
        val collator = Collator.getInstance(Locale.ENGLISH)

        listOf("zebra", "Éclair", "apple", "Banana").sortedWith(collator) shouldContainExactly
            listOf("apple", "Banana", "Éclair", "zebra")
        (collator.compare("a", "B") < 0) shouldBe true
    }

    @Test
    fun `strength decides which differences count`() {
        val collator = Collator.getInstance(Locale.FRENCH)

        collator.getStrength() shouldBe Collator.TERTIARY
        collator.equals("Cote", "côte") shouldBe false
        collator.setStrength(Collator.SECONDARY)
        collator.equals("cote", "Cote") shouldBe true
        collator.equals("cote", "côte") shouldBe false
        collator.setStrength(Collator.PRIMARY)
        collator.equals("cote", "Côte") shouldBe true
        collator.getStrength() shouldBe Collator.PRIMARY
    }

    @Test
    fun `a clone keeps its own strength`() {
        val collator = Collator.getInstance(Locale.ENGLISH)
        val clone = collator.clone() as Collator

        clone.setStrength(Collator.PRIMARY)

        collator.getStrength() shouldBe Collator.TERTIARY
        clone.equals("a", "A") shouldBe true
    }
}
