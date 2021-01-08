package exh.ui.metadata

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.databinding.MetadataViewItemBinding
import eu.kanade.tachiyomi.util.system.copyToClipboard

class MetadataViewAdapter(private var data: List<Pair<String, String>>) :
    RecyclerView.Adapter<MetadataViewAdapter.ViewHolder>() {

    private lateinit var binding: MetadataViewItemBinding

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MetadataViewAdapter.ViewHolder {
        binding = MetadataViewItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding.root)
    }

    fun update(data: List<Pair<String, String>>) {
        this.data = data
        notifyDataSetChanged()
    }

    // binds the data to the TextView in each cell
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(position)
    }

    // total number of cells
    override fun getItemCount(): Int = data.size

    // stores and recycles views as they are scrolled off screen
    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind(position: Int) {
            binding.infoTitle.text = data[position].first
            binding.infoText.text = data[position].second
            binding.infoText.setOnClickListener {
                itemView.context.copyToClipboard(data[position].second, data[position].second)
            }
        }

        override fun equals(other: Any?): Boolean {
            return binding.infoText.hashCode() == other.hashCode()
        }

        override fun hashCode(): Int {
            return binding.infoText.hashCode()
        }
    }

    override fun equals(other: Any?): Boolean {
        return super.equals(other)
    }

    override fun hashCode(): Int {
        return super.hashCode()
    }
}
