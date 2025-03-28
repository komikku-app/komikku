package exh.ui.metadata.adapters

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.view.LayoutInflater
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import eu.kanade.presentation.theme.colorscheme.AndroidViewColorScheme
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.DescriptionAdapterPuBinding
import eu.kanade.tachiyomi.ui.manga.MangaScreenModel.State
import eu.kanade.tachiyomi.util.system.copyToClipboard
import exh.metadata.metadata.PururinSearchMetadata
import exh.ui.metadata.adapters.MetadataUIUtil.bindDrawable
import exh.util.SourceTagsUtil.genreTextColor
import tachiyomi.core.common.i18n.pluralStringResource
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import kotlin.math.round

@Composable
fun PururinDescription(state: State.Success, openMetadataViewer: () -> Unit) {
    val context = LocalContext.current
    // KMK -->
    val colorScheme = AndroidViewColorScheme(MaterialTheme.colorScheme)
    val iconColor = colorScheme.iconColor
    val ratingBarColor = colorScheme.ratingBarColor
    val ratingBarSecondaryColor = colorScheme.ratingBarSecondaryColor
    val textColor = LocalContentColor.current.toArgb()
    // KMK <--
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { factoryContext ->
            DescriptionAdapterPuBinding.inflate(LayoutInflater.from(factoryContext)).root
        },
        update = {
            val meta = state.meta
            if (meta == null || meta !is PururinSearchMetadata) return@AndroidView
            val binding = DescriptionAdapterPuBinding.bind(it)

            binding.genre.text =
                meta.tags.find { it.namespace == PururinSearchMetadata.TAG_NAMESPACE_CATEGORY }.let { genre ->
                    genre?.let { tag -> MetadataUIUtil.getGenreAndColour(context, tag.name) }
                        ?.let { (genre, name) ->
                            binding.genre.setBackgroundColor(genre.color)
                            // KMK -->
                            binding.genre.setTextColor(genreTextColor(genre))
                            // KMK <--
                            name
                        } ?: genre?.name ?: context.stringResource(MR.strings.unknown)
                }

            binding.uploader.text = meta.uploaderDisp ?: meta.uploader.orEmpty()
            // KMK -->
            binding.uploader.setTextColor(textColor)
            // KMK <--

            binding.size.text = meta.fileSize ?: context.stringResource(MR.strings.unknown)
            // KMK -->
            binding.size.bindDrawable(context, R.drawable.ic_outline_sd_card_24, iconColor)
            binding.size.setTextColor(textColor)
            // KMK <--

            binding.pages.text = context.pluralStringResource(SYMR.plurals.num_pages, meta.pages ?: 0, meta.pages ?: 0)
            // KMK -->
            binding.pages.bindDrawable(context, R.drawable.ic_baseline_menu_book_24, iconColor)
            binding.pages.setTextColor(textColor)
            // KMK <--

            val ratingFloat = meta.averageRating?.toFloat()
            binding.ratingBar.rating = ratingFloat ?: 0F
            @SuppressLint("SetTextI18n")
            binding.rating.text =
                (round((ratingFloat ?: 0F) * 100.0) / 100.0).toString() + " - " +
                MetadataUIUtil.getRatingString(context, ratingFloat?.times(2))
            // KMK -->
            binding.ratingBar.supportProgressTintList = ColorStateList.valueOf(ratingBarColor)
            binding.ratingBar.supportSecondaryProgressTintList = ColorStateList.valueOf(ratingBarSecondaryColor)
            binding.rating.setTextColor(textColor)

            binding.moreInfo.bindDrawable(context, R.drawable.ic_info_24dp, iconColor)
            binding.moreInfo.text = context.stringResource(SYMR.strings.more_info)
            binding.moreInfo.setTextColor(iconColor)
            // KMK <--

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
                openMetadataViewer()
            }
        },
    )
}
