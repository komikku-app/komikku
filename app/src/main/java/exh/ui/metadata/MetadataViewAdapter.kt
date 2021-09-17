package exh.ui.metadata

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.databinding.MetadataViewItemBinding
import eu.kanade.tachiyomi.util.system.copyToClipboard

class MetadataViewAdapter :
    RecyclerView.Adapter<MetadataViewAdapter.ViewHolder>() {

    private lateinit var binding: MetadataViewItemBinding

    var items: List<Pair<String, String>> = emptyList()
        set(value) {
            if (field !== value) {
                field = value
                notifyDataSetChanged()
            }
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MetadataViewAdapter.ViewHolder {
        binding = MetadataViewItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding.root)
    }

    // binds the data to the TextView in each cell
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position].first, items[position].second)
    }

    // total number of cells
    override fun getItemCount(): Int {
        return items.size
    }

    // stores and recycles views as they are scrolled off screen
    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind(title: String, text: String) {
            binding.infoTitle.text = title
            binding.infoText.text = text
            binding.infoText.setOnClickListener {
                itemView.context.copyToClipboard(title, text)
            }
        }
    }
}
