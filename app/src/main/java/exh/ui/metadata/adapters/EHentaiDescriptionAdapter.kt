package exh.ui.metadata.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.DescriptionAdapterEhBinding
import eu.kanade.tachiyomi.ui.base.controller.pushController
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.util.system.copyToClipboard
import exh.metadata.MetadataUtil
import exh.metadata.bindDrawable
import exh.metadata.metadata.EHentaiSearchMetadata
import exh.metadata.metadata.base.RaisedSearchMetadata
import exh.ui.metadata.MetadataViewController

class EHentaiDescriptionAdapter(
    private val controller: MangaController,
) :
    RecyclerView.Adapter<EHentaiDescriptionAdapter.EHentaiDescriptionViewHolder>() {

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
            val meta = controller.presenter.meta.value
            if (meta == null || meta !is EHentaiSearchMetadata) return

            binding.genre.text =
                meta.genre?.let { MetadataUtil.getGenreAndColour(itemView.context, it) }
                    ?.let {
                        binding.genre.setBackgroundColor(it.first)
                        it.second
                    }
                    ?: meta.genre
                    ?: itemView.context.getString(R.string.unknown)

            binding.visible.text = itemView.context.getString(R.string.is_visible, meta.visible ?: itemView.context.getString(R.string.unknown))

            binding.favorites.text = (meta.favorites ?: 0).toString()
            binding.favorites.bindDrawable(itemView.context, R.drawable.ic_book_24dp)

            binding.uploader.text = meta.uploader ?: itemView.context.getString(R.string.unknown)

            binding.size.text = MetadataUtil.humanReadableByteCount(meta.size ?: 0, true)
            binding.size.bindDrawable(itemView.context, R.drawable.ic_outline_sd_card_24)

            val length = meta.length ?: 0
            binding.pages.text = itemView.resources.getQuantityString(R.plurals.num_pages, length, length)
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
                binding.visible,
            ).forEach { textView ->
                textView.setOnLongClickListener {
                    itemView.context.copyToClipboard(
                        textView.text.toString(),
                        textView.text.toString(),
                    )
                    true
                }
            }

            binding.uploader.setOnClickListener {
                meta.uploader?.let { controller.performSearch("uploader:\"$it\"") }
            }

            binding.moreInfo.setOnClickListener {
                controller.router?.pushController(
                    MetadataViewController(
                        controller.manga,
                    ),
                )
            }
        }
    }
}

@Composable
fun EHentaiDescription(controller: MangaController) {
    val meta by controller.presenter.meta.collectAsState()
    EHentaiDescription(controller = controller, meta = meta)
}

@Composable
private fun EHentaiDescription(controller: MangaController, meta: RaisedSearchMetadata?) {
    val context = LocalContext.current
    AndroidView(
        factory = { factoryContext ->
            DescriptionAdapterEhBinding.inflate(LayoutInflater.from(factoryContext)).root
        },
        update = {
            if (meta == null || meta !is EHentaiSearchMetadata) return@AndroidView
            val binding = DescriptionAdapterEhBinding.bind(it)

            binding.genre.text =
                meta.genre?.let { MetadataUtil.getGenreAndColour(context, it) }
                    ?.let {
                        binding.genre.setBackgroundColor(it.first)
                        it.second
                    }
                    ?: meta.genre
                    ?: context.getString(R.string.unknown)

            binding.visible.text = context.getString(R.string.is_visible, meta.visible ?: context.getString(R.string.unknown))

            binding.favorites.text = (meta.favorites ?: 0).toString()
            binding.favorites.bindDrawable(context, R.drawable.ic_book_24dp)

            binding.uploader.text = meta.uploader ?: context.getString(R.string.unknown)

            binding.size.text = MetadataUtil.humanReadableByteCount(meta.size ?: 0, true)
            binding.size.bindDrawable(context, R.drawable.ic_outline_sd_card_24)

            val length = meta.length ?: 0
            binding.pages.text = context.resources.getQuantityString(R.plurals.num_pages, length, length)
            binding.pages.bindDrawable(context, R.drawable.ic_baseline_menu_book_24)

            val language = meta.language ?: context.getString(R.string.unknown)
            binding.language.text = if (meta.translated == true) {
                context.getString(R.string.language_translated, language)
            } else {
                language
            }

            val ratingFloat = meta.averageRating?.toFloat()
            binding.ratingBar.rating = ratingFloat ?: 0F
            @SuppressLint("SetTextI18n")
            binding.rating.text = (ratingFloat ?: 0F).toString() + " - " + MetadataUtil.getRatingString(context, ratingFloat?.times(2))

            binding.moreInfo.bindDrawable(context, R.drawable.ic_info_24dp)

            listOf(
                binding.favorites,
                binding.genre,
                binding.language,
                binding.pages,
                binding.rating,
                binding.uploader,
                binding.visible,
            ).forEach { textView ->
                textView.setOnLongClickListener {
                    context.copyToClipboard(
                        textView.text.toString(),
                        textView.text.toString(),
                    )
                    true
                }
            }

            binding.uploader.setOnClickListener {
                meta.uploader?.let { controller.performSearch("uploader:\"$it\"") }
            }

            binding.moreInfo.setOnClickListener {
                controller.router?.pushController(
                    MetadataViewController(
                        controller.manga,
                    ),
                )
            }
        },
    )
}
