package tachiyomi.core.common.preference

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DesktopPreferenceStoreTest {

    @TempDir
    lateinit var dir: File

    private val file get() = dir.resolve("test.json")

    private fun newStore() = DesktopPreferenceStore(file)

    @Test
    fun `unset preferences return defaults`() {
        val store = newStore()
        val pref = store.getInt("count", 7)

        pref.get() shouldBe 7
        pref.isSet() shouldBe false
        pref.defaultValue() shouldBe 7
        file.exists() shouldBe false
    }

    @Test
    fun `typed values survive a reload`() {
        newStore().apply {
            getString("string").set("hello")
            getInt("int").set(42)
            getLong("long").set(Long.MAX_VALUE)
            getFloat("float").set(1.5f)
            getFloat("nan").set(Float.NaN)
            getBoolean("boolean").set(true)
            getStringSet("set").set(setOf("a", "b", "c"))
        }

        val reloaded = newStore()
        reloaded.getString("string").get() shouldBe "hello"
        reloaded.getInt("int").get() shouldBe 42
        reloaded.getLong("long").get() shouldBe Long.MAX_VALUE
        reloaded.getFloat("float").get() shouldBe 1.5f
        reloaded.getFloat("nan").get().isNaN() shouldBe true
        reloaded.getBoolean("boolean").get() shouldBe true
        reloaded.getStringSet("set").get() shouldBe setOf("a", "b", "c")
    }

    @Test
    fun `getAll keeps value types`() {
        val store = newStore()
        store.getString("s").set("x")
        store.getInt("i").set(1)
        store.getLong("l").set(2L)
        store.getFloat("f").set(3f)
        store.getBoolean("b").set(false)
        store.getStringSet("set").set(setOf("y"))

        val all = newStore().getAll()
        all.shouldContainExactly(
            mapOf(
                "s" to "x",
                "i" to 1,
                "l" to 2L,
                "f" to 3f,
                "b" to false,
                "set" to setOf("y"),
            ),
        )
        all["i"].shouldBeInstanceOf<Int>()
        all["l"].shouldBeInstanceOf<Long>()
    }

    @Test
    fun `delete removes the value from memory and disk`() {
        val store = newStore()
        val pref = store.getString("key", "default")
        pref.set("value")
        pref.isSet() shouldBe true

        pref.delete()

        pref.isSet() shouldBe false
        pref.get() shouldBe "default"
        newStore().getString("key", "default").isSet() shouldBe false
    }

    @Test
    fun `value stored with another type falls back to default and is dropped`() {
        val store = newStore()
        store.getString("key").set("not a number")

        store.getInt("key", 5).get() shouldBe 5
        store.getAll().containsKey("key") shouldBe false
    }

    @Test
    fun `object preferences use their serializers`() {
        val store = newStore()
        store.getEnum("enum", Mode.A).set(Mode.B)
        store.getLongArray("ids", emptyList()).set(listOf(1L, 2L, 3L))
        store.getObjectFromInt("obj", Mode.A, { it.ordinal }, { Mode.entries[it] }).set(Mode.C)

        val reloaded = newStore()
        reloaded.getEnum("enum", Mode.A).get() shouldBe Mode.B
        reloaded.getLongArray("ids", emptyList()).get() shouldBe listOf(1L, 2L, 3L)
        reloaded.getObjectFromInt("obj", Mode.A, { it.ordinal }, { Mode.entries[it] }).get() shouldBe Mode.C
        reloaded.getAll()["obj"] shouldBe Mode.C.ordinal
    }

    @Test
    fun `object preference with an unparsable value returns default`() {
        val store = newStore()
        store.getString("enum").set("UNKNOWN")

        store.getEnum("enum", Mode.A).get() shouldBe Mode.A
    }

    @Test
    fun `changes emits current value then distinct updates`() = runTest {
        val store = newStore()
        val pref = store.getInt("count", 0)
        val emissions = mutableListOf<Int>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            pref.changes().toList(emissions)
        }

        pref.set(1)
        pref.set(1)
        store.getString("other").set("unrelated")
        pref.set(2)
        pref.delete()

        job.cancel()
        emissions.shouldContainExactly(0, 1, 2, 0)
    }

    @Test
    fun `changes reflects edits made through another preference instance`() = runTest {
        val store = newStore()
        store.getBoolean("flag").set(true)

        store.getBoolean("flag").changes().first() shouldBe true
    }

    @Test
    fun `edit applies a batch with one write`() {
        val store = newStore()
        store.edit {
            put("a", 1)
            put("b", "two")
            put("c", setOf("3"))
        }
        store.edit {
            remove("a")
            put("b", "deux")
        }

        newStore().getAll().shouldContainExactly(mapOf("b" to "deux", "c" to setOf("3")))
    }

    @Test
    fun `edit rejects unsupported value types`() {
        val store = newStore()

        assertThrows<IllegalArgumentException> {
            store.edit { put("bad", 1.0) }
        }
        store.getAll().containsKey("bad") shouldBe false
    }

    @Test
    fun `writes are atomic and leave no temporary files`() {
        val store = newStore()
        repeat(20) { store.getInt("i").set(it) }

        dir.listFiles()!!.map { it.name }.shouldContainExactly("test.json")
        file.readText() shouldContain "\"type\": \"int\""
    }

    @Test
    fun `corrupt file is moved aside and the store starts empty`() {
        file.writeText("{ not json")
        dir.resolve("test.json.tmp").writeText("leftover")

        val store = newStore()

        store.getAll().keys.shouldBeEmpty()
        dir.listFiles()!!.count { it.name.startsWith("test.json.corrupt-") } shouldBe 1
        dir.resolve("test.json.tmp").exists() shouldBe false

        store.getInt("i").set(1)
        newStore().getInt("i").get() shouldBe 1
    }

    @Test
    fun `concurrent writes from several threads are all persisted`() {
        val store = newStore()
        val threads = 4
        val writesPerThread = 50
        val executor = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        try {
            val futures = (0..<threads).map { t ->
                executor.submit {
                    start.await()
                    repeat(writesPerThread) { i -> store.getInt("t$t-$i").set(i) }
                }
            }
            start.countDown()
            futures.forEach { it.get(30, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }

        val reloaded = newStore().getAll()
        reloaded.size shouldBe threads * writesPerThread
        reloaded["t3-49"] shouldBe 49
    }

    @Test
    fun `store name is mapped to a safe file name`() {
        DesktopPreferenceStore.fileNameFor("source_123") shouldBe "source_123.json"
        DesktopPreferenceStore.fileNameFor("eu.kanade/../x y") shouldBe "eu.kanade_.._x_y.json"
    }

    private enum class Mode { A, B, C }
}
