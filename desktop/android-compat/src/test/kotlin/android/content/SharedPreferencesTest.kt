package android.content

import android.app.Application
import androidx.preference.PreferenceManager
import app.komikku.desktop.compat.DesktopPreferenceStores
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import tachiyomi.core.common.preference.DesktopPreferenceStore
import java.io.File

class SharedPreferencesTest {

    @TempDir
    lateinit var dir: File

    private val context by lazy { Application(dir) }

    @Test
    fun `stores typed values in one batch`() {
        val prefs = context.getSharedPreferences("source_1", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("s", "value")
            .putInt("i", 1)
            .putLong("l", 2L)
            .putFloat("f", 3.5f)
            .putBoolean("b", true)
            .putStringSet("set", setOf("a", "b"))
            .commit() shouldBe true

        prefs.getString("s", null) shouldBe "value"
        prefs.getInt("i", 0) shouldBe 1
        prefs.getLong("l", 0) shouldBe 2L
        prefs.getFloat("f", 0f) shouldBe 3.5f
        prefs.getBoolean("b", false) shouldBe true
        prefs.getStringSet("set", null) shouldBe setOf("a", "b")
        prefs.getString("missing", "default") shouldBe "default"
        prefs.contains("s") shouldBe true
        prefs.all shouldContainExactly mapOf(
            "s" to "value",
            "i" to 1,
            "l" to 2L,
            "f" to 3.5f,
            "b" to true,
            "set" to setOf("a", "b"),
        )

        // Persisted to the desktop store file and readable by a fresh store.
        val file = dir.resolve("preferences/source_1.json")
        DesktopPreferenceStore(file).getAll()["i"] shouldBe 1
    }

    @Test
    fun `clear applies before puts and null removes`() {
        val prefs = context.getSharedPreferences("p", Context.MODE_PRIVATE)
        prefs.edit().putString("a", "1").putString("b", "2").apply()

        prefs.edit().putString("c", "3").clear().putString("a", null).apply()

        prefs.all shouldContainExactly mapOf("c" to "3")
    }

    @Test
    fun `wrong type throws ClassCastException`() {
        val prefs = context.getSharedPreferences("p", Context.MODE_PRIVATE)
        prefs.edit().putString("k", "text").apply()
        assertThrows<ClassCastException> { prefs.getInt("k", 0) }
    }

    @Test
    fun `listeners see changed keys`() {
        val prefs = context.getSharedPreferences("p", Context.MODE_PRIVATE)
        prefs.edit().putString("same", "x").apply()
        val changed = mutableListOf<String?>()
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> changed += key }
        prefs.registerOnSharedPreferenceChangeListener(listener)

        prefs.edit().putString("same", "x").putInt("n", 1).remove("absent").apply()
        changed shouldContainExactly listOf("n")

        prefs.unregisterOnSharedPreferenceChangeListener(listener)
        prefs.edit().putInt("n", 2).apply()
        changed shouldContainExactly listOf("n")
    }

    @Test
    fun `one store per file, shared with PreferenceStore users`() {
        val prefs = context.getSharedPreferences("shared", Context.MODE_PRIVATE)
        prefs shouldBeSameInstanceAs context.getSharedPreferences("shared", Context.MODE_PRIVATE)

        val store = DesktopPreferenceStores.get("shared", dir.resolve("preferences"))
        store.getInt("n", 0).set(5)
        prefs.getInt("n", 0) shouldBe 5

        val default = PreferenceManager.getDefaultSharedPreferences(context)
        default.edit().putBoolean("x", true).apply()
        dir.resolve("preferences/${DesktopPreferenceStore.DEFAULT_STORE_NAME}.json").isFile shouldBe true
    }
}
