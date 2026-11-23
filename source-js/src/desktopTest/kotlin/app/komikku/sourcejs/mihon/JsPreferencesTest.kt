package app.komikku.sourcejs.mihon

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import app.komikku.sourcejs.bridge.JsPreferenceValue
import app.komikku.sourcejs.model.JsPreference
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** Preferences of the adapter on the desktop compat model (`androidx.preference` / `SharedPreferences`). */
class JsPreferencesTest {

    private val dir: File = Files.createTempDirectory("js-prefs").toFile()
    private val context = Application(dir)
    private val prefs: SharedPreferences = context.getSharedPreferences("source_1", Context.MODE_PRIVATE)

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private val declared = listOf(
        JsPreference.CheckBox("check", title = "Check", summary = "A check box", value = true),
        JsPreference.Switch("switch", title = "Switch"),
        JsPreference.List("list", title = "List", entries = listOf("One", "Two", "Three"), entryValues = listOf("1", "2"), valueIndex = 1),
        JsPreference.MultiSelect("multi", title = "Multi", entries = listOf("Red", "Green", "Blue"), entryValues = listOf("r", "g", "b"), values = listOf("b", "r")),
        JsPreference.EditText("text", title = "Text", value = "abc", dialogTitle = "", dialogMessage = "Type"),
        JsPreference.CheckBox("", title = "No key"),
        JsPreference.Switch("check", title = "Repeated key"),
    )

    @Test
    fun hostPreferencesStoreAndroidTypes() {
        val host = SharedPreferencesHostPreferences(prefs) { declared }
        host.setValue("check", JsPreferenceValue.Bool(false))
        host.setValue("list", JsPreferenceValue.Text("2"))
        host.setValue("multi", JsPreferenceValue.TextSet(listOf("g", "r")))
        assertEquals(false, prefs.getBoolean("check", true))
        assertEquals("2", prefs.getString("list", null))
        assertEquals(setOf("g", "r"), prefs.getStringSet("multi", null))

        assertEquals(JsPreferenceValue.Bool(false), host.getValue("check"))
        assertEquals(JsPreferenceValue.Text("2"), host.getValue("list"))
        assertNull(host.getValue("switch"))
        // Declared order, then unknown values sorted.
        prefs.edit().putStringSet("multi", linkedSetOf("zz", "b", "x", "r")).commit()
        assertEquals(JsPreferenceValue.TextSet(listOf("r", "b", "x", "zz")), host.getValue("multi"))
    }

    @Test
    fun storedValuesOfTheWrongTypeReadAsMissing() {
        val host = SharedPreferencesHostPreferences(prefs) { declared }
        prefs.edit().putString("check", "yes").putBoolean("list", true).putString("multi", "r").putInt("text", 3).commit()
        assertNull(host.getValue("check"))
        assertNull(host.getValue("list"))
        assertNull(host.getValue("multi"))
        assertNull(host.getValue("text"))
        // Without declarations (metadata not read yet) the stored type decides.
        val undeclared = SharedPreferencesHostPreferences(prefs)
        assertEquals(JsPreferenceValue.Text("yes"), undeclared.getValue("check"))
        assertEquals(JsPreferenceValue.Bool(true), undeclared.getValue("list"))
        assertNull(undeclared.getValue("text"))
    }

    @Test
    fun theFreeStringStoreIsSeparateFromDeclaredKeys() {
        val host = SharedPreferencesHostPreferences(prefs) { declared }
        host.setString("text", "token")
        assertEquals("token", host.getString("text"))
        assertNull(host.getValue("text"))
        assertEquals("token", prefs.getString(SharedPreferencesHostPreferences.STRING_KEY_PREFIX + "text", null))
        prefs.edit().putInt(SharedPreferencesHostPreferences.STRING_KEY_PREFIX + "n", 1).commit()
        assertNull(host.getString("n"))
        assertNull(host.getString("missing"))
    }

