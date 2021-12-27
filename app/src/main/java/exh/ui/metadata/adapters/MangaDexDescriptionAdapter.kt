package exh.ui.metadata.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.DescriptionAdapterMdBinding
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.util.system.copyToClipboard
import exh.metadata.MetadataUtil.getRatingString
import exh.metadata.bindDrawable
import exh.metadata.metadata.MangaDexSearchMetadata
import exh.ui.metadata.MetadataViewController
import kotlin.math.round

class MangaDexDescriptionAdapter(
    private val controller: MangaController
) :
    RecyclerView.Adapter<MangaDexDescriptionAdapter.MangaDexDescriptionViewHolder>() {

    private lateinit var binding: DescriptionAdapterMdBinding

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MangaDexDescriptionViewHolder {
        binding = DescriptionAdapterMdBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MangaDexDescriptionViewHolder(binding.root)
    }

    override fun getItemCount(): Int = 1

    override fun onBindViewHolder(holder: MangaDexDescriptionViewHolder, position: Int) {
        holder.bind()
    }

    inner class MangaDexDescriptionViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        fun bind() {
            val meta = controller.presenter.meta
            if (meta == null || meta !is MangaDexSearchMetadata) return

            // todo
            val ratingFloat = meta.rating
            binding.ratingBar.rating = ratingFloat?.div(2F) ?: 0F
            @SuppressLint("SetTextI18n")
            binding.rating.text = (round((ratingFloat ?: 0F) * 100.0) / 100.0).toString() + " - " + getRatingString(itemView.context, ratingFloat)
            binding.rating.isVisible = ratingFloat != null
            binding.ratingBar.isVisible = ratingFloat != null

            binding.moreInfo.bindDrawable(itemView.context, R.drawable.ic_info_24dp)

            binding.rating.setOnLongClickListener {
                itemView.context.copyToClipboard(
                    binding.rating.text.toString(),
                    binding.rating.text.toString()
                )
                true
            }

            binding.moreInfo.setOnClickListener {
                controller.router?.pushController(
                    MetadataViewController(
                        controller.manga
                    ).withFadeTransaction()
                )
            }
        }
    }
}
