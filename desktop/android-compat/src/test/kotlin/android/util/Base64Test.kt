package android.util

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.random.Random

class Base64Test {

    @Test
    fun `encodes with flags`() {
        val bytes = "hello world?".toByteArray()
        Base64.encodeToString(bytes, Base64.NO_WRAP) shouldBe "aGVsbG8gd29ybGQ/"
        Base64.encodeToString(bytes, Base64.DEFAULT) shouldBe "aGVsbG8gd29ybGQ/\n"
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP) shouldBe "aGVsbG8gd29ybGQ_"
        Base64.encodeToString("a".toByteArray(), Base64.NO_WRAP) shouldBe "YQ=="
        Base64.encodeToString("a".toByteArray(), Base64.NO_WRAP or Base64.NO_PADDING) shouldBe "YQ"
        Base64.encodeToString("ab".toByteArray(), Base64.CRLF) shouldBe "YWI=\r\n"
        Base64.encodeToString(ByteArray(0), Base64.DEFAULT) shouldBe ""
        Base64.encodeToString("xxhello".toByteArray(), 2, 5, Base64.NO_WRAP) shouldBe "aGVsbG8="
    }

    @Test
    fun `wraps lines at 76 characters`() {
        val encoded = Base64.encodeToString(ByteArray(57 * 2 + 1) { it.toByte() }, Base64.DEFAULT)
        val lines = encoded.split("\n")
        lines.map { it.length } shouldBe listOf(76, 76, 4, 0)
        // Exactly one full line: a single trailing newline.
        Base64.encodeToString(ByteArray(57), Base64.DEFAULT).count { it == '\n' } shouldBe 1
    }

    @Test
    fun `round trips and matches the JDK`() {
        val random = Random(42)
        repeat(50) {
            val bytes = random.nextBytes(random.nextInt(0, 300))
            val standard = Base64.encodeToString(bytes, Base64.NO_WRAP)
            standard shouldBe java.util.Base64.getEncoder().encodeToString(bytes)
            Base64.decode(standard, Base64.DEFAULT).toList() shouldBe bytes.toList()
            Base64.decode(Base64.encode(bytes, Base64.DEFAULT), Base64.DEFAULT).toList() shouldBe bytes.toList()
            val urlSafe = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            Base64.decode(urlSafe, Base64.URL_SAFE).toList() shouldBe bytes.toList()
        }
    }

    @Test
    fun `decodes leniently`() {
        String(Base64.decode("aGVs\nbG8g d29y\r\nbGQ/", Base64.DEFAULT)) shouldBe "hello world?"
        String(Base64.decode("YQ", Base64.DEFAULT)) shouldBe "a"
        String(Base64.decode("YWI", Base64.DEFAULT)) shouldBe "ab"
        String(Base64.decode("YQ==\n", Base64.DEFAULT)) shouldBe "a"
        String(Base64.decode("YQ= =", Base64.DEFAULT)) shouldBe "a"
    }

    @Test
    fun `rejects malformed input`() {
        assertThrows<IllegalArgumentException> { Base64.decode("Y", Base64.DEFAULT) }
        assertThrows<IllegalArgumentException> { Base64.decode("=YQ", Base64.DEFAULT) }
        assertThrows<IllegalArgumentException> { Base64.decode("YQ=", Base64.DEFAULT) }
        assertThrows<IllegalArgumentException> { Base64.decode("YQ==YQ", Base64.DEFAULT) }
    }
}
