package exh.ui.metadata.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.DescriptionAdapterPeBinding
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.util.system.copyToClipboard
import exh.metadata.metadata.PervEdenSearchMetadata
import exh.ui.metadata.MetadataViewController
import exh.util.SourceTagsUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import reactivecircus.flowbinding.android.view.clicks
import reactivecircus.flowbinding.android.view.longClicks
import java.util.Locale
import kotlin.math.roundToInt

class PervEdenDescriptionAdapter(
    private val controller: MangaController
) :
    RecyclerView.Adapter<PervEdenDescriptionAdapter.PervEdenDescriptionViewHolder>() {

    private val scope = CoroutineScope(Job() + Dispatchers.Main)
    private lateinit var binding: DescriptionAdapterPeBinding

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PervEdenDescriptionViewHolder {
        binding = DescriptionAdapterPeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PervEdenDescriptionViewHolder(binding.root)
    }

    override fun getItemCount(): Int = 1

    override fun onBindViewHolder(holder: PervEdenDescriptionViewHolder, position: Int) {
        holder.bind()
    }

    inner class PervEdenDescriptionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind() {
            val meta = controller.presenter.meta
            if (meta == null || meta !is PervEdenSearchMetadata) return

            val genre = meta.genre
            if (genre != null) {
                val pair = when (genre) {
                    "Doujinshi" -> Pair(SourceTagsUtil.DOUJINSHI_COLOR, R.string.doujinshi)
                    "Japanese Manga" -> Pair(SourceTagsUtil.MANGA_COLOR, R.string.manga)
                    "Korean Manhwa" -> Pair(SourceTagsUtil.ARTIST_CG_COLOR, R.string.manhwa)
                    "Chinese Manhua" -> Pair(SourceTagsUtil.GAME_CG_COLOR, R.string.manhua)
                    "Comic" -> Pair(SourceTagsUtil.WESTERN_COLOR, R.string.comic)
                    else -> Pair("", 0)
                }

                if (pair.first.isNotBlank()) {
                    binding.genre.setBackgroundColor(Color.parseColor(pair.first))
                    binding.genre.text = itemView.context.getString(pair.second)
                } else binding.genre.text = genre
            } else binding.genre.setText(R.string.unknown)

            val language = meta.lang
            binding.language.text = if (language != null) {
                val local = Locale(language)
                local.displayName
            } else itemView.context.getString(R.string.unknown)

            val name = when (((meta.rating ?: 100F) * 2).roundToInt()) {
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
            binding.ratingBar.rating = meta.rating ?: 0F
            binding.rating.text = itemView.context.getString(R.string.rating_view_no_count, itemView.context.getString(name), (meta.rating ?: 0F).toString())

            listOf(
                binding.genre,
                binding.language,
                binding.rating
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
