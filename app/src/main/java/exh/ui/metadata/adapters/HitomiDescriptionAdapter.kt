package exh.ui.metadata.adapters

import android.view.LayoutInflater
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.DescriptionAdapterHiBinding
import eu.kanade.tachiyomi.ui.manga.MangaScreenState
import eu.kanade.tachiyomi.util.system.copyToClipboard
import exh.metadata.MetadataUtil
import exh.metadata.bindDrawable
import exh.metadata.metadata.HitomiSearchMetadata
import java.util.Date

@Composable
fun HitomiDescription(state: MangaScreenState.Success, openMetadataViewer: () -> Unit) {
    val context = LocalContext.current
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { factoryContext ->
            DescriptionAdapterHiBinding.inflate(LayoutInflater.from(factoryContext)).root
        },
        update = {
            val meta = state.meta
            if (meta == null || meta !is HitomiSearchMetadata) return@AndroidView
            val binding = DescriptionAdapterHiBinding.bind(it)

            binding.genre.text = meta.genre?.let { MetadataUtil.getGenreAndColour(context, it) }?.let {
                binding.genre.setBackgroundColor(it.first)
                it.second
            } ?: meta.genre ?: context.getString(R.string.unknown)

            binding.whenPosted.text = MetadataUtil.EX_DATE_FORMAT.format(Date(meta.uploadDate ?: 0))
            binding.language.text = meta.language ?: context.getString(R.string.unknown)

            binding.moreInfo.bindDrawable(context, R.drawable.ic_info_24dp)

            listOf(
                binding.genre,
                binding.language,
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
