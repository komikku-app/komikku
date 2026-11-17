package app.komikku.sourcejs.conformance

import app.komikku.sourcejs.TestHost
import app.komikku.sourcejs.bridge.JsPreferenceValue
import app.komikku.sourcejs.bridge.KeyValueHostPreferences
import app.komikku.sourcejs.testSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Contract 4.6: `SharedPreferences` with default materialisation. */
class PreferencesConformanceTest {

    private val members = """
constructor() {
  super();
  // Sources read preferences while the provider is being created (e.g. a base URL override).
  this.domain = new SharedPreferences().get("domain_url");
}
getSourcePreferences() {
  return [
    { key: "domain_url", editTextPreference: { title: "Domain", summary: "", value: "https://example.org", dialogTitle: "", dialogMessage: "", text: "" } },
    { key: "lang", listPreference: { title: "Language", summary: "", valueIndex: 1, entries: ["English", "French"], entryValues: ["en", "fr"] } },
    { key: "hd", switchPreferenceCompat: { title: "HD", summary: "", value: true } },
    { key: "nsfw", checkBoxPreference: { title: "NSFW", summary: "", value: false } },
    { key: "langs", multiSelectListPreference: { title: "Languages", summary: "", entries: ["A", "B"], entryValues: ["a", "b"], values: ["b"] } },
  ];
}
"""

    private val fixture = """
async function conformance(provider) {
  const prefs = new SharedPreferences();
  const r = {};
  r.constructorValue = provider.domain;
  r.values = [prefs.get("domain_url"), prefs.get("lang", "ignored default"), prefs.get("hd"), prefs.get("nsfw"), prefs.get("langs")];
  try { prefs.get("missing"); r.unknown = "no error"; } catch (e) { r.unknown = e.message; }
  r.strings = [prefs.getString("token", "def"), prefs.setString("token", "abc"), prefs.getString("token", "def"), prefs.getString("none", null)];
  return r;
}
"""

    @Test
    fun declaredDefaultsAreMaterialisedAndStoredValuesWin() = runTest {
        val host = TestHost()
        val stored = KeyValueHostPreferences(host.store, testSource.id)
        stored.setValue("hd", JsPreferenceValue.Bool(false))

        val r = host.runConformance(fixture, members)
        assertJson("\"https://example.org\"", r["constructorValue"])
        assertJson("""["https://example.org", "fr", false, false, ["b"]]""", r["values"])
        assertJson("\"Error when getting source preference \\\"missing\\\": no such preference\"", r["unknown"])
        assertJson("""["def", null, "abc", null]""", r["strings"])

        // Defaults were stored; the free string store is separate from declared preferences.
        assertEquals(JsPreferenceValue.Text("fr"), stored.getValue("lang"))
        assertEquals(JsPreferenceValue.TextSet(listOf("b")), stored.getValue("langs"))
        assertEquals(JsPreferenceValue.Bool(false), stored.getValue("hd"))
        assertEquals("abc", stored.getString("token"))
        assertEquals(null, stored.getValue("token"))

        // The user's later choice is what the next call sees.
        stored.setValue("lang", JsPreferenceValue.Text("en"))
        assertJson("\"en\"", host.runConformance(fixture, members)["values"]!!.let { (it as kotlinx.serialization.json.JsonArray)[1] })
    }
}
