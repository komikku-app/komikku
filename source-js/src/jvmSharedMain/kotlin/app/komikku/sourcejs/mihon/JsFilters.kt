package app.komikku.sourcejs.mihon

import app.komikku.sourcejs.model.JsFilter
import app.komikku.sourcejs.model.JsSortState
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

/**
 * Mangayomi filters (contract 5.1) as Mihon [Filter]s and back.
 *
 * Each Mihon filter keeps the [JsFilter] it was made from ([JsBackedFilter]), so going back only replaces the state
 * and the source gets its own objects (raw keys included) in the original order.
 */
object JsFilters {

    /** Fresh Mihon filters for [filters] (Mihon's UI changes their state in place, so never reuse them). */
    fun toFilterList(filters: List<JsFilter>): FilterList = FilterList(filters.map(::toMihon))

    /**
     * The list `search` gets for the Mihon [filters]: every filter made by [toFilterList] with its current state, in
     * order; filters of other kinds are skipped. When nothing is left (an empty `FilterList()`, as global search and
     * related-manga search may pass, or a foreign list) the source's [defaults] are used: sources read filters by
     * position, so they must never get `[]` instead of their list (contract 5.1).
     */
    fun toJsFilters(filters: List<Filter<*>>, defaults: List<JsFilter>): List<JsFilter> {
        val mapped = filters.mapNotNull { (it as? JsBackedFilter)?.toJs() }
        return mapped.ifEmpty { defaults }
    }

    internal fun toMihon(filter: JsFilter): Filter<*> = when (filter) {
        is JsFilter.Header -> HeaderFilter(filter)
        is JsFilter.Separator -> SeparatorFilter(filter)
        is JsFilter.Text -> TextFilter(filter)
        is JsFilter.CheckBox -> CheckBoxFilter(filter)
        is JsFilter.TriState -> TriStateFilter(filter)
        // Mihon's select UI reads its first option: a select without options is shown as its name only.
        is JsFilter.Select -> if (filter.values.isEmpty()) EmptySelectFilter(filter) else SelectFilter(filter)
        is JsFilter.Sort -> SortFilter(filter)
        is JsFilter.Group -> GroupFilter(filter)
    }
}

/** A Mihon filter made from a Mangayomi filter; [toJs] is that filter with the Mihon state. */
interface JsBackedFilter {
    val js: JsFilter

    fun toJs(): JsFilter
}

class HeaderFilter internal constructor(override val js: JsFilter.Header) : Filter.Header(js.name), JsBackedFilter {
    override fun toJs(): JsFilter = js
}

class SeparatorFilter internal constructor(override val js: JsFilter.Separator) : Filter.Separator(js.name), JsBackedFilter {
    override fun toJs(): JsFilter = js
}

class TextFilter internal constructor(override val js: JsFilter.Text) : Filter.Text(js.name, js.state), JsBackedFilter {
    override fun toJs(): JsFilter = js.copy(state = state)
}

class CheckBoxFilter internal constructor(override val js: JsFilter.CheckBox) :
    Filter.CheckBox(js.name, js.state),
    JsBackedFilter {
    override fun toJs(): JsFilter = js.copy(state = state)
}

class TriStateFilter internal constructor(override val js: JsFilter.TriState) :
    Filter.TriState(js.name, js.state.coerceIn(Filter.TriState.STATE_IGNORE, Filter.TriState.STATE_EXCLUDE)),
    JsBackedFilter {
    override fun toJs(): JsFilter = js.copy(state = state)
}

/** The options are shown by their names; the state is the index, as in Mangayomi. */
class SelectFilter internal constructor(override val js: JsFilter.Select) :
    Filter.Select<String>(js.name, js.values.map { it.name }.toTypedArray(), js.state.coerceIndex(js.values.size)),
    JsBackedFilter {
    override fun toJs(): JsFilter = js.copy(state = state.coerceIndex(js.values.size))
}

/** A select without options: a header with its name, sent back to the source unchanged. */
class EmptySelectFilter internal constructor(override val js: JsFilter.Select) : Filter.Header(js.name), JsBackedFilter {
    override fun toJs(): JsFilter = js
}

/** No selection in Mihon (`null`) sends the source's own default state. */
class SortFilter internal constructor(override val js: JsFilter.Sort) :
    Filter.Sort(
        js.name,
        js.values.map { it.name }.toTypedArray(),
        Selection(js.state.index.coerceIndex(js.values.size), js.state.ascending),
    ),
    JsBackedFilter {
    override fun toJs(): JsFilter {
        val selection = state ?: return js
        return js.copy(state = JsSortState(selection.index.coerceIndex(js.values.size), selection.ascending))
    }
}

/** Children of other kinds than Mihon's group UI shows are still kept and sent back. */
class GroupFilter internal constructor(override val js: JsFilter.Group) :
    Filter.Group<Filter<*>>(js.name, js.filters.map(JsFilters::toMihon)),
    JsBackedFilter {
    override fun toJs(): JsFilter = js.copy(filters = state.mapNotNull { (it as? JsBackedFilter)?.toJs() })
}

private fun Int.coerceIndex(size: Int): Int = if (size == 0) 0 else coerceIn(0, size - 1)
