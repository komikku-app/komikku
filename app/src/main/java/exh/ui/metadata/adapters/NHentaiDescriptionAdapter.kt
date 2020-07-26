package exh.ui.metadata.adapters

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.DescriptionAdapterNhBinding
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.util.system.getResourceColor
import exh.metadata.EX_DATE_FORMAT
import exh.metadata.metadata.NHentaiSearchMetadata
import exh.ui.metadata.MetadataViewController
import java.util.Date
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import reactivecircus.flowbinding.android.view.clicks

class NHentaiDescriptionAdapter(
    private val controller: MangaController
) :
    RecyclerView.Adapter<NHentaiDescriptionAdapter.NHentaiDescriptionViewHolder>() {

    private val scope = CoroutineScope(Job() + Dispatchers.Main)
    private lateinit var binding: DescriptionAdapterNhBinding

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NHentaiDescriptionViewHolder {
        binding = DescriptionAdapterNhBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NHentaiDescriptionViewHolder(binding.root)
    }

    override fun getItemCount(): Int = 1

    override fun onBindViewHolder(holder: NHentaiDescriptionViewHolder, position: Int) {
        holder.bind()
    }

    inner class NHentaiDescriptionViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        fun bind() {
            val meta = controller.presenter.meta
            if (meta == null || meta !is NHentaiSearchMetadata) return

            var category: String? = null
            meta.tags.filter { it.namespace == NHentaiSearchMetadata.NHENTAI_CATEGORIES_NAMESPACE }.let {
                if (it.isNotEmpty()) category = it.joinToString(transform = { it.name })
            }

            if (category != null) {
                val pair = when (category) {
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
                } else binding.genre.text = category
            } else binding.genre.setText(R.string.unknown)

            meta.favoritesCount?.let {
                if (it == 0L) return@let
                binding.favorites.text = it.toString()

                val drawable = itemView.context.getDrawable(R.drawable.ic_favorite_24dp)
                drawable?.setTint(itemView.context.getResourceColor(R.attr.colorAccent))

                binding.favorites.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null)
            }

            binding.whenPosted.text = EX_DATE_FORMAT.format(Date((meta.uploadDate ?: 0) * 1000))

            binding.pages.text = itemView.context.getString(R.string.num_pages, meta.pageImageTypes.size)
            @SuppressLint("SetTextI18n")
            binding.id.text = "#" + (meta.nhId ?: 0)

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
