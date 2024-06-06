package exh.ui.metadata.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.DescriptionAdapterNhBinding
import eu.kanade.tachiyomi.ui.manga.MangaScreenModel.State
import eu.kanade.tachiyomi.util.system.copyToClipboard
import exh.metadata.MetadataUtil
import exh.metadata.metadata.NHentaiSearchMetadata
import exh.ui.metadata.adapters.MetadataUIUtil.bindDrawable
import tachiyomi.core.common.i18n.pluralStringResource
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

@Composable
fun NHentaiDescription(state: State.Success, openMetadataViewer: () -> Unit) {
    val context = LocalContext.current
    val textColor = MaterialTheme.colorScheme.secondary.toArgb()
    val iconColor = MaterialTheme.colorScheme.primary.toArgb()
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { factoryContext ->
            DescriptionAdapterNhBinding.inflate(LayoutInflater.from(factoryContext)).root
        },
        update = {
            val meta = state.meta
            if (meta == null || meta !is NHentaiSearchMetadata) return@AndroidView
            val binding = DescriptionAdapterNhBinding.bind(it)

            binding.genre.text = meta.tags.filter {
                it.namespace == NHentaiSearchMetadata.NHENTAI_CATEGORIES_NAMESPACE
            }.let { tags ->
                if (tags.isNotEmpty()) tags.joinToString(transform = { it.name }) else null
            }.let { categoriesString ->
                categoriesString?.let { MetadataUIUtil.getGenreAndColour(context, it) }?.let {
                    binding.genre.setBackgroundColor(it.first)
                    it.second
                } ?: categoriesString ?: context.stringResource(MR.strings.unknown)
            }
            binding.genre.setTextColor(textColor)

            meta.favoritesCount?.let {
                if (it == 0L) return@let
                binding.favorites.text = it.toString()
                binding.favorites.bindDrawable(context, R.drawable.ic_book_24dp, iconColor)
                binding.favorites.setTextColor(textColor)
            }

            binding.whenPosted.text = MetadataUtil.EX_DATE_FORMAT
                .format(
                    ZonedDateTime
                        .ofInstant(Instant.ofEpochSecond(meta.uploadDate ?: 0), ZoneId.systemDefault())
                )
            binding.whenPosted.setTextColor(textColor)

            binding.pages.text = context.pluralStringResource(
                SYMR.plurals.num_pages,
                meta.pageImageTypes.size,
                meta.pageImageTypes.size,
            )
            binding.pages.bindDrawable(context, R.drawable.ic_baseline_menu_book_24, iconColor)
            binding.pages.setTextColor(textColor)

            @SuppressLint("SetTextI18n")
            binding.id.text = "#" + (meta.nhId ?: 0)
            binding.id.setTextColor(textColor)

            binding.moreInfo.bindDrawable(context, R.drawable.ic_info_24dp, iconColor)
            binding.moreInfo.setTextColor(textColor)

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
