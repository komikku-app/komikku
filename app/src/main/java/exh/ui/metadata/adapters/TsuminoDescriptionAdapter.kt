package exh.ui.metadata.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.DescriptionAdapterTsBinding
import eu.kanade.tachiyomi.ui.manga.MangaScreenState
import eu.kanade.tachiyomi.util.system.copyToClipboard
import exh.metadata.MetadataUtil
import exh.metadata.bindDrawable
import exh.metadata.metadata.TsuminoSearchMetadata
import java.util.Date
import kotlin.math.round

@Composable
fun TsuminoDescription(state: MangaScreenState.Success, openMetadataViewer: () -> Unit) {
    val context = LocalContext.current
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { factoryContext ->
            DescriptionAdapterTsBinding.inflate(LayoutInflater.from(factoryContext)).root
        },
        update = {
            val meta = state.meta
            if (meta == null || meta !is TsuminoSearchMetadata) return@AndroidView
            val binding = DescriptionAdapterTsBinding.bind(it)

            binding.genre.text = meta.category?.let { MetadataUtil.getGenreAndColour(context, it) }?.let {
                binding.genre.setBackgroundColor(it.first)
                it.second
            } ?: meta.category ?: context.getString(R.string.unknown)

            binding.favorites.text = (meta.favorites ?: 0).toString()
            binding.favorites.bindDrawable(context, R.drawable.ic_book_24dp)

            binding.whenPosted.text = TsuminoSearchMetadata.TSUMINO_DATE_FORMAT.format(Date(meta.uploadDate ?: 0))

            binding.uploader.text = meta.uploader ?: context.getString(R.string.unknown)

            binding.pages.text = context.resources.getQuantityString(R.plurals.num_pages, meta.length ?: 0, meta.length ?: 0)
            binding.pages.bindDrawable(context, R.drawable.ic_baseline_menu_book_24)

            binding.ratingBar.rating = meta.averageRating ?: 0F
            @SuppressLint("SetTextI18n")
            binding.rating.text = (round((meta.averageRating ?: 0F) * 100.0) / 100.0).toString() + " - " + MetadataUtil.getRatingString(context, meta.averageRating?.times(2))

            binding.moreInfo.bindDrawable(context, R.drawable.ic_info_24dp)

            listOf(
                binding.favorites,
                binding.genre,
                binding.pages,
                binding.rating,
                binding.uploader,
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
