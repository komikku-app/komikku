package app.cash.quickjs

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Parity of the quickjs-kt-backed `QuickJs` with quickjs-android (docs/kmp-desktop/PROGRESS.md, "QuickJS parity"). */
class QuickJsTest {

    @Test
    fun `evaluates expressions to quickjs-android types`() {
        QuickJs.create().use { js ->
            js.evaluate("1 + 2") shouldBe 3
            js.evaluate("1 + 2").shouldBeInstanceOf<Int>()
            js.evaluate("-5") shouldBe -5
            js.evaluate("1.123") shouldBe 1.123
            js.evaluate("2 ** 40") shouldBe 1099511627776.0
            js.evaluate("'Kom' + 'ikku'") shouldBe "Komikku"
            js.evaluate("[1, 2].includes(2)") shouldBe true
            js.evaluate("false") shouldBe false
            js.evaluate("null").shouldBeNull()
            js.evaluate("undefined").shouldBeNull()
            (js.evaluate("NaN") as Double).isNaN() shouldBe true
            js.evaluate("'héllo 😀'") shouldBe "héllo 😀"
            (js.evaluate("['a', 1, 1.5, null, true, [2]]") as Array<*>).let { array ->
                array.toList().take(5) shouldBe listOf("a", 1, 1.5, null, true)
                (array[5] as Array<*>).toList() shouldBe listOf(2)
            }
        }
    }

    @Test
    fun `returns null for objects, functions and promises like quickjs-android`() {
        QuickJs.create().use { js ->
            js.evaluate("({ a: 1 })").shouldBeNull()
            js.evaluate("(function () {})").shouldBeNull()
            js.evaluate("new Map([[1, 2]])").shouldBeNull()
            js.evaluate("new Error('x')").shouldBeNull()
            js.evaluate("Promise.resolve(1)").shouldBeNull()
            js.evaluate("Symbol('s')").shouldBeNull()
        }
    }

    @Test
    fun `rejects objects nested in arrays like quickjs-android`() {
        QuickJs.create().use { js ->
            val error = assertThrows<QuickJsException> { js.evaluate("[1, { a: 1 }]") }
            error.message shouldBe "Cannot marshal value [object Object] to Java"
        }
    }

    @Test
    fun `keeps state between evaluations and runs typical source scripts`() {
        QuickJs.create().use { js ->
            js.evaluate("var key = 'abc'; function decode(s) { return s.split('').reverse().join('') + key; }")
            js.evaluate("decode('olleh')") shouldBe "helloabc"
            js.evaluate("let counter = 1; const step = 2;")
            js.evaluate("counter += step; counter") shouldBe 3
            js.evaluate("(function(p) { return p.toUpperCase() })('packed')") shouldBe "PACKED"
            js.evaluate("JSON.stringify({a: [1, 2], b: `t${1 + 1}`})") shouldBe """{"a":[1,2],"b":"t2"}"""
            js.evaluate("eval('1 + 1')") shouldBe 2
        }
    }

    @Test
    fun `compiles and executes bytecode, also in another instance`() {
        val bytecode = QuickJs.create().use { it.compile("function answer() { return 6 * 7 }", "answer.js") }
        QuickJs.create().use { js ->
            js.execute(bytecode).shouldBeNull()
            js.evaluate("answer()") shouldBe 42
            js.execute(js.compile("answer() + 1", "?")) shouldBe 43
        }
    }

    @Test
    fun `reports script errors as QuickJsException with the message and the JavaScript stack`() {
        QuickJs.create().use { js ->
            val error = assertThrows<QuickJsException> { js.evaluate("throw new Error('boom')") }
            error.message shouldBe "boom"
            assertThrows<QuickJsException> { js.evaluate("nope();") }.message shouldContain "nope"
            assertThrows<QuickJsException> { js.evaluate("throw 2") }.message shouldBe "2"
            assertThrows<QuickJsException> { js.evaluate("throw 'text: with colon'") }.message shouldBe
                "text: with colon"
            assertThrows<QuickJsException> { js.evaluate("this is not javascript") }
            assertThrows<QuickJsException> { js.compile("function (", "bad.js") }

            val stacked = assertThrows<QuickJsException> {
                js.evaluate(
                    """
                    function f1() {
                      f2();
                    }

                    function f2() {
                      nope();
                    }

                    f1();
                    """.trimIndent(),
                    "test.js",
                )
            }
            val frames = stacked.stackTrace.map { it.toString() }
            frames[0] shouldStartWith "JavaScript.f2(test.js:6"
            frames[1] shouldStartWith "JavaScript.f1(test.js:2"
            frames[2] shouldStartWith "JavaScript.<eval>(test.js:9"
            frames[3] shouldStartWith "app.cash.quickjs.QuickJs.evaluate"
        }
    }

