package exh.ui.metadata.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.DescriptionAdapterTsBinding
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.util.system.copyToClipboard
import exh.metadata.MetadataUtil
import exh.metadata.bindDrawable
import exh.metadata.metadata.TsuminoSearchMetadata
import exh.ui.metadata.MetadataViewController
import java.util.Date
import kotlin.math.round

class TsuminoDescriptionAdapter(
    private val controller: MangaController,
) :
    RecyclerView.Adapter<TsuminoDescriptionAdapter.TsuminoDescriptionViewHolder>() {

    private lateinit var binding: DescriptionAdapterTsBinding

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TsuminoDescriptionViewHolder {
        binding = DescriptionAdapterTsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TsuminoDescriptionViewHolder(binding.root)
    }

    override fun getItemCount(): Int = 1

    override fun onBindViewHolder(holder: TsuminoDescriptionViewHolder, position: Int) {
        holder.bind()
    }

    inner class TsuminoDescriptionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind() {
            val meta = controller.presenter.meta
            if (meta == null || meta !is TsuminoSearchMetadata) return

            binding.genre.text = meta.category?.let { MetadataUtil.getGenreAndColour(itemView.context, it) }?.let {
                binding.genre.setBackgroundColor(it.first)
                it.second
            } ?: meta.category ?: itemView.context.getString(R.string.unknown)

            binding.favorites.text = (meta.favorites ?: 0).toString()
            binding.favorites.bindDrawable(itemView.context, R.drawable.ic_book_24dp)

            binding.whenPosted.text = TsuminoSearchMetadata.TSUMINO_DATE_FORMAT.format(Date(meta.uploadDate ?: 0))

            binding.uploader.text = meta.uploader ?: itemView.context.getString(R.string.unknown)

            binding.pages.text = itemView.resources.getQuantityString(R.plurals.num_pages, meta.length ?: 0, meta.length ?: 0)
            binding.pages.bindDrawable(itemView.context, R.drawable.ic_baseline_menu_book_24)

            binding.ratingBar.rating = meta.averageRating ?: 0F
            @SuppressLint("SetTextI18n")
            binding.rating.text = (round((meta.averageRating ?: 0F) * 100.0) / 100.0).toString() + " - " + MetadataUtil.getRatingString(itemView.context, meta.averageRating?.times(2))

            binding.moreInfo.bindDrawable(itemView.context, R.drawable.ic_info_24dp)

            listOf(
                binding.favorites,
                binding.genre,
                binding.pages,
                binding.rating,
                binding.uploader,
                binding.whenPosted,
            ).forEach { textView ->
                textView.setOnLongClickListener {
                    itemView.context.copyToClipboard(
                        textView.text.toString(),
                        textView.text.toString(),
                    )
                    true
                }
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
