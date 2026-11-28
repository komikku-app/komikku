package android.icu.text

import app.komikku.desktop.compat.UnsupportedOnDesktop
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class RuleBasedCollatorTest {

    /** `aquellosquetraducen`'s: "like the browser's localeCompare with { numeric: true, sensitivity: "base" }". */
    private fun nameOrder(): Collator = (Collator.getInstance(Locale.ROOT) as RuleBasedCollator).apply {
        setStrength(Collator.PRIMARY)
        setNumericCollation(true)
    }.freeze()

    @Test
    fun `getInstance returns a RuleBasedCollator, as on Android`() {
        Collator.getInstance().shouldBeInstanceOf<RuleBasedCollator>()
        Collator.getInstance(Locale.ROOT).shouldBeInstanceOf<RuleBasedCollator>()
    }

    @Test
    fun `numeric collation orders runs of digits by their value`() {
        val collator = Collator.getInstance(Locale.ENGLISH) as RuleBasedCollator
        val chapters = listOf("Chapter 10", "Chapter 2", "Chapter 1", "Chapter 1.10", "Chapter 1.5", "Chapter 100")

        chapters.sortedWith(collator) shouldContainExactly
            listOf("Chapter 1", "Chapter 1.10", "Chapter 1.5", "Chapter 10", "Chapter 100", "Chapter 2")
        collator.setNumericCollation(true)
        collator.getNumericCollation() shouldBe true
        chapters.sortedWith(collator) shouldContainExactly
            listOf("Chapter 1", "Chapter 1.5", "Chapter 1.10", "Chapter 2", "Chapter 10", "Chapter 100")
    }

    @Test
    fun `numbers of any length, leading zeros and other scripts' digits`() {
        val collator = (Collator.getInstance(Locale.ROOT) as RuleBasedCollator).apply { setNumericCollation(true) }

        collator.compare("Vol. 02", "Vol. 2") shouldBe 0
        (collator.compare("Vol. 007", "Vol. 10") < 0) shouldBe true
        (collator.compare("123456789012345678901234567890", "99") > 0) shouldBe true
        // Arabic-Indic three sorts between 2 and 10
        listOf("Vol. 10", "Vol. \u0663", "Vol. 2").sortedWith(collator) shouldContainExactly
            listOf("Vol. 2", "Vol. \u0663", "Vol. 10")
        (collator.compare("Vol. 2", "Vol. 2 extra") < 0) shouldBe true
        (collator.compare("2", "a") < 0) shouldBe true
    }

    @Test
    fun `above PRIMARY a later letter difference outranks an earlier case or accent difference, like ICU`() {
        val collator = (Collator.getInstance(Locale.ROOT) as RuleBasedCollator).apply { setNumericCollation(true) }
        collator.getStrength() shouldBe Collator.TERTIARY

        // b > a decides; the a / A case difference of the first run only breaks a tie
        (collator.compare("a1b", "A1a") > 0) shouldBe true
        (collator.compare("é1b", "e1a") > 0) shouldBe true
        (collator.compare("a1a", "A1a") < 0) shouldBe true
        collator.setStrength(Collator.SECONDARY)
        (collator.compare("é1b", "e1a") > 0) shouldBe true
        collator.compare("a1a", "A1a") shouldBe 0
    }

    @Test
    fun `a number compares against other characters at its place like a digit, zeros as 0`() {
        val numeric = (Collator.getInstance(Locale.ROOT) as RuleBasedCollator).apply { setNumericCollation(true) }
        val plain = java.text.Collator.getInstance(Locale.ROOT)

        for (other in listOf(".", " ", "-", "a", ".b", "a0")) {
            Integer.signum(numeric.compare("0", other)) shouldBe Integer.signum(plain.compare("0", other))
            Integer.signum(numeric.compare("x00", "x$other")) shouldBe Integer.signum(plain.compare("x0", "x$other"))
            Integer.signum(numeric.compare("x7y", "x$other")) shouldBe Integer.signum(plain.compare("x7y", "x$other"))
        }
    }

    @Test
    fun `an extension's base-sensitivity numeric order ignores case and accents`() {
        val names = listOf("Tomo 10", "tomo 2", "Tomo 1", "el 20", "Él 3", "Anexo")

        names.sortedWith(nameOrder()) shouldContainExactly listOf("Anexo", "Él 3", "el 20", "Tomo 1", "tomo 2", "Tomo 10")
        nameOrder().compare("ÉL 3", "el 03") shouldBe 0
    }

    @Test
    fun `a frozen collator refuses changes, a thawed copy does not`() {
        val frozen = nameOrder() as RuleBasedCollator

        frozen.isFrozen() shouldBe true
        shouldThrow<UnsupportedOperationException> { frozen.setStrength(Collator.TERTIARY) }
        shouldThrow<UnsupportedOperationException> { frozen.setNumericCollation(false) }
        val thawed = frozen.cloneAsThawed() as RuleBasedCollator
        thawed.isFrozen() shouldBe false
        thawed.getNumericCollation() shouldBe true
        thawed.setNumericCollation(false)
        frozen.getNumericCollation() shouldBe true
        (thawed == frozen) shouldBe false
    }

    @Test
    fun `a frozen collator sorts the same on several threads at once`() {
        val collator = nameOrder()
        val names = (1..500).map { "Capítulo ${(it * 7919) % 500}" }
        val expected = (0 until 500).map { "Capítulo $it" }
        val pool = Executors.newFixedThreadPool(8)
        try {
            pool.invokeAll((1..8).map { Callable { names.sortedWith(collator) } }).forEach { it.get() shouldContainExactly expected }
        } finally {
            pool.shutdown()
        }
    }

    @Test
    fun `case level is not available`() {
        val collator = Collator.getInstance() as RuleBasedCollator

        collator.setCaseLevel(false)
        collator.isCaseLevel() shouldBe false
        shouldThrow<UnsupportedOnDesktop> { collator.setCaseLevel(true) }.needs shouldBe
            "android.icu.text.RuleBasedCollator.setCaseLevel(true)"
    }
}
