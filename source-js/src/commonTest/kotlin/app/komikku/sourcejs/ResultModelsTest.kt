package app.komikku.sourcejs

import app.komikku.sourcejs.bridge.HostLog
import app.komikku.sourcejs.model.JsChapter
import app.komikku.sourcejs.model.JsFilter
import app.komikku.sourcejs.model.JsManga
import app.komikku.sourcejs.model.JsMangaStatus
import app.komikku.sourcejs.model.JsPage
import app.komikku.sourcejs.model.JsPreference
import app.komikku.sourcejs.model.JsSelectOption
import app.komikku.sourcejs.model.JsSortState
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResultModelsTest {

    private fun returning(member: String, json: String) = TestHost().extension(provider("$member { return $json; }"))

    @Test
    fun parsesMangaPagesLeniently() = runTest {
        val page = returning(
            "async getPopular(page)",
            """{ list: [{ name: "A", link: "/a", imageUrl: "/a.jpg", extra: 1 }, null, { name: 42, link: 7 }] }""",
        ).getPopular(1)
        assertEquals(
            listOf(JsManga(name = "A", link = "/a", imageUrl = "/a.jpg"), JsManga(name = "42", link = "7")),
            page.list,
        )
        assertFalse(page.hasNextPage)
        assertTrue(returning("async getPopular(page)", """{ list: [], hasNextPage: "true" }""").getPopular(1).hasNextPage)
    }

    @Test
    fun malformedPagesNameMethodAndField() = runTest {
        val notArray = assertFailsWith<JsSourceException.MalformedResult> {
            returning("async getLatestUpdates(page)", """{ list: { name: "A" }, hasNextPage: false }""").getLatestUpdates(1)
        }
        assertEquals("getLatestUpdates", notArray.method)
        assertEquals("list", notArray.field)

        val badItem = assertFailsWith<JsSourceException.MalformedResult> {
            returning("async getPopular(page)", """{ list: [{ name: "ok", link: "x" }, "oops"], hasNextPage: false }""").getPopular(1)
        }
        assertEquals("list[1]", badItem.field)

        val badName = assertFailsWith<JsSourceException.MalformedResult> {
            returning("async getPopular(page)", """{ list: [{ name: { text: "A" }, link: "x" }] }""").getPopular(1)
        }
        assertEquals("list[0].name", badName.field)

        val notObject = assertFailsWith<JsSourceException.MalformedResult> {
            returning("async getPopular(page)", "undefined").getPopular(1)
        }
        assertEquals("(result)", notObject.field)
        assertTrue("malformed result of getPopular at (result)" in notObject.message.orEmpty(), notObject.message)
    }

    @Test
    fun parsesDetailsAndChapters() = runTest {
        val manga = returning(
            "async getDetail(url)",
            """{
              name: "Title", imageUrl: "", description: " text ", author: "Au", artist: null, status: "1",
              genre: [" Action ", "Drama", "Action", "", null],
              chapters: [
                { name: "Ch 3", url: "/c3", dateUpload: "1704153600000", scanlator: "Team" },
                { name: "Ch 2", url: "/c2", dateUpload: 1704067200000 },
                { name: "Ch 1.5", url: "/c15", dateUpload: "NaN" },
                { name: "Ch 1", url: "/c1", dateUpload: null },
                { name: "no url", url: "" },
                null,
                { url: "/c0" }
              ]
            }""",
        ).getDetail("/m")
        assertEquals("Title", manga.name)
        assertEquals(null, manga.imageUrl)
        assertEquals(" text ", manga.description)
        assertEquals("Au", manga.author)
        assertEquals(null, manga.artist)
        assertEquals(JsMangaStatus.COMPLETED, manga.status)
        assertEquals(listOf("Action", "Drama"), manga.genre)
        assertEquals(
            listOf(
                JsChapter("Ch 3", "/c3", 1704153600000, "Team"),
                JsChapter("Ch 2", "/c2", 1704067200000),
                JsChapter("Ch 1.5", "/c15", null),
                JsChapter("Ch 1", "/c1", null),
                JsChapter("", "/c0", null),
            ),
            manga.chapters,
        )
    }

    @Test
    fun mapsStatusCodesAndEpisodeAlias() = runTest {
        val codes = listOf(0, 1, 2, 3, 4, 5, 9).map { code ->
            returning("async getDetail(url)", """{ status: $code }""").getDetail("x").status
        }
        assertEquals(
            listOf(
                JsMangaStatus.ONGOING,
                JsMangaStatus.COMPLETED,
                JsMangaStatus.ON_HIATUS,
                JsMangaStatus.CANCELLED,
                JsMangaStatus.PUBLISHING_FINISHED,
                JsMangaStatus.UNKNOWN,
                JsMangaStatus.UNKNOWN,
            ),
            codes,
        )
        val alias = returning("async getDetail(url)", """{ episodes: [{ name: "E1", url: "/e1" }] }""").getDetail("x")
        assertEquals(listOf(JsChapter("E1", "/e1")), alias.chapters)
    }

    @Test
    fun parsesPageLists() = runTest {
        val pages = returning(
            "async getPageList(url)",
            """[" https://i/1.jpg ", null, { url: "https://i/2.jpg", headers: { Referer: "https://site/", "X-N": 1 }, other: true },
               "https://i/1.jpg", "", { url: "https://i/3.jpg" }]""",
        ).getPageList("/c1")
        assertEquals(
            listOf(
                JsPage("https://i/1.jpg"),
                JsPage("https://i/2.jpg", mapOf("Referer" to "https://site/", "X-N" to "1")),
                JsPage("https://i/3.jpg"),
            ),
            pages,
        )
        val error = assertFailsWith<JsSourceException.MalformedResult> {
            returning("async getPageList(url)", """{ pages: [] }""").getPageList("/c1")
        }
        assertEquals("getPageList", error.method)
    }

    private val filterListJs = """[
      { type_name: "HeaderFilter", name: "Note" },
      { type_name: "SeparatorFilter" },
      { type_name: "TextFilter", type: "author", name: "Author" },
      { type_name: "CheckBox", type: "completed", name: "Completed", value: "done" },
      { type_name: "TriState", type: "nsfw", name: "NSFW", value: "adult" },
      { type_name: "SelectFilter", type: "order", name: "Order", state: 1, values: [
          { type_name: "SelectOption", name: "Popular", value: "pop" },
          { type_name: "SelectOption", name: "Latest", value: "new" } ] },
      { type_name: "SortFilter", type: "sort", name: "Sort", state: { type_name: "SortState", index: 1, ascending: true },
        values: [{ type_name: "SelectOption", name: "Name", value: "name" }, { type_name: "SelectOption", name: "Date", value: "date" }] },
      { type_name: "GroupFilter", type: "genres", name: "Genres", sourceKey: "g", state: [
          { type_name: "CheckBox", name: "Action", value: "1" },
          { type_name: "TriState", name: "Drama", value: "2", state: 2 } ] },
      { type_name: "Unknown", name: "dropped" }
    ]"""

    @Test
    fun parsesTheFullFilterTree() = runTest {
        val filters = returning("getFilterList()", filterListJs).getFilterList()
        assertEquals(8, filters.size)
        val header = filters[0] as JsFilter.Header
        assertEquals("Note", header.name)
        assertTrue(filters[1] is JsFilter.Separator)
        assertEquals("", (filters[2] as JsFilter.Text).state)
        assertEquals("author", filters[2].type)
        val checkBox = filters[3] as JsFilter.CheckBox
        assertEquals("done" to false, checkBox.value to checkBox.state)
        assertEquals(JsFilter.TriState.STATE_IGNORE, (filters[4] as JsFilter.TriState).state)
        val select = filters[5] as JsFilter.Select
        assertEquals(listOf(JsSelectOption("Popular", "pop"), JsSelectOption("Latest", "new")), select.values)
        assertEquals(1, select.state)
        val sort = filters[6] as JsFilter.Sort
        assertEquals(JsSortState(1, true), sort.state)
        assertEquals(listOf("name", "date"), sort.values.map { it.value })
        val group = filters[7] as JsFilter.Group
        assertEquals(listOf("Action", "Drama"), group.filters.map { it.name })
        assertEquals(2, (group.filters[1] as JsFilter.TriState).state)
    }

    @Test
    fun searchReceivesTheDefaultFilterListWithStatesWhenNoneIsGiven() = runTest {
        val extension = TestHost().extension(
            provider(
                """
                getFilterList() { return $filterListJs; }
                async search(query, page, filters) {
                  // Position-based reads, as real sources do.
                  const order = filters[5].values[filters[5].state].value;
                  return { list: [{ name: query + "|" + page + "|" + order + "|" + JSON.stringify(filters), link: "x" }], hasNextPage: false };
                }
                """,
            ),
        )
        val (query, page, order, json) = extension.search("one piece", 2).list.single().name.split("|", limit = 4)
        assertEquals(listOf("one piece", "2", "new"), listOf(query, page, order))
        val sent = Json.parseToJsonElement(json).let { it as kotlinx.serialization.json.JsonArray }.map { it as JsonObject }
        assertEquals(8, sent.size)
        assertEquals(
            listOf(null, null, "\"\"", "false", "0", "1", """{"type_name":"SortState","index":1,"ascending":true}"""),
            sent.take(7).map { it["state"]?.toString() },
        )
        assertEquals(
            """[{"type_name":"CheckBox","name":"Action","value":"1","state":false},{"type_name":"TriState","name":"Drama","value":"2","state":2}]""",
            sent[7]["state"].toString(),
        )
        assertEquals("\"g\"", sent[7]["sourceKey"].toString())
    }

    @Test
    fun searchSendsTheGivenFilterStates() = runTest {
        val extension = TestHost().extension(
            provider(
                """
                getFilterList() { return $filterListJs; }
                async search(query, page, filters) { return { list: [{ name: JSON.stringify(filters.map((f) => f.state)), link: "x" }], hasNextPage: false }; }
                """,
            ),
        )
        val defaults = extension.getFilterList()
        val changed = defaults.toMutableList().apply {
            this[2] = (this[2] as JsFilter.Text).copy(state = "Oda")
            this[3] = (this[3] as JsFilter.CheckBox).copy(state = true)
            this[4] = (this[4] as JsFilter.TriState).copy(state = JsFilter.TriState.STATE_EXCLUDE)
            this[5] = (this[5] as JsFilter.Select).copy(state = 0)
            this[6] = (this[6] as JsFilter.Sort).copy(state = JsSortState(0, false))
            val group = this[7] as JsFilter.Group
            this[7] = group.copy(filters = listOf((group.filters[0] as JsFilter.CheckBox).copy(state = true), group.filters[1]))
        }
        assertEquals(
            """[null,null,"Oda",true,2,0,{"type_name":"SortState","index":0,"ascending":false},""" +
                """[{"type_name":"CheckBox","name":"Action","value":"1","state":true},{"type_name":"TriState","name":"Drama","value":"2","state":2}]]""",
            extension.search("q", 1, changed).list.single().name,
        )
        // An explicitly empty list is sent as is.
        assertEquals("[]", extension.search("q", 1, emptyList()).list.single().name)
    }

    @Test
    fun unparsableFilterListsBecomeEmptyWithAWarning() = runTest {
        val host = TestHost()
        val extension = host.extension(provider("""getFilterList() { return [{ type_name: "SelectFilter", name: "x", values: 3 }]; }"""))
        assertEquals(emptyList(), extension.getFilterList())
        assertEquals(HostLog.Level.WARN, host.log.lines.single().first)
    }

    @Test
    fun parsesAllPreferenceTypes() = runTest {
        val preferences = returning(
            "getSourcePreferences()",
            """[
              { key: "cb", checkBoxPreference: { title: "CB", summary: "s", value: true } },
              { key: "sw", switchPreferenceCompat: { title: "SW", summary: "", value: false } },
              { key: "list", listPreference: { title: "L", summary: "", valueIndex: -2, entries: ["A", "B"], entryValues: ["a", "b"] } },
              { key: "multi", multiSelectListPreference: { title: "M", summary: "", entries: ["X", "Y"], entryValues: ["x", "y"], values: ["y"] } },
              { key: "url", editTextPreference: { title: "U", summary: "", value: "https://a", dialogTitle: "T", dialogMessage: "M", text: "https://a" } },
              { key: "odd", unknownPreference: {} },
              { checkBoxPreference: { title: "no key" } }
            ]""",
        ).getSourcePreferences()
        assertEquals(
            listOf(
                JsPreference.CheckBox("cb", "CB", "s", true),
                JsPreference.Switch("sw", "SW", "", false),
                JsPreference.List("list", "L", "", listOf("A", "B"), listOf("a", "b"), 0),
                JsPreference.MultiSelect("multi", "M", "", listOf("X", "Y"), listOf("x", "y"), listOf("y")),
                JsPreference.EditText("url", "U", "", "https://a", "T", "M", "https://a"),
            ),
            preferences,
        )
    }

    @Test
    fun modelsAreSerializable() {
        val filter: JsFilter = JsFilter.Select("Order", "order", JsonObject(emptyMap()), listOf(JsSelectOption("A", "a")), 0)
        val json = Json.encodeToString(JsFilter.serializer(), filter)
        assertEquals(filter, Json.decodeFromString(JsFilter.serializer(), json))
        val preference: JsPreference = JsPreference.List("k", entries = listOf("A"), entryValues = listOf("a"))
        assertEquals(preference, Json.decodeFromString(JsPreference.serializer(), Json.encodeToString(JsPreference.serializer(), preference)))
    }
}
