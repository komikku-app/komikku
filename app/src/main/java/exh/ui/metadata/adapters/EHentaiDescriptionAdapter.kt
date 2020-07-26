package exh.ui.metadata.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.DescriptionAdapterEhBinding
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.util.system.getResourceColor
import exh.metadata.EX_DATE_FORMAT
import exh.metadata.humanReadableByteCount
import exh.metadata.metadata.EHentaiSearchMetadata
import exh.ui.metadata.MetadataViewController
import java.util.Date
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import reactivecircus.flowbinding.android.view.clicks

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

            val genre = meta.genre
            if (genre != null) {
                val pair = when (genre) {
                    "doujinshi" -> Pair("#fc4e4e", R.string.doujinshi)
                    "manga" -> Pair("#e78c1a", R.string.manga)
                    "artistcg" -> Pair("#dde500", R.string.artist_cg)
                    "gamecg" -> Pair("#05bf0b", R.string.game_cg)
                    "western" -> Pair("#14e723", R.string.western)
                    "non-h" -> Pair("#08d7e2", R.string.non_h)
                    "imageset" -> Pair("#5f5fff", R.string.image_set)
                    "cosplay" -> Pair("#9755f5", R.string.cosplay)
                    "asianporn" -> Pair("#fe93ff", R.string.asian_porn)
                    "misc" -> Pair("#9e9e9e", R.string.misc)
                    else -> Pair("", 0)
                }

                if (pair.first.isNotBlank()) {
                    binding.genre.setBackgroundColor(Color.parseColor(pair.first))
                    binding.genre.text = itemView.context.getString(pair.second)
                } else binding.genre.text = genre
            } else binding.genre.setText(R.string.unknown)

            binding.visible.text = itemView.context.getString(R.string.is_visible, meta.visible ?: itemView.context.getString(R.string.unknown))

            binding.favorites.text = (meta.favorites ?: 0).toString()
            val drawable = itemView.context.getDrawable(R.drawable.ic_favorite_24dp)
            drawable?.setTint(itemView.context.getResourceColor(R.attr.colorAccent))
            binding.favorites.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null)

            binding.whenPosted.text = EX_DATE_FORMAT.format(Date(meta.datePosted ?: 0))

            binding.uploader.text = meta.uploader ?: itemView.context.getString(R.string.unknown)
            binding.size.text = humanReadableByteCount(meta.size ?: 0, true)
            binding.pages.text = itemView.context.getString(R.string.num_pages, meta.length ?: 0)
            val language = meta.language ?: itemView.context.getString(R.string.unknown)
            binding.language.text = if (meta.translated == true) {
                itemView.context.getString(R.string.language_translated, language)
            } else {
                language
            }

            val ratingFloat = meta.averageRating?.toFloat()
            val name = when (((ratingFloat ?: 100F) * 2).roundToInt()) {
                0 -> R.string.rating0
                1 -> R.string.rating1
                2 -> R.string.rating2
                3 -> R.string.rating3
                4 -> R.string.rating4
                5 -> R.string.rating5
                6 -> R.string.rating6
                7 -> R.string.rating7
                8 -> R.string.rating8
                9 -> R.string.rating9
                10 -> R.string.rating10
                else -> R.string.no_rating
            }
            binding.ratingBar.rating = ratingFloat ?: 0F
            binding.rating.text = if (meta.ratingCount != null) {
                itemView.context.getString(R.string.rating_view, itemView.context.getString(name), (ratingFloat ?: 0F).toString(), meta.ratingCount ?: 0)
            } else {
                itemView.context.getString(R.string.rating_view_no_count, itemView.context.getString(name), (ratingFloat ?: 0F).toString())
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
