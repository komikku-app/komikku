package exh.ui.metadata.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.DescriptionAdapterNhBinding
import eu.kanade.tachiyomi.ui.manga.MangaScreenState
import eu.kanade.tachiyomi.util.system.copyToClipboard
import exh.metadata.MetadataUtil
import exh.metadata.bindDrawable
import exh.metadata.metadata.NHentaiSearchMetadata
import java.util.Date

@Composable
fun NHentaiDescription(state: MangaScreenState.Success, openMetadataViewer: () -> Unit) {
    val context = LocalContext.current
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { factoryContext ->
            DescriptionAdapterNhBinding.inflate(LayoutInflater.from(factoryContext)).root
        },
        update = {
            val meta = state.meta
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
                openMetadataViewer()
            }
        },
    )
}
