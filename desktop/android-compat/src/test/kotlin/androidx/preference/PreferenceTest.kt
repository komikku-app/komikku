package androidx.preference

import android.app.Application
import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.widget.EditText
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

class PreferenceTest {

    @TempDir
    lateinit var dir: File

    private val context by lazy { Application(dir) }

    private fun manager(name: String = "source_1") = PreferenceManager(context).apply { sharedPreferencesName = name }

    private fun prefs(name: String = "source_1") = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    @Test
    fun `builds a screen like an extension and persists defaults`() {
        val screen = manager().createPreferenceScreen(context)
        val list = ListPreference(screen.context).apply {
            key = "quality"
            title = "Quality"
            entries = arrayOf("Low", "High")
            entryValues = arrayOf("low", "high")
            setDefaultValue("high")
            summary = "%s"
        }
        val switch = SwitchPreferenceCompat(screen.context).apply {
            key = "nsfw"
            title = "NSFW"
            setDefaultValue(false)
        }
        screen.addPreference(list)
        screen.addPreference(switch)

        screen.preferenceCount shouldBe 2
        screen.getPreference(0) shouldBeSameInstanceAs list
        list.value shouldBe "high"
        list.entry shouldBe "High"
        list.summary shouldBe "High"
        prefs().getString("quality", null) shouldBe "high"
        prefs().getBoolean("nsfw", true) shouldBe false
        screen.findPreference<ListPreference>("quality") shouldBeSameInstanceAs list
    }

    @Test
    fun `loads persisted values instead of defaults`() {
        prefs().edit().putString("quality", "low").putStringSet("langs", setOf("en")).commit()
        val screen = manager().createPreferenceScreen(context)
        val list = ListPreference(context).apply {
            key = "quality"
            entries = arrayOf("Low", "High")
            entryValues = arrayOf("low", "high")
            setDefaultValue("high")
        }
        val multi = MultiSelectListPreference(context).apply {
            key = "langs"
            entries = arrayOf("English", "French")
            entryValues = arrayOf("en", "fr")
            setDefaultValue(setOf("en", "fr"))
        }
        screen.addPreference(list)
        screen.addPreference(multi)

        list.value shouldBe "low"
        list.findIndexOfValue("high") shouldBe 1
        multi.values shouldBe setOf("en")
        multi.values = setOf("en", "fr")
        prefs().getStringSet("langs", null) shouldBe setOf("en", "fr")
    }

    @Test
    fun `change listener can reject a click on a two state preference`() {
        val screen = manager().createPreferenceScreen(context)
        val seen = mutableListOf<Any?>()
        var accept = false
        val checkBox = CheckBoxPreference(context).apply {
            key = "flag"
            setDefaultValue(false)
            setOnPreferenceChangeListener { _, newValue ->
                seen += newValue
                accept
            }
        }
        screen.addPreference(checkBox)

        checkBox.performClick()
        checkBox.isChecked shouldBe false
        accept = true
        checkBox.performClick()
        checkBox.isChecked shouldBe true
        seen shouldContainExactly listOf(true, true)
        prefs().getBoolean("flag", false) shouldBe true
    }

    @Test
    fun `dependency disables dependents while the switch is off`() {
        val screen = manager().createPreferenceScreen(context)
        val switch = SwitchPreferenceCompat(context).apply {
            key = "enabled"
            setDefaultValue(false)
        }
        val text = EditTextPreference(context).apply {
            key = "username"
            dependency = "enabled"
        }
        screen.addPreference(switch)
        screen.addPreference(text)

        text.isEnabled shouldBe false
        switch.isChecked = true
        text.isEnabled shouldBe true
    }

    @Test
    fun `children added to a category before it is attached are persisted`() {
        val screen = manager().createPreferenceScreen(context)
        val category = PreferenceCategory(context).apply { title = "Section" }
        val text = EditTextPreference(context).apply {
            key = "domain"
            setDefaultValue("example.org")
            summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
        }
        category.addPreference(text)
        screen.addPreference(category)

        category.isEnabled shouldBe false
        text.isEnabled shouldBe true
        text.text shouldBe "example.org"
        text.summary shouldBe "example.org"
        prefs().getString("domain", null) shouldBe "example.org"
        assertThrows<IllegalStateException> { text.summary = "manual" }

        val names = mutableListOf<String?>()
        screen.forEach { names += it.title?.toString() }
        names shouldContainExactly listOf("Section")
        screen.findPreference<EditTextPreference>("domain") shouldBeSameInstanceAs text
    }

    @Test
    fun `data store replaces shared preferences`() {
        val stored = mutableMapOf<String, Any?>()
        val manager = manager().apply {
            preferenceDataStore = object : PreferenceDataStore() {
                override fun putString(key: String, value: String?) {
                    stored[key] = value
                }

                override fun getString(key: String, defValue: String?) = stored[key] as String? ?: defValue
            }
        }
        val screen = manager.createPreferenceScreen(context)
        val text = EditTextPreference(context).apply { key = "token" }
        screen.addPreference(text)
        text.text = "abc"

        stored["token"] shouldBe "abc"
        text.sharedPreferences shouldBe null
        prefs().contains("token") shouldBe false
    }

    @Test
    fun `observer is told about state changes`() {
        val manager = manager()
        val changed = mutableListOf<String?>()
        manager.addPreferenceChangeObserver { changed += it.key }
        val screen = manager.createPreferenceScreen(context)
        val list = ListPreference(context).apply {
            key = "sort"
            entries = arrayOf("A", "B")
            entryValues = arrayOf("a", "b")
        }
        screen.addPreference(list)
        changed.clear()

        list.setValueIndex(1)
        list.value shouldBe "b"
        changed shouldContainExactly listOf("sort")
    }

    @Test
    fun `bind listener configures an edit text with watchers`() {
        val text = EditTextPreference(context)
        text.setOnBindEditTextListener { editText ->
            editText.inputType = InputType.TYPE_CLASS_NUMBER
            editText.addTextChangedListener(
                object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: Editable) {
                        editText.error = if (s.toString().toIntOrNull() == null) "Not a number" else null
                    }
                },
            )
        }
        val editText = EditText(context)
        text.getOnBindEditTextListener()!!.onBindEditText(editText)

        editText.inputType shouldBe InputType.TYPE_CLASS_NUMBER
        editText.setText("12a")
        editText.error shouldBe "Not a number"
        editText.setText("12")
        editText.error shouldBe null
        editText.getText().toString() shouldBe "12"
    }
}
