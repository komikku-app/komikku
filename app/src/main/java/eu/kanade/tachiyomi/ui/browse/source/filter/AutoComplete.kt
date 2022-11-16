package eu.kanade.tachiyomi.ui.browse.source.filter

import android.annotation.SuppressLint
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.AutoCompleteTextView
import android.widget.TextView
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.davidea.viewholders.FlexibleViewHolder
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.widget.AutoCompleteAdapter
import exh.log.xLogD

open class AutoComplete(val filter: Filter.AutoComplete) : AbstractFlexibleItem<AutoComplete.Holder>() {

    override fun getLayoutRes(): Int {
        return R.layout.navigation_view_autocomplete
    }

    override fun createViewHolder(view: View, adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>): Holder {
        return Holder(view, adapter)
    }

    override fun bindViewHolder(adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>, holder: Holder, position: Int, payloads: List<Any?>?) {
        @SuppressLint("SetTextI18n")
        holder.text.text = "${filter.name}: "

        holder.autoComplete.hint = filter.hint
        holder.autoComplete.setAdapter(
            AutoCompleteAdapter(
                holder.itemView.context,
                android.R.layout.simple_dropdown_item_1line,
                filter.values,
                filter.validPrefixes,
            ),
        )
        holder.autoComplete.threshold = 3

        // select from auto complete
        holder.autoComplete.setOnItemClickListener { adapterView, _, chipPosition, _ ->
            var name = (adapterView.getItemAtPosition(chipPosition) as String).trim()
            filter.validPrefixes.find { name.startsWith(it) }?.let { name = name.removePrefix(it).trim() }
            if (name !in filter.skipAutoFillTags) {
                holder.autoComplete.text = null
                addTag(name, holder)
            }
        }

        // done keyboard button is pressed
        holder.autoComplete.setOnEditorActionListener { textView, actionId, _ ->
            if (actionId != EditorInfo.IME_ACTION_DONE) return@setOnEditorActionListener false
            var name = textView.text.toString().trim()
            filter.validPrefixes.find { name.startsWith(it) }?.let { name = name.removePrefix(it).trim() }
            if (name !in filter.skipAutoFillTags) {
                textView.text = null
                addTag(name, holder)
            }
            true
        }

        // space or comma is detected
        holder.autoComplete.addTextChangedListener {
            if (it == null || it.isEmpty()) {
                return@addTextChangedListener
            }

            if (it.last() == ',') {
                val name = it.toString().dropLast(1).trim()
                addTag(name, holder)

                holder.autoComplete.text = null
            }
        }

        holder.mainTagChipGroup.removeAllViews()
        filter.state.forEach {
            addChipToGroup(it, holder)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return filter == (other as AutoComplete).filter
    }

    override fun hashCode(): Int {
        return filter.hashCode()
    }

    private fun addTag(name: String, holder: Holder) {
        if (name.isNotEmpty() && !filter.state.contains(name)) {
            addChipToGroup(name, holder)
            filter.state += name
        } else {
            xLogD("Invalid tag: %s", name)
        }
    }

    private fun addChipToGroup(name: String, holder: Holder) {
        val chip = Chip(holder.itemView.context)
        chip.text = name

        chip.isClickable = true
        chip.isCheckable = false
        chip.isCloseIconVisible = true

        holder.mainTagChipGroup.addView(chip)

        chip.setOnCloseIconClickListener {
            holder.mainTagChipGroup.removeView(chip)
            filter.state -= name
        }
    }

    class Holder(view: View, adapter: FlexibleAdapter<*>) : FlexibleViewHolder(view, adapter) {

        val text: TextView = itemView.findViewById(R.id.nav_view_item_text)
        val autoComplete: AutoCompleteTextView = itemView.findViewById(R.id.nav_view_item)
        val mainTagChipGroup: ChipGroup = itemView.findViewById(R.id.chip_group)
    }
}
