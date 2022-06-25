package exh.ui.metadata.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.DescriptionAdapterPeBinding
import eu.kanade.tachiyomi.ui.manga.MangaScreenState
import eu.kanade.tachiyomi.util.system.copyToClipboard
import exh.metadata.MetadataUtil
import exh.metadata.bindDrawable
import exh.metadata.metadata.PervEdenSearchMetadata
import java.util.Locale
import kotlin.math.round

@Composable
fun PervEdenDescription(state: MangaScreenState.Success, openMetadataViewer: () -> Unit) {
    val context = LocalContext.current
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { factoryContext ->
            DescriptionAdapterPeBinding.inflate(LayoutInflater.from(factoryContext)).root
        },
        update = {
            val meta = state.meta
            if (meta == null || meta !is PervEdenSearchMetadata) return@AndroidView
            val binding = DescriptionAdapterPeBinding.bind(it)

            binding.genre.text = meta.genre?.let { MetadataUtil.getGenreAndColour(context, it) }?.let {
                binding.genre.setBackgroundColor(it.first)
                it.second
            } ?: meta.genre ?: context.getString(R.string.unknown)

            val language = meta.lang
            binding.language.text = if (language != null) {
                val local = Locale(language)
                local.displayName
            } else context.getString(R.string.unknown)

            binding.ratingBar.rating = meta.rating ?: 0F
            @SuppressLint("SetTextI18n")
            binding.rating.text = (round((meta.rating ?: 0F) * 100.0) / 100.0).toString() + " - " + MetadataUtil.getRatingString(context, meta.rating?.times(2))

            binding.moreInfo.bindDrawable(context, R.drawable.ic_info_24dp)

            listOf(
                binding.genre,
                binding.language,
                binding.rating,
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
