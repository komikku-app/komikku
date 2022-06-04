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
import eu.kanade.tachiyomi.databinding.DescriptionAdapterNhBinding
import eu.kanade.tachiyomi.ui.base.controller.pushController
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.util.system.copyToClipboard
import exh.metadata.MetadataUtil
import exh.metadata.bindDrawable
import exh.metadata.metadata.NHentaiSearchMetadata
import exh.metadata.metadata.base.RaisedSearchMetadata
import exh.ui.metadata.MetadataViewController
import java.util.Date

class NHentaiDescriptionAdapter(
    private val controller: MangaController,
) :
    RecyclerView.Adapter<NHentaiDescriptionAdapter.NHentaiDescriptionViewHolder>() {

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
            val meta = controller.presenter.meta.value
            if (meta == null || meta !is NHentaiSearchMetadata) return

            binding.genre.text = meta.tags.filter { it.namespace == NHentaiSearchMetadata.NHENTAI_CATEGORIES_NAMESPACE }.let { tags ->
                if (tags.isNotEmpty()) tags.joinToString(transform = { it.name }) else null
            }.let { categoriesString ->
                categoriesString?.let { MetadataUtil.getGenreAndColour(itemView.context, it) }?.let {
                    binding.genre.setBackgroundColor(it.first)
                    it.second
                } ?: categoriesString ?: itemView.context.getString(R.string.unknown)
            }

            meta.favoritesCount?.let {
                if (it == 0L) return@let
                binding.favorites.text = it.toString()
                binding.favorites.bindDrawable(itemView.context, R.drawable.ic_book_24dp)
            }

            binding.whenPosted.text = MetadataUtil.EX_DATE_FORMAT.format(Date((meta.uploadDate ?: 0) * 1000))

            binding.pages.text = itemView.resources.getQuantityString(R.plurals.num_pages, meta.pageImageTypes.size, meta.pageImageTypes.size)
            binding.pages.bindDrawable(itemView.context, R.drawable.ic_baseline_menu_book_24)

            @SuppressLint("SetTextI18n")
            binding.id.text = "#" + (meta.nhId ?: 0)

            binding.moreInfo.bindDrawable(itemView.context, R.drawable.ic_info_24dp)

            listOf(
                binding.favorites,
                binding.genre,
                binding.id,
                binding.pages,
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
                    ),
                )
            }
        }
    }
}

@Composable
fun NHentaiDescription(controller: MangaController) {
    val meta by controller.presenter.meta.collectAsState()
    NHentaiDescription(controller = controller, meta = meta)
}

@Composable
private fun NHentaiDescription(controller: MangaController, meta: RaisedSearchMetadata?) {
    val context = LocalContext.current
    AndroidView(
        factory = { factoryContext ->
            DescriptionAdapterNhBinding.inflate(LayoutInflater.from(factoryContext)).root
        },
        update = {
            if (meta == null || meta !is NHentaiSearchMetadata) return@AndroidView
            val binding = DescriptionAdapterNhBinding.bind(it)

            binding.genre.text = meta.tags.filter { it.namespace == NHentaiSearchMetadata.NHENTAI_CATEGORIES_NAMESPACE }.let { tags ->
                if (tags.isNotEmpty()) tags.joinToString(transform = { it.name }) else null
            }.let { categoriesString ->
                categoriesString?.let { MetadataUtil.getGenreAndColour(context, it) }?.let {
                    binding.genre.setBackgroundColor(it.first)
                    it.second
                } ?: categoriesString ?: context.getString(R.string.unknown)
            }

            meta.favoritesCount?.let {
                if (it == 0L) return@let
                binding.favorites.text = it.toString()
                binding.favorites.bindDrawable(context, R.drawable.ic_book_24dp)
            }

            binding.whenPosted.text = MetadataUtil.EX_DATE_FORMAT.format(Date((meta.uploadDate ?: 0) * 1000))

            binding.pages.text = context.resources.getQuantityString(R.plurals.num_pages, meta.pageImageTypes.size, meta.pageImageTypes.size)
            binding.pages.bindDrawable(context, R.drawable.ic_baseline_menu_book_24)

            @SuppressLint("SetTextI18n")
            binding.id.text = "#" + (meta.nhId ?: 0)

            binding.moreInfo.bindDrawable(context, R.drawable.ic_info_24dp)

            listOf(
                binding.favorites,
                binding.genre,
                binding.id,
                binding.pages,
                binding.whenPosted,
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
