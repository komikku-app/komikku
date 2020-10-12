package exh.ui.metadata.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.DescriptionAdapterHiBinding
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.util.system.copyToClipboard
import exh.metadata.EX_DATE_FORMAT
import exh.metadata.metadata.HitomiSearchMetadata
import exh.ui.metadata.MetadataViewController
import exh.util.SourceTagsUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import reactivecircus.flowbinding.android.view.clicks
import reactivecircus.flowbinding.android.view.longClicks
import java.util.Date

class HitomiDescriptionAdapter(
    private val controller: MangaController
) :
    RecyclerView.Adapter<HitomiDescriptionAdapter.HitomiDescriptionViewHolder>() {

    private val scope = CoroutineScope(Job() + Dispatchers.Main)
    private lateinit var binding: DescriptionAdapterHiBinding

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HitomiDescriptionViewHolder {
        binding = DescriptionAdapterHiBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HitomiDescriptionViewHolder(binding.root)
    }

    override fun getItemCount(): Int = 1

    override fun onBindViewHolder(holder: HitomiDescriptionViewHolder, position: Int) {
        holder.bind()
    }

    inner class HitomiDescriptionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind() {
            val meta = controller.presenter.meta
            if (meta == null || meta !is HitomiSearchMetadata) return

            val genre = meta.genre
            if (genre != null) {
                val pair = when (genre) {
                    "doujinshi" -> Pair(SourceTagsUtil.DOUJINSHI_COLOR, R.string.doujinshi)
                    "manga" -> Pair(SourceTagsUtil.MANGA_COLOR, R.string.manga)
                    "artist CG" -> Pair(SourceTagsUtil.ARTIST_CG_COLOR, R.string.artist_cg)
                    "game CG" -> Pair(SourceTagsUtil.GAME_CG_COLOR, R.string.game_cg)
                    "western" -> Pair(SourceTagsUtil.WESTERN_COLOR, R.string.western)
                    "non-H" -> Pair(SourceTagsUtil.NON_H_COLOR, R.string.non_h)
                    "image Set" -> Pair(SourceTagsUtil.IMAGE_SET_COLOR, R.string.image_set)
                    "cosplay" -> Pair(SourceTagsUtil.COSPLAY_COLOR, R.string.cosplay)
                    "asian Porn" -> Pair(SourceTagsUtil.ASIAN_PORN_COLOR, R.string.asian_porn)
                    "misc" -> Pair(SourceTagsUtil.MISC_COLOR, R.string.misc)
                    else -> Pair("", 0)
                }

                if (pair.first.isNotBlank()) {
                    binding.genre.setBackgroundColor(Color.parseColor(pair.first))
                    binding.genre.text = itemView.context.getString(pair.second)
                } else binding.genre.text = genre
            } else binding.genre.setText(R.string.unknown)

            binding.whenPosted.text = EX_DATE_FORMAT.format(Date(meta.uploadDate ?: 0))
            binding.group.text = meta.group ?: itemView.context.getString(R.string.unknown)
            binding.language.text = meta.language ?: itemView.context.getString(R.string.unknown)

            listOf(
                binding.genre,
                binding.group,
                binding.language,
                binding.whenPosted
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
