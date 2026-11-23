package app.komikku.sourcejs.mihon

import app.komikku.sourcejs.model.JsFilter
import app.komikku.sourcejs.model.JsResultParser
import app.komikku.sourcejs.model.toJsonArray
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame

class JsFiltersTest {

    /** Every kind, as a source's `getFilterList()` returns them (extra keys kept). */
    private val defaults: List<JsFilter> = JsResultParser.filters(
        Json.parseToJsonElement(
            """
            [
              {"type_name": "HeaderFilter", "name": "Note"},
              {"type_name": "SeparatorFilter", "name": ""},
              {"type_name": "TextFilter", "name": "Author", "type": "author", "state": "x"},
              {"type_name": "CheckBox", "name": "Completed", "value": "done", "state": true},
              {"type_name": "TriState", "name": "Romance", "value": "romance", "state": 2},
              {"type_name": "SelectFilter", "name": "Type", "type": "kind", "state": 1, "custom": 7,
               "values": [{"type_name": "SelectOption", "name": "All", "value": ""},
                          {"type_name": "SelectOption", "name": "Manga", "value": "manga"}]},
              {"type_name": "SortFilter", "name": "Sort", "state": {"type_name": "SortState", "index": 1, "ascending": true},
               "values": [{"type_name": "SelectOption", "name": "Latest", "value": "latest"},
                          {"type_name": "SelectOption", "name": "Views", "value": "views"}]},
              {"type_name": "GroupFilter", "name": "Genres", "state": [
                {"type_name": "CheckBox", "name": "Action", "value": "action"},
                {"type_name": "TriState", "name": "Drama", "value": "drama"},
                {"type_name": "GroupFilter", "name": "Nested", "state": [{"type_name": "CheckBox", "name": "Deep", "value": "deep"}]}
              ]}
            ]
            """.trimIndent(),
        ),
    )

    @Test
    fun everyKindMapsToTheMatchingMihonFilterWithItsState() {
        val list = JsFilters.toFilterList(defaults)
        assertIs<Filter.Header>(list[0]).also { assertEquals("Note", it.name) }
        assertIs<Filter.Separator>(list[1])
        assertEquals("x", assertIs<Filter.Text>(list[2]).state)
        assertEquals(true, assertIs<Filter.CheckBox>(list[3]).state)
        assertEquals(Filter.TriState.STATE_EXCLUDE, assertIs<Filter.TriState>(list[4]).state)
        val select = assertIs<Filter.Select<*>>(list[5])
        assertEquals(listOf("All", "Manga"), select.values.toList())
        assertEquals(1, select.state)
        assertEquals(Filter.Sort.Selection(1, true), assertIs<Filter.Sort>(list[6]).state)
        assertEquals(listOf("Latest", "Views"), (list[6] as Filter.Sort).values.toList())
        val group = assertIs<Filter.Group<*>>(list[7])
        assertEquals(listOf("Action", "Drama", "Nested"), group.state.map { (it as Filter<*>).name })
        assertIs<Filter.Group<*>>(group.state[2])
    }

    @Test
    fun stateChangesGoBackToTheSourcesOwnObjects() {
        val list = JsFilters.toFilterList(defaults)
        (list[2] as Filter.Text).state = "Oda"
        (list[3] as Filter.CheckBox).state = false
        (list[4] as Filter.TriState).state = Filter.TriState.STATE_INCLUDE
        (list[5] as Filter.Select<*>).state = 0
        (list[6] as Filter.Sort).state = Filter.Sort.Selection(0, false)
        val group = list[7] as Filter.Group<*>
        (group.state[0] as Filter.CheckBox).state = true
        (group.state[1] as Filter.TriState).state = Filter.TriState.STATE_EXCLUDE
        (((group.state[2] as Filter.Group<*>).state[0]) as Filter.CheckBox).state = true

        val back = JsFilters.toJsFilters(list, defaults = emptyList())
        assertEquals(defaults.size, back.size)
        assertSame(defaults[0], back[0])
        assertEquals("Oda", (back[2] as JsFilter.Text).state)
        assertEquals(false, (back[3] as JsFilter.CheckBox).state)
        assertEquals(JsFilter.TriState.STATE_INCLUDE, (back[4] as JsFilter.TriState).state)
        assertEquals(0, (back[5] as JsFilter.Select).state)
        assertEquals(0, (back[6] as JsFilter.Sort).state.index)
        assertEquals(false, (back[6] as JsFilter.Sort).state.ascending)

        // What the source receives: the raw keys, the canonical type names and the new states, in the original order.
        val json = back.toJsonArray()
        assertEquals(7, json[5].jsonObject["custom"]!!.jsonPrimitive.content.toInt())
        assertEquals("kind", json[5].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("Oda", json[2].jsonObject["state"]!!.jsonPrimitive.content)
        val groupState = json[7].jsonObject["state"]!!.jsonArray
        assertEquals("true", groupState[0].jsonObject["state"]!!.jsonPrimitive.content)
        assertEquals("2", groupState[1].jsonObject["state"]!!.jsonPrimitive.content)
        val nested = groupState[2].jsonObject["state"]!!.jsonArray[0] as JsonObject
        assertEquals("true", nested["state"]!!.jsonPrimitive.content)
        assertEquals("deep", nested["value"]!!.jsonPrimitive.content)
    }

    @Test
    fun aSortWithoutSelectionSendsTheSourcesDefault() {
        val list = JsFilters.toFilterList(defaults)
        (list[6] as Filter.Sort).state = null
        assertSame(defaults[6], JsFilters.toJsFilters(list, emptyList())[6])
    }

    @Test
    fun indexesOutOfRangeAreClamped() {
        val select = JsFilter.Select("S", null, JsonObject(emptyMap()), values = (defaults[5] as JsFilter.Select).values, state = 9)
        val mihon = JsFilters.toMihon(select) as Filter.Select<*>
        assertEquals(1, mihon.state)
        mihon.state = -3
        assertEquals(0, ((mihon as JsBackedFilter).toJs() as JsFilter.Select).state)
        val empty = JsFilter.Select("E", null, JsonObject(emptyMap()), values = emptyList(), state = 4)
        assertEquals(0, (JsFilters.toMihon(empty) as Filter.Select<*>).state)
        val tri = JsFilter.TriState("T", null, JsonObject(emptyMap()), state = 7)
        assertEquals(Filter.TriState.STATE_EXCLUDE, (JsFilters.toMihon(tri) as Filter.TriState).state)
    }

    @Test
    fun emptyOrForeignListsFallBackToTheDefaults() {
        assertSame(defaults, JsFilters.toJsFilters(FilterList(), defaults))
        val foreign = object : Filter.CheckBox("Foreign") {}
        assertSame(defaults, JsFilters.toJsFilters(FilterList(foreign), defaults))
        val mixed = JsFilters.toJsFilters(FilterList(foreign, JsFilters.toMihon(defaults[3])), defaults)
        assertEquals(listOf(defaults[3]), mixed)
    }

    @Test
    fun everyCallGivesFreshFilters() {
        val first = JsFilters.toFilterList(defaults)
        val second = JsFilters.toFilterList(defaults)
        assertNotSame(first[2], second[2])
        (first[2] as Filter.Text).state = "changed"
        assertEquals("x", (second[2] as Filter.Text).state)
        assertNull(JsFilters.toFilterList(emptyList()).firstOrNull())
    }
}
