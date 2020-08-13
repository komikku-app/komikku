package exh.ui.metadata

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.databinding.MetadataViewItemBinding
import eu.kanade.tachiyomi.util.system.copyToClipboard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import reactivecircus.flowbinding.android.view.clicks
import kotlin.math.floor

class MetadataViewAdapter(private var data: List<Pair<String, String>>) :
    RecyclerView.Adapter<MetadataViewAdapter.ViewHolder>() {

    private lateinit var binding: MetadataViewItemBinding
    private val scope = CoroutineScope(Job() + Dispatchers.Main)

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
    override fun getItemCount(): Int = data.size * 2

    // stores and recycles views as they are scrolled off screen
    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private var dataPosition: Int? = null
        fun bind(position: Int) {
            if (data.isEmpty() || !binding.infoText.text.isNullOrBlank()) return
            dataPosition = floor(position / 2F).toInt()
            binding.infoText.text = if (position % 2 == 0) data[dataPosition!!].first else data[dataPosition!!].second
            binding.infoText.clicks()
                .onEach {
                    itemView.context.copyToClipboard(data[dataPosition!!].second, data[dataPosition!!].second)
                }
                .launchIn(scope)
        }

        override fun equals(other: Any?): Boolean {
            return dataPosition.hashCode() == other.hashCode()
        }

        override fun hashCode(): Int {
            return dataPosition.hashCode()
        }
    }

    override fun equals(other: Any?): Boolean {
        return super.equals(other)
    }

    override fun hashCode(): Int {
        return super.hashCode()
    }
}
