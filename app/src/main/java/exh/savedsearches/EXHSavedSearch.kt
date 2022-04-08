package exh.savedsearches

import eu.kanade.tachiyomi.source.model.FilterList

data class EXHSavedSearch(
    val id: Long,
    val name: String,
    val query: String,
    val filterList: FilterList?,
)
