package eu.kanade.tachiyomi.widget

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.Filterable

class AutoCompleteAdapter(context: Context, resource: Int, var objects: List<String>, val excludePrefix: String?) :
    ArrayAdapter<String>(context, resource, objects),
    Filterable {

    private var mOriginalValues: List<String>? = objects
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
        override fun performFiltering(prefix: CharSequence?): FilterResults {
            val results = FilterResults()
            if (mOriginalValues == null) {
                mOriginalValues = objects
            }

            if (prefix == null || prefix.isEmpty()) {
                val list = mOriginalValues!!
                results.values = list
                results.count = list.size
            } else {
                val prefixString = prefix.toString()
                val containsPrefix: Boolean = excludePrefix?.let { prefixString.startsWith(it) } ?: false
                val filterResults = mOriginalValues!!.filter { it.contains(if (excludePrefix != null) prefixString.removePrefix(excludePrefix) else prefixString, true) }
                results.values = if (containsPrefix) filterResults.map { excludePrefix + it } else filterResults
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
