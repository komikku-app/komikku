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
import eu.kanade.tachiyomi.databinding.DescriptionAdapter8mBinding
import eu.kanade.tachiyomi.ui.manga.MangaScreenModel.State
import eu.kanade.tachiyomi.util.system.copyToClipboard
import exh.metadata.metadata.EightMusesSearchMetadata
import exh.ui.metadata.adapters.MetadataUIUtil.bindDrawable
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR

@Composable
fun EightMusesDescription(state: State.Success, openMetadataViewer: () -> Unit) {
    val context = LocalContext.current
    val onBackgroundColor = MaterialTheme.colorScheme.onBackground.toArgb()
    val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { factoryContext ->
            DescriptionAdapter8mBinding.inflate(LayoutInflater.from(factoryContext)).root
        },
        update = {
            val meta = state.meta
            if (meta == null || meta !is EightMusesSearchMetadata) return@AndroidView
            val binding = DescriptionAdapter8mBinding.bind(it)

            binding.title.text = meta.title ?: context.stringResource(MR.strings.unknown)
            binding.title.setTextColor(onBackgroundColor)

            binding.moreInfo.bindDrawable(context, R.drawable.ic_info_24dp, primaryColor)
            binding.moreInfo.setTextColor(onBackgroundColor)

            binding.title.setOnLongClickListener {
                context.copyToClipboard(
                    binding.title.text.toString(),
                    binding.title.text.toString(),
                )
                true
            }
            binding.title.setTextColor(onBackgroundColor)

            binding.moreInfo.setOnClickListener {
                openMetadataViewer()
            }
        },
    )
}
