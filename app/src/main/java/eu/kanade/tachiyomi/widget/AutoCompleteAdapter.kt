package eu.kanade.tachiyomi.widget

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.Filterable

class AutoCompleteAdapter(context: Context, resource: Int, var objects: List<String>, val validPrefixes: List<String>) :
    ArrayAdapter<String>(context, resource, objects),
    Filterable {

    private val mOriginalValues: List<String> = objects
    private var mFilter: ListFilter? = null

    override fun getCount(): Int {
        return objects.size
    }

    override fun getItem(position: Int): String {
        return objects[position]
    }

    override fun getFilter(): Filter {
        if (mFilter == null) {
            mFilter = ListFilter()
        }
        return mFilter!!
    }

    private inner class ListFilter : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val results = FilterResults()

            if (constraint.isNullOrBlank()) {
                val list = mOriginalValues
                results.values = list
                results.count = list.size
            } else {
                val constraintString = constraint.toString()
                val prefix = validPrefixes.find { constraintString.startsWith(it) }
                val constraintStringNoPrefix = if (prefix != null) {
                    constraintString.removePrefix(prefix)
                } else {
                    constraintString
                }
                val filterResults = mOriginalValues.filter { it.contains(constraintStringNoPrefix, true) }
                results.values = if (prefix != null) {
                    filterResults.map { prefix + it }
                } else {
                    filterResults
                }
                results.count = filterResults.size
            }
            return results
        }

        override fun publishResults(constraint: CharSequence?, results: FilterResults) {
            objects = if (results.values != null) {
                @Suppress("UNCHECKED_CAST")
                (results.values as List<String>?).orEmpty()
            } else {
                emptyList()
            }

            if (results.count > 0) {
                notifyDataSetChanged()
            } else {
                notifyDataSetInvalidated()
            }
        }
    }
}
