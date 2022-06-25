package exh.ui.metadata.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.DescriptionAdapterEhBinding
import eu.kanade.tachiyomi.ui.manga.MangaScreenState
import eu.kanade.tachiyomi.util.system.copyToClipboard
import exh.metadata.MetadataUtil
import exh.metadata.bindDrawable
import exh.metadata.metadata.EHentaiSearchMetadata

@Composable
fun EHentaiDescription(state: MangaScreenState.Success, openMetadataViewer: () -> Unit, search: (String) -> Unit) {
    val context = LocalContext.current
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { factoryContext ->
            DescriptionAdapterEhBinding.inflate(LayoutInflater.from(factoryContext)).root
        },
        update = {
            val meta = state.meta
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
                meta.uploader?.let { search("uploader:\"$it\"") }
            }

            binding.moreInfo.setOnClickListener {
                openMetadataViewer()
            }
        },
    )
}
