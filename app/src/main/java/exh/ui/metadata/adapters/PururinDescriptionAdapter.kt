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
import eu.kanade.tachiyomi.databinding.DescriptionAdapterPuBinding
import eu.kanade.tachiyomi.ui.base.controller.pushController
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.util.system.copyToClipboard
import exh.metadata.MetadataUtil
import exh.metadata.bindDrawable
import exh.metadata.metadata.PururinSearchMetadata
import exh.metadata.metadata.base.RaisedSearchMetadata
import exh.ui.metadata.MetadataViewController
import kotlin.math.round

class PururinDescriptionAdapter(
    private val controller: MangaController,
) :
    RecyclerView.Adapter<PururinDescriptionAdapter.PururinDescriptionViewHolder>() {

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
            val meta = controller.presenter.meta.value
            if (meta == null || meta !is PururinSearchMetadata) return

            binding.genre.text = meta.tags.find { it.namespace == PururinSearchMetadata.TAG_NAMESPACE_CATEGORY }.let { genre ->
                genre?.let { MetadataUtil.getGenreAndColour(itemView.context, it.name) }?.let {
                    binding.genre.setBackgroundColor(it.first)
                    it.second
                } ?: genre?.name ?: itemView.context.getString(R.string.unknown)
            }

            binding.uploader.text = meta.uploaderDisp ?: meta.uploader.orEmpty()

            binding.size.text = meta.fileSize ?: itemView.context.getString(R.string.unknown)
            binding.size.bindDrawable(itemView.context, R.drawable.ic_outline_sd_card_24)

            binding.pages.text = itemView.resources.getQuantityString(R.plurals.num_pages, meta.pages ?: 0, meta.pages ?: 0)
            binding.pages.bindDrawable(itemView.context, R.drawable.ic_baseline_menu_book_24)

            val ratingFloat = meta.averageRating?.toFloat()
            binding.ratingBar.rating = ratingFloat ?: 0F
            @SuppressLint("SetTextI18n")
            binding.rating.text = (round((ratingFloat ?: 0F) * 100.0) / 100.0).toString() + " - " + MetadataUtil.getRatingString(itemView.context, ratingFloat?.times(2))

            binding.moreInfo.bindDrawable(itemView.context, R.drawable.ic_info_24dp)

            listOf(
                binding.genre,
                binding.pages,
                binding.rating,
                binding.size,
                binding.uploader,
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
                    ),
                )
            }
        }
    }
}

@Composable
fun PururinDescription(controller: MangaController) {
    val meta by controller.presenter.meta.collectAsState()
    PururinDescription(controller = controller, meta = meta)
}

@Composable
private fun PururinDescription(controller: MangaController, meta: RaisedSearchMetadata?) {
    val context = LocalContext.current
    AndroidView(
        factory = { factoryContext ->
            DescriptionAdapterPuBinding.inflate(LayoutInflater.from(factoryContext)).root
        },
        update = {
            if (meta == null || meta !is PururinSearchMetadata) return@AndroidView
            val binding = DescriptionAdapterPuBinding.bind(it)

            binding.genre.text = meta.tags.find { it.namespace == PururinSearchMetadata.TAG_NAMESPACE_CATEGORY }.let { genre ->
                genre?.let { MetadataUtil.getGenreAndColour(context, it.name) }?.let {
                    binding.genre.setBackgroundColor(it.first)
                    it.second
                } ?: genre?.name ?: context.getString(R.string.unknown)
            }

            binding.uploader.text = meta.uploaderDisp ?: meta.uploader.orEmpty()

            binding.size.text = meta.fileSize ?: context.getString(R.string.unknown)
            binding.size.bindDrawable(context, R.drawable.ic_outline_sd_card_24)

            binding.pages.text = context.resources.getQuantityString(R.plurals.num_pages, meta.pages ?: 0, meta.pages ?: 0)
            binding.pages.bindDrawable(context, R.drawable.ic_baseline_menu_book_24)

            val ratingFloat = meta.averageRating?.toFloat()
            binding.ratingBar.rating = ratingFloat ?: 0F
            @SuppressLint("SetTextI18n")
            binding.rating.text = (round((ratingFloat ?: 0F) * 100.0) / 100.0).toString() + " - " + MetadataUtil.getRatingString(context, ratingFloat?.times(2))

            binding.moreInfo.bindDrawable(context, R.drawable.ic_info_24dp)

            listOf(
                binding.genre,
                binding.pages,
                binding.rating,
                binding.size,
                binding.uploader,
            ).forEach { textView ->
                textView.setOnLongClickListener {
                    context.copyToClipboard(
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
                    ),
                )
            }
        },
    )
}
