package exh.ui.metadata.adapters

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
import eu.kanade.tachiyomi.databinding.DescriptionAdapterHbBinding
import eu.kanade.tachiyomi.ui.manga.MangaScreenModel.State
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.dpToPx
import exh.metadata.metadata.HBrowseSearchMetadata
import exh.ui.metadata.adapters.MetadataUIUtil.bindDrawable
import tachiyomi.core.common.i18n.pluralStringResource
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.sy.SYMR

@Composable
fun HBrowseDescription(state: State.Success, openMetadataViewer: () -> Unit) {
    val context = LocalContext.current
    // KMK -->
    val colorScheme = AndroidViewColorScheme(MaterialTheme.colorScheme)
    val iconColor = colorScheme.iconColor
    val textColor = LocalContentColor.current.toArgb()
    // KMK <--
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { factoryContext ->
            DescriptionAdapterHbBinding.inflate(LayoutInflater.from(factoryContext)).root
        },
        update = {
            val meta = state.meta
            if (meta == null || meta !is HBrowseSearchMetadata) return@AndroidView
            val binding = DescriptionAdapterHbBinding.bind(it)

            binding.pages.text =
                context.pluralStringResource(SYMR.plurals.num_pages, meta.length ?: 0, meta.length ?: 0)
            // KMK -->
            binding.pages.bindDrawable(context, R.drawable.ic_baseline_menu_book_24, iconColor, 4.dpToPx)
            binding.pages.setTextColor(textColor)

            binding.moreInfo.bindDrawable(context, R.drawable.ic_info_24dp, iconColor)
            binding.moreInfo.text = context.stringResource(SYMR.strings.more_info)
            binding.moreInfo.setTextColor(iconColor)
            // KMK <--

            binding.pages.setOnLongClickListener {
                context.copyToClipboard(
                    binding.pages.text.toString(),
                    binding.pages.text.toString(),
                )
                true
            }

            binding.moreInfo.setOnClickListener {
                openMetadataViewer()
            }
        },
    )
}
