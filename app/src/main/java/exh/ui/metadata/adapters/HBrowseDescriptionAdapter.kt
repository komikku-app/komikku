package exh.ui.metadata.adapters

import android.view.LayoutInflater
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.DescriptionAdapterHbBinding
import eu.kanade.tachiyomi.ui.manga.MangaScreenModel.State
import eu.kanade.tachiyomi.util.system.copyToClipboard
import exh.metadata.metadata.HBrowseSearchMetadata
import exh.ui.metadata.adapters.MetadataUIUtil.bindDrawable
import tachiyomi.core.common.i18n.pluralStringResource
import tachiyomi.i18n.sy.SYMR

@Composable
fun HBrowseDescription(state: State.Success, openMetadataViewer: () -> Unit) {
    val context = LocalContext.current
    val onBackgroundColor = MaterialTheme.colorScheme.onBackground.toArgb()
    val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { factoryContext ->
            DescriptionAdapterHbBinding.inflate(LayoutInflater.from(factoryContext)).root
        },
        update = {
            val meta = state.meta
            if (meta == null || meta !is HBrowseSearchMetadata) return@AndroidView
            val binding = DescriptionAdapterHbBinding.bind(it)

            binding.pages.text = context.pluralStringResource(SYMR.plurals.num_pages, meta.length ?: 0, meta.length ?: 0)
            binding.pages.bindDrawable(context, R.drawable.ic_baseline_menu_book_24, primaryColor)
            binding.pages.setTextColor(onBackgroundColor)

            binding.moreInfo.bindDrawable(context, R.drawable.ic_info_24dp, primaryColor)
            binding.moreInfo.setTextColor(onBackgroundColor)

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
