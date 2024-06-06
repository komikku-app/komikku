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
import eu.kanade.tachiyomi.databinding.DescriptionAdapterEhBinding
import eu.kanade.tachiyomi.ui.manga.MangaScreenModel.State
import eu.kanade.tachiyomi.util.system.copyToClipboard
import exh.metadata.MetadataUtil
import exh.metadata.metadata.EHentaiSearchMetadata
import exh.ui.metadata.adapters.MetadataUIUtil.bindDrawable
import tachiyomi.core.common.i18n.pluralStringResource
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR

@Composable
fun EHentaiDescription(
    state: State.Success,
    openMetadataViewer: () -> Unit,
    search: (String) -> Unit,
) {
    val context = LocalContext.current
    val textColor = MaterialTheme.colorScheme.secondary.toArgb()
    val iconColor = MaterialTheme.colorScheme.primary.toArgb()
    val ratingBarSecondaryColor = MaterialTheme.colorScheme.outlineVariant.toArgb()
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
                meta.genre?.let { MetadataUIUtil.getGenreAndColour(context, it) }
                    ?.let {
                        binding.genre.setBackgroundColor(it.first)
                        it.second
                    }
                    ?: meta.genre
                    ?: context.stringResource(MR.strings.unknown)
            binding.genre.setTextColor(textColor)

            binding.visible.text = context.stringResource(SYMR.strings.is_visible, meta.visible ?: context.stringResource(MR.strings.unknown))
            binding.visible.setTextColor(textColor)

            binding.favorites.text = (meta.favorites ?: 0).toString()
            binding.favorites.bindDrawable(context, R.drawable.ic_book_24dp, iconColor)
            binding.favorites.setTextColor(textColor)

            binding.uploader.text = meta.uploader ?: context.stringResource(MR.strings.unknown)
            binding.uploader.setTextColor(textColor)

            binding.size.text = MetadataUtil.humanReadableByteCount(meta.size ?: 0, true)
            binding.size.bindDrawable(context, R.drawable.ic_outline_sd_card_24, iconColor)
            binding.size.setTextColor(textColor)

            val length = meta.length ?: 0
            binding.pages.text = context.pluralStringResource(SYMR.plurals.num_pages, length, length)
            binding.pages.bindDrawable(context, R.drawable.ic_baseline_menu_book_24, iconColor)
            binding.pages.setTextColor(textColor)

            val language = meta.language ?: context.stringResource(MR.strings.unknown)
            binding.language.text = if (meta.translated == true) {
                context.stringResource(SYMR.strings.language_translated, language)
            } else {
                language
            }
            binding.language.setTextColor(textColor)

            val ratingFloat = meta.averageRating?.toFloat()
            binding.ratingBar.rating = ratingFloat ?: 0F
            @SuppressLint("SetTextI18n")
            binding.rating.text = (ratingFloat ?: 0F).toString() + " - " + MetadataUIUtil.getRatingString(context, ratingFloat?.times(2))
            binding.ratingBar.supportProgressTintList = ColorStateList.valueOf(iconColor)
            binding.ratingBar.supportSecondaryProgressTintList = ColorStateList.valueOf(ratingBarSecondaryColor)
            binding.rating.setTextColor(textColor)

            binding.moreInfo.bindDrawable(context, R.drawable.ic_info_24dp, iconColor)
            binding.moreInfo.setTextColor(textColor)

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