    @Test
    fun `turns deep recursion into a JavaScript error`() {
        QuickJs.create().use { js ->
            val error = assertThrows<QuickJsException> { js.evaluate("function f(n) { return f(n + 1) + 1 } f(0)") }
            error.message.orEmpty() shouldContain "stack"
            js.evaluate("1 + 1") shouldBe 2
            // Deeper than quickjs-android's 256 KiB limit allowed.
            js.evaluate("function g(n) { return n === 0 ? 0 : g(n - 1) + 1 } g(900)") shouldBe 900
        }
    }

    @Test
    fun `serializes calls from several threads`() {
        QuickJs.create().use { js ->
            js.evaluate("var total = 0; function add(n) { total += n; return total; }")
            val pool = Executors.newFixedThreadPool(4)
            try {
                pool.invokeAll(List(40) { Callable { js.evaluate("add(1)") } }).forEach { it.get() }
            } finally {
                pool.shutdown()
                pool.awaitTermination(10, TimeUnit.SECONDS)
            }
            js.evaluate("total") shouldBe 40
        }
    }

    @Test
    fun `throws the old NullPointerException after close`() {
        val js = QuickJs.create()
        js.close()
        js.close()
        val error = assertThrows<NullPointerException> { js.evaluate("1") }
        error.message shouldBe "Null QuickJs context - did you close your QuickJs?"
        assertThrows<NullPointerException> { js.compile("1", "?") }
    }

    @Test
    fun `binds Java interfaces with set`() {
        QuickJs.create().use { js ->
            val calls = mutableListOf<String>()
            js.set(
                "host",
                Host::class.java,
                object : Host {
                    override fun greet(name: String?): String = "hello $name"
                    override fun sum(a: Int, b: Double): Double = a + b
                    override fun flags(values: BooleanArray?): Int = values?.count { it } ?: -1
                    override fun join(separator: String?, vararg parts: String?): String =
                        parts.joinToString(separator.orEmpty())
                    override fun echo(value: Any?): Any? = value
                    override fun log(message: String?) {
                        calls += message.orEmpty()
                    }
                    override fun fail(): Boolean = throw IllegalStateException("from Java")
                },
            )
            js.evaluate("host.greet('js')") shouldBe "hello js"
            js.evaluate("host.greet(null)") shouldBe "hello null"
            js.evaluate("host.sum(2, 0.5)") shouldBe 2.5
            js.evaluate("host.flags([true, false, true])") shouldBe 2
            js.evaluate("host.join('-', 'a', 'b', 'c')") shouldBe "a-b-c"
            js.evaluate("host.join('-')") shouldBe ""
            (js.evaluate("host.echo([1, 'x', [true]])") as Array<*>).let {
                it[0] shouldBe 1
                it[1] shouldBe "x"
            }
            js.evaluate("host.echo(1.5)") shouldBe 1.5
            js.evaluate("host.log('a'); host.log('b')").shouldBeNull()
            calls shouldBe listOf("a", "b")
            js.evaluate("typeof host.greet + host.greet.length + host.greet.name") shouldBe "function1greet"

            assertThrows<IllegalStateException> { js.evaluate("host.fail()") }.message shouldBe "from Java"
            js.evaluate("try { host.fail(); 'no' } catch (e) { e.message }") shouldBe "Java Exception"
            assertThrows<QuickJsException> { js.evaluate("host.greet()") }.message shouldBe
                "Wrong number of arguments"
            assertThrows<IllegalArgumentException> { js.evaluate("host.greet(1)") }.message shouldBe
                "Cannot convert value 1 to String"
            assertThrows<IllegalArgumentException> { js.evaluate("host.sum(1.5, 1)") }.message shouldBe
                "Cannot convert value 1.5 to int"
            assertThrows<QuickJsException> { js.evaluate("host.echo({})") }.message shouldBe
                "Cannot marshal value [object Object] to Java"

            assertThrows<IllegalArgumentException> { js.set("host", Reenter::class.java, Reenter { "" }) }
                .message shouldBe "A global object called host already exists"
            assertThrows<UnsupportedOperationException> { js.set("x", String::class.java, "") }
            assertThrows<UnsupportedOperationException> { js.set("x", Overloaded::class.java, Overloaded {}) }
            assertThrows<IllegalArgumentException> { js.set("x", Unsupported::class.java, Unsupported { it }) }
                .message shouldBe "Unsupported Java type long"
        }
    }

