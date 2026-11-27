package android.util

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class LruCacheTest {

    @Test
    fun `evicts the least recently used entries past the maximum size`() {
        val removed = mutableListOf<String>()
        val cache = object : LruCache<String, String>(2) {
            override fun entryRemoved(evicted: Boolean, key: String, oldValue: String, newValue: String?) {
                removed += "$evicted:$key=$oldValue->$newValue"
            }
        }
        cache.put("a", "A") shouldBe null
        cache.put("b", "B")
        cache.get("a") shouldBe "A"
        cache.put("c", "C")

        cache.snapshot().keys.toList() shouldContainExactly listOf("a", "c")
        cache.get("b") shouldBe null
        cache.put("a", "A2") shouldBe "A"
        cache.remove("c") shouldBe "C"
        removed shouldContainExactly listOf("true:b=B->null", "false:a=A->A2", "false:c=C->null")
        cache.size() shouldBe 1
        cache.evictionCount() shouldBe 1
        cache.putCount() shouldBe 4
        cache.toString() shouldBe "LruCache[maxSize=2,hits=1,misses=1,hitRate=50%]"
    }

    @Test
    fun `creates missing values and sizes entries with sizeOf`() {
        val cache = object : LruCache<Int, String>(10) {
            override fun create(key: Int) = "x".repeat(key)
            override fun sizeOf(key: Int, value: String) = value.length
        }
        cache.get(4) shouldBe "xxxx"
        cache.get(5) shouldBe "xxxxx"
        cache.size() shouldBe 9
        cache.get(3) shouldBe "xxx"
        cache.snapshot().keys.toList() shouldContainExactly listOf(5, 3)
        cache.createCount() shouldBe 3
        cache.resize(3)
        cache.snapshot().keys.toList() shouldContainExactly listOf(3)
        cache.evictAll()
        cache.size() shouldBe 0
        assertThrows<IllegalArgumentException> { LruCache<String, String>(0) }
    }
}
