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
import exh.metadata.EX_DATE_FORMAT
import exh.metadata.metadata.HitomiSearchMetadata
import exh.ui.metadata.MetadataViewController
import java.util.Date
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import reactivecircus.flowbinding.android.view.clicks

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

            val genre = meta.type
            if (genre != null) {
                val pair = when (genre) {
                    "doujinshi" -> Pair("#fc4e4e", R.string.doujinshi)
                    "manga" -> Pair("#e78c1a", R.string.manga)
                    "artist CG" -> Pair("#dde500", R.string.artist_cg)
                    "game CG" -> Pair("#05bf0b", R.string.game_cg)
                    "western" -> Pair("#14e723", R.string.western)
                    "non-H" -> Pair("#08d7e2", R.string.non_h)
                    "image Set" -> Pair("#5f5fff", R.string.image_set)
                    "cosplay" -> Pair("#9755f5", R.string.cosplay)
                    "asian Porn" -> Pair("#fe93ff", R.string.asian_porn)
                    "misc" -> Pair("#9e9e9e", R.string.misc)
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
