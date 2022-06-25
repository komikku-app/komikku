package exh.ui.metadata.adapters

import android.view.LayoutInflater
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.DescriptionAdapter8mBinding
import eu.kanade.tachiyomi.ui.manga.MangaScreenState
import eu.kanade.tachiyomi.util.system.copyToClipboard
import exh.metadata.bindDrawable
import exh.metadata.metadata.EightMusesSearchMetadata

@Composable
fun EightMusesDescription(state: MangaScreenState.Success, openMetadataViewer: () -> Unit) {
    val context = LocalContext.current
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { factoryContext ->
            DescriptionAdapter8mBinding.inflate(LayoutInflater.from(factoryContext)).root
        },
        update = {
            val meta = state.meta
            if (meta == null || meta !is EightMusesSearchMetadata) return@AndroidView
            val binding = DescriptionAdapter8mBinding.bind(it)

            binding.title.text = meta.title ?: context.getString(R.string.unknown)

            binding.moreInfo.bindDrawable(context, R.drawable.ic_info_24dp)

            binding.title.setOnLongClickListener {
                context.copyToClipboard(
                    binding.title.text.toString(),
                    binding.title.text.toString(),
                )
                true
            }

            binding.moreInfo.setOnClickListener {
                openMetadataViewer()
            }
        },
    )
}
