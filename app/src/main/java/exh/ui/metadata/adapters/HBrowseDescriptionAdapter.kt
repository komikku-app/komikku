package exh.ui.metadata.adapters

import android.view.LayoutInflater
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.DescriptionAdapterHbBinding
import eu.kanade.tachiyomi.ui.manga.MangaScreenState
import eu.kanade.tachiyomi.util.system.copyToClipboard
import exh.metadata.bindDrawable
import exh.metadata.metadata.HBrowseSearchMetadata

@Composable
fun HBrowseDescription(state: MangaScreenState.Success, openMetadataViewer: () -> Unit) {
    val context = LocalContext.current
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { factoryContext ->
            DescriptionAdapterHbBinding.inflate(LayoutInflater.from(factoryContext)).root
        },
        update = {
            val meta = state.meta
            if (meta == null || meta !is HBrowseSearchMetadata) return@AndroidView
            val binding = DescriptionAdapterHbBinding.bind(it)

            binding.pages.text = context.resources.getQuantityString(R.plurals.num_pages, meta.length ?: 0, meta.length ?: 0)
            binding.pages.bindDrawable(context, R.drawable.ic_baseline_menu_book_24)

            binding.moreInfo.bindDrawable(context, R.drawable.ic_info_24dp)

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
