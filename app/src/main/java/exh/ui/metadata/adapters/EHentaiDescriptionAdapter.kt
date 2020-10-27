package exh.ui.metadata.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.DescriptionAdapterEhBinding
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.util.system.copyToClipboard
import exh.metadata.MetadataUtil
import exh.metadata.bindDrawable
import exh.metadata.metadata.EHentaiSearchMetadata
import exh.ui.metadata.MetadataViewController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import reactivecircus.flowbinding.android.view.clicks
import reactivecircus.flowbinding.android.view.longClicks

class EHentaiDescriptionAdapter(
    private val controller: MangaController
) :
    RecyclerView.Adapter<EHentaiDescriptionAdapter.EHentaiDescriptionViewHolder>() {

    private val scope = CoroutineScope(Job() + Dispatchers.Main)
    private lateinit var binding: DescriptionAdapterEhBinding

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EHentaiDescriptionViewHolder {
        binding = DescriptionAdapterEhBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return EHentaiDescriptionViewHolder(binding.root)
    }

    override fun getItemCount(): Int = 1

    override fun onBindViewHolder(holder: EHentaiDescriptionViewHolder, position: Int) {
        holder.bind()
    }

    inner class EHentaiDescriptionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind() {
            val meta = controller.presenter.meta
            if (meta == null || meta !is EHentaiSearchMetadata) return

            binding.genre.text = meta.genre?.let { MetadataUtil.getGenreAndColour(itemView.context, it) }?.let {
                binding.genre.setBackgroundColor(it.first)
                it.second
            } ?: meta.genre ?: itemView.context.getString(R.string.unknown)

            binding.visible.text = itemView.context.getString(R.string.is_visible, meta.visible ?: itemView.context.getString(R.string.unknown))

            binding.favorites.text = (meta.favorites ?: 0).toString()
            binding.favorites.bindDrawable(itemView.context, R.drawable.ic_book_24dp)

            binding.uploader.text = meta.uploader ?: itemView.context.getString(R.string.unknown)

            binding.size.text = MetadataUtil.humanReadableByteCount(meta.size ?: 0, true)
            binding.size.bindDrawable(itemView.context, R.drawable.ic_outline_sd_card_24)

            binding.pages.text = itemView.resources.getQuantityString(R.plurals.num_pages, meta.length ?: 0, meta.length ?: 0)
            binding.pages.bindDrawable(itemView.context, R.drawable.ic_baseline_menu_book_24)

            val language = meta.language ?: itemView.context.getString(R.string.unknown)
            binding.language.text = if (meta.translated == true) {
                itemView.context.getString(R.string.language_translated, language)
            } else {
                language
            }

            val ratingFloat = meta.averageRating?.toFloat()
            binding.ratingBar.rating = ratingFloat ?: 0F
            @SuppressLint("SetTextI18n")
            binding.rating.text = (ratingFloat ?: 0F).toString() + " - " + MetadataUtil.getRatingString(itemView.context, ratingFloat?.times(2))

            binding.moreInfo.bindDrawable(itemView.context, R.drawable.ic_info_24dp)

            listOf(
                binding.favorites,
                binding.genre,
                binding.language,
                binding.pages,
                binding.rating,
                binding.uploader,
                binding.visible
            ).forEach { textView ->
                textView.longClicks()
                    .onEach {
                        itemView.context.copyToClipboard(
                            textView.text.toString(),
                            textView.text.toString()
                        )
                    }
                    .launchIn(scope)
            }

            binding.moreInfo.clicks()
                .onEach {
                    controller.router?.pushController(
                        MetadataViewController(
                            controller.manga
                        ).withFadeTransaction()
                    )
                }
                .launchIn(scope)
        }
    }
}
