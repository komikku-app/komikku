package exh.ui.metadata.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.DescriptionAdapterHbBinding
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.util.system.copyToClipboard
import exh.metadata.bindDrawable
import exh.metadata.metadata.HBrowseSearchMetadata
import exh.ui.metadata.MetadataViewController

class HBrowseDescriptionAdapter(
    private val controller: MangaController,
) :
    RecyclerView.Adapter<HBrowseDescriptionAdapter.HBrowseDescriptionViewHolder>() {

    private lateinit var binding: DescriptionAdapterHbBinding

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HBrowseDescriptionViewHolder {
        binding = DescriptionAdapterHbBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HBrowseDescriptionViewHolder(binding.root)
    }

    override fun getItemCount(): Int = 1

    override fun onBindViewHolder(holder: HBrowseDescriptionViewHolder, position: Int) {
        holder.bind()
    }

    inner class HBrowseDescriptionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind() {
            val meta = controller.presenter.meta
            if (meta == null || meta !is HBrowseSearchMetadata) return

            binding.pages.text = itemView.resources.getQuantityString(R.plurals.num_pages, meta.length ?: 0, meta.length ?: 0)
            binding.pages.bindDrawable(itemView.context, R.drawable.ic_baseline_menu_book_24)

            binding.moreInfo.bindDrawable(itemView.context, R.drawable.ic_info_24dp)

            binding.pages.setOnLongClickListener {
                itemView.context.copyToClipboard(
                    binding.pages.text.toString(),
                    binding.pages.text.toString(),
                )
                true
            }

            binding.moreInfo.setOnClickListener {
                controller.router?.pushController(
                    MetadataViewController(
                        controller.manga,
                    ).withFadeTransaction(),
                )
            }
        }
    }
}
