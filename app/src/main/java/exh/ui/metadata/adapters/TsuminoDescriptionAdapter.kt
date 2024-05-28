package exh.ui.metadata.adapters

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.view.LayoutInflater
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.DescriptionAdapterTsBinding
import eu.kanade.tachiyomi.ui.manga.MangaScreenModel.State
import eu.kanade.tachiyomi.util.system.copyToClipboard
import exh.metadata.metadata.TsuminoSearchMetadata
import exh.ui.metadata.adapters.MetadataUIUtil.bindDrawable
import tachiyomi.core.common.i18n.pluralStringResource
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import java.util.Date
import kotlin.math.round

@Composable
fun TsuminoDescription(state: State.Success, openMetadataViewer: () -> Unit) {
    val context = LocalContext.current
    val onBackgroundColor = MaterialTheme.colorScheme.onBackground.toArgb()
    val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
    val outlineVariantColor = MaterialTheme.colorScheme.outlineVariant.toArgb()
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { factoryContext ->
            DescriptionAdapterTsBinding.inflate(LayoutInflater.from(factoryContext)).root
        },
        update = {
            val meta = state.meta
            if (meta == null || meta !is TsuminoSearchMetadata) return@AndroidView
            val binding = DescriptionAdapterTsBinding.bind(it)

            binding.genre.text = meta.category?.let { MetadataUIUtil.getGenreAndColour(context, it) }?.let {
                binding.genre.setBackgroundColor(it.first)
                it.second
            } ?: meta.category ?: context.stringResource(MR.strings.unknown)
            binding.genre.setTextColor(onBackgroundColor)

            binding.favorites.text = (meta.favorites ?: 0).toString()
            binding.favorites.bindDrawable(context, R.drawable.ic_book_24dp, primaryColor)
            binding.favorites.setTextColor(onBackgroundColor)

            binding.whenPosted.text = TsuminoSearchMetadata.TSUMINO_DATE_FORMAT.format(Date(meta.uploadDate ?: 0))
            binding.whenPosted.setTextColor(onBackgroundColor)

            binding.uploader.text = meta.uploader ?: context.stringResource(MR.strings.unknown)
            binding.uploader.setTextColor(onBackgroundColor)

            binding.pages.text = context.pluralStringResource(SYMR.plurals.num_pages, meta.length ?: 0, meta.length ?: 0)
            binding.pages.bindDrawable(context, R.drawable.ic_baseline_menu_book_24, primaryColor)
            binding.pages.setTextColor(onBackgroundColor)

            binding.ratingBar.rating = meta.averageRating ?: 0F
            @SuppressLint("SetTextI18n")
            binding.rating.text = (round((meta.averageRating ?: 0F) * 100.0) / 100.0).toString() + " - " + MetadataUIUtil.getRatingString(context, meta.averageRating?.times(2))
            binding.ratingBar.setSupportProgressTintList(ColorStateList.valueOf(primaryColor))
            binding.ratingBar.setSupportSecondaryProgressTintList(ColorStateList.valueOf(outlineVariantColor))
            binding.rating.setTextColor(onBackgroundColor)

            binding.moreInfo.bindDrawable(context, R.drawable.ic_info_24dp, primaryColor)
            binding.moreInfo.setTextColor(onBackgroundColor)

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