    @Test
    fun `reports re-entrant evaluation from a binding instead of deadlocking`() {
        QuickJs.create().use { js ->
            js.set("reenter", Reenter::class.java, Reenter { js.evaluate("1").toString() })
            assertThrows<UnsupportedOperationException> { js.evaluate("reenter.call()") }
            js.evaluate("2") shouldBe 2
        }
    }

    @Test
    fun `calls JavaScript objects through get`() {
        QuickJs.create().use { js ->
            js.evaluate(
                """
                var calc = {
                  base: 10,
                  add: function (a, b) { return this.base + a + b; },
                  name: function (s) { return 'n:' + s; },
                  list: function () { return ['a', 'b']; },
                  ratio: function () { return '0.25'; },
                  obj: function () { return {}; },
                  fail: function () { throw new TypeError('bad'); },
                };
                """.trimIndent(),
            )
            val calc = js.get("calc", Calc::class.java)
            calc.add(1, 2.5) shouldBe 13.5
            calc.name("x") shouldBe "n:x"
            calc.name(null) shouldBe "n:null"
            calc.list().toList() shouldBe listOf("a", "b")
            calc.ratio() shouldBe 0.25
            assertThrows<QuickJsException> { calc.obj() }.message shouldBe "Cannot marshal value [object Object] to Java"
            assertThrows<QuickJsException> { calc.fail() }.message shouldBe "bad"
            calc.toString() shouldBe "QuickJsProxy{name=calc, type=${Calc::class.java.name}}"

            js.evaluate("calc.base = 0; 1")
            calc.add(1, 1.0) shouldBe 2.0

            assertThrows<IllegalArgumentException> { js.get("missing", Calc::class.java) }.message shouldBe
                "A global JavaScript object called missing was not found"
            js.evaluate("var partial = { add: function () { return 1; } }; var notObject = 1;")
            assertThrows<QuickJsException> { js.get("partial", Calc::class.java) }.message.orEmpty() shouldContain
                "JavaScript global partial has no method called"
            assertThrows<IllegalArgumentException> { js.get("notObject", Calc::class.java) }
        }
    }

    @Test
    fun `runs scripts the way extensions use them`() {
        QuickJs.create().use { js ->
            // Mangahere / Doodmanga: an eval packer returning the unpacked text.
            val packed = "eval(function(p,a,c,k,e,d){return p.replace('X', k[0])}('var url=\"X\";',0,0,['https://a']))"
            js.evaluate(packed.removePrefix("eval")) shouldBe "var url=\"https://a\";"
            // Kuaikanmanhua: globals across evaluations and JSON out.
            js.evaluate("var window = {};")
            js.evaluate("window.__NUXT__ = (function (a) { return { data: [a] } })(1)")
            js.evaluate("JSON.stringify(window.__NUXT__)") shouldBe """{"data":[1]}"""
            // Comicabc: an array of URLs.
            (js.evaluate("var urls = []; for (var i = 0; i < 3; i++) urls.push('u' + i); urls") as Array<*>)
                .toList() shouldContain "u2"
        }
    }

    interface Host {
        fun greet(name: String?): String
        fun sum(a: Int, b: Double): Double
        fun flags(values: BooleanArray?): Int
        fun join(separator: String?, vararg parts: String?): String
        fun echo(value: Any?): Any?
        fun log(message: String?)
        fun fail(): Boolean
    }

    fun interface Overloaded {
        fun call()
        fun call(value: Int) = Unit
    }

    fun interface Unsupported {
        fun call(value: Long): Long
    }

    fun interface Reenter {
        fun call(): String
    }

    interface Calc {
        fun add(a: Int, b: Double): Double
        fun name(value: String?): String?
        fun list(): Array<String?>
        fun ratio(): Double
        fun obj(): Any?
        fun fail(): Any?
    }
}