    @Test
    fun theScreenShowsEveryDeclaredPreferenceAndPersistsDefaults() {
        val screen = PreferenceManager(context).apply { sharedPreferencesName = "source_1" }.createPreferenceScreen(context)
        JsPreferenceScreens.setup(screen, declared)

        assertEquals(5, screen.preferenceCount)
        val check = assertIs<CheckBoxPreference>(screen.getPreference(0))
        assertEquals("check", check.key)
        assertEquals("Check", check.title)
        assertEquals("A check box", check.summary)
        assertEquals(true, check.isChecked)
        assertIs<SwitchPreferenceCompat>(screen.getPreference(1)).also { assertEquals(false, it.isChecked) }
        val list = assertIs<ListPreference>(screen.getPreference(2))
        assertEquals(listOf("One", "Two"), list.entries!!.map { it.toString() })
        assertEquals(listOf("1", "2"), list.entryValues!!.map { it.toString() })
        assertEquals("2", list.value)
        assertEquals("Two", list.summary)
        val multi = assertIs<MultiSelectListPreference>(screen.getPreference(3))
        assertEquals(setOf("b", "r"), multi.values)
        val text = assertIs<EditTextPreference>(screen.getPreference(4))
        assertEquals("abc", text.text)
        assertEquals("Text", text.dialogTitle)
        assertEquals("Type", text.dialogMessage)

        // What the user picks on the screen is what the script reads.
        val host = SharedPreferencesHostPreferences(prefs) { declared }
        assertEquals(JsPreferenceValue.Bool(true), host.getValue("check"))
        assertEquals(JsPreferenceValue.TextSet(listOf("r", "b")), host.getValue("multi"))
        list.value = "1"
        text.text = "xyz"
        assertEquals(JsPreferenceValue.Text("1"), host.getValue("list"))
        assertEquals(JsPreferenceValue.Text("xyz"), host.getValue("text"))
    }

    @Test
    fun listSummariesAreShownVerbatimOrAsTheCurrentEntry() {
        val lists = listOf(
            // AndroidX formats a list summary with String.format: a lone % would throw.
            JsPreference.List("percent", title = "Quality", summary = "50% of %s", entries = listOf("Low"), entryValues = listOf("l")),
            JsPreference.List("plain", title = "Plain", entries = listOf("One", "Two"), entryValues = listOf("1", "2"), valueIndex = 1),
        )
        val screen = PreferenceManager(context).apply { sharedPreferencesName = "source_1" }.createPreferenceScreen(context)
        JsPreferenceScreens.setup(screen, lists)
        assertEquals("50% of %s", screen.findPreference<ListPreference>("percent")!!.summary?.toString())
        val plain = screen.findPreference<ListPreference>("plain")!!
        assertEquals("Two", plain.summary?.toString())
        plain.value = "1"
        assertEquals("One", plain.summary?.toString())
    }

    @Test
    fun storedValuesOfTheWrongTypeAreResetWhenTheScreenOpens() {
        prefs.edit()
            .putString("check", "yes")
            .putString("switch", "no")
            .putBoolean("list", true)
            .putString("multi", "r")
            .putInt("text", 3)
            .commit()
        val screen = PreferenceManager(context).apply { sharedPreferencesName = "source_1" }.createPreferenceScreen(context)
        JsPreferenceScreens.setup(screen, declared)

        // The widgets show the declared defaults, which are what the script reads now.
        assertEquals(true, screen.findPreference<CheckBoxPreference>("check")!!.isChecked)
        assertEquals(false, screen.findPreference<SwitchPreferenceCompat>("switch")!!.isChecked)
        assertEquals("2", screen.findPreference<ListPreference>("list")!!.value)
        assertEquals(setOf("b", "r"), screen.findPreference<MultiSelectListPreference>("multi")!!.values)
        assertEquals("abc", screen.findPreference<EditTextPreference>("text")!!.text)
        assertEquals(true, prefs.getBoolean("check", false))
        assertEquals("2", prefs.getString("list", null))
    }

    @Test
    fun storedValuesOfTheRightTypeAreKeptWhenTheScreenOpens() {
        prefs.edit().putBoolean("check", false).putString("list", "1").putStringSet("multi", setOf("g")).putString("text", "kept").commit()
        val screen = PreferenceManager(context).apply { sharedPreferencesName = "source_1" }.createPreferenceScreen(context)
        JsPreferenceScreens.setup(screen, declared)
        assertEquals(false, screen.findPreference<CheckBoxPreference>("check")!!.isChecked)
        assertEquals("1", screen.findPreference<ListPreference>("list")!!.value)
        assertEquals(setOf("g"), screen.findPreference<MultiSelectListPreference>("multi")!!.values)
        assertEquals("kept", screen.findPreference<EditTextPreference>("text")!!.text)
    }
}
