package app.cash.quickjs

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class QuickJsTest {

    @Test
    fun `evaluates expressions to quickjs-android types`() {
        QuickJs.create().use { js ->
            js.evaluate("1 + 2") shouldBe 3
            js.evaluate("1 + 2").shouldBeInstanceOf<Int>()
            js.evaluate("0.5 * 3") shouldBe 1.5
            js.evaluate("2 ** 40") shouldBe 1099511627776.0
            js.evaluate("'Kom' + 'ikku'") shouldBe "Komikku"
            js.evaluate("[1, 2].includes(2)") shouldBe true
            js.evaluate("null") shouldBe null
            js.evaluate("undefined") shouldBe null
            (js.evaluate("['a', 1]") as Array<*>).toList() shouldBe listOf("a", 1)
        }
    }

    @Test
    fun `keeps state between evaluations and runs typical source scripts`() {
        QuickJs.create().use { js ->
            js.evaluate("var key = 'abc'; function decode(s) { return s.split('').reverse().join('') + key; }")
            js.evaluate("decode('olleh')") shouldBe "helloabc"
            js.evaluate("(function(p) { return p.toUpperCase() })('packed')") shouldBe "PACKED"
            js.evaluate("JSON.stringify({a: [1, 2], b: `t${1 + 1}`})") shouldBe """{"a":[1,2],"b":"t2"}"""
        }
    }

    @Test
    fun `compiles and executes bytecode`() {
        QuickJs.create().use { js ->
            val bytecode = js.compile("6 * 7", "answer.js")
            js.execute(bytecode) shouldBe 42
        }
    }

    @Test
    fun `reports script errors as QuickJsException`() {
        QuickJs.create().use { js ->
            val error = assertThrows<QuickJsException> { js.evaluate("throw new Error('boom')") }
            error.message.orEmpty() shouldContain "boom"
            assertThrows<QuickJsException> { js.evaluate("this is not javascript") }
        }
    }
}
