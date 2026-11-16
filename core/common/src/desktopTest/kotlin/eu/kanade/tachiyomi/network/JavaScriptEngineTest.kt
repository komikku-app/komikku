package eu.kanade.tachiyomi.network

import android.app.Application
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class JavaScriptEngineTest {

    @TempDir
    lateinit var dir: File

    @Test
    fun `evaluates scripts like extensions do`(): Unit = runBlocking {
        val engine = JavaScriptEngine(Application(dir))

        engine.evaluate<Int>("[1, 2, 3].reduce((a, b) => a + b, 0)") shouldBe 6
        engine.evaluate<String>("var s = 'tpircsavaj'; s.split('').reverse().join('')") shouldBe "javascript"
    }
}
