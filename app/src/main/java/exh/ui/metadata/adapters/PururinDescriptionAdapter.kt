package exh.ui.metadata.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.DescriptionAdapterPuBinding
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.manga.MangaController
import exh.metadata.metadata.PururinSearchMetadata
import exh.metadata.metadata.PururinSearchMetadata.Companion.TAG_NAMESPACE_CATEGORY
import exh.ui.metadata.MetadataViewController
import exh.util.SourceTagsUtil
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import reactivecircus.flowbinding.android.view.clicks

class PururinDescriptionAdapter(
    private val controller: MangaController
) :
    RecyclerView.Adapter<PururinDescriptionAdapter.PururinDescriptionViewHolder>() {

    private val scope = CoroutineScope(Job() + Dispatchers.Main)
    private lateinit var binding: DescriptionAdapterPuBinding

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PururinDescriptionViewHolder {
        binding = DescriptionAdapterPuBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PururinDescriptionViewHolder(binding.root)
    }

    override fun getItemCount(): Int = 1

    override fun onBindViewHolder(holder: PururinDescriptionViewHolder, position: Int) {
        holder.bind()
    }

    inner class PururinDescriptionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind() {
            val meta = controller.presenter.meta
            if (meta == null || meta !is PururinSearchMetadata) return

            val genre = meta.tags.find { it.namespace == TAG_NAMESPACE_CATEGORY }
            if (genre != null) {
                val pair = when (genre.name) {
                    "doujinshi" -> Pair(SourceTagsUtil.DOUJINSHI_COLOR, R.string.doujinshi)
                    "manga" -> Pair(SourceTagsUtil.MANGA_COLOR, R.string.manga)
                    "artist-cg" -> Pair(SourceTagsUtil.ARTIST_CG_COLOR, R.string.artist_cg)
                    "game-cg" -> Pair(SourceTagsUtil.GAME_CG_COLOR, R.string.game_cg)
                    "artbook" -> Pair(SourceTagsUtil.IMAGE_SET_COLOR, R.string.artbook)
                    "webtoon" -> Pair(SourceTagsUtil.NON_H_COLOR, R.string.webtoon)
                    else -> Pair("", 0)
                }

                if (pair.first.isNotBlank()) {
                    binding.genre.setBackgroundColor(Color.parseColor(pair.first))
                    binding.genre.text = itemView.context.getString(pair.second)
                } else binding.genre.text = genre.name
            } else binding.genre.setText(R.string.unknown)

            binding.uploader.text = meta.uploaderDisp ?: meta.uploader ?: ""
            binding.size.text = meta.fileSize ?: itemView.context.getString(R.string.unknown)
            binding.pages.text = itemView.resources.getQuantityString(R.plurals.num_pages, meta.pages ?: 0, meta.pages ?: 0)

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
