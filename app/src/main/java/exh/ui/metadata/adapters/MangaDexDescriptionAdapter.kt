package exh.ui.metadata.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.isVisible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.DescriptionAdapterMdBinding
import eu.kanade.tachiyomi.ui.manga.MangaScreenState
import eu.kanade.tachiyomi.util.system.copyToClipboard
import exh.metadata.MetadataUtil.getRatingString
import exh.metadata.bindDrawable
import exh.metadata.metadata.MangaDexSearchMetadata
import kotlin.math.round

@Composable
fun MangaDexDescription(state: MangaScreenState.Success, openMetadataViewer: () -> Unit) {
    val context = LocalContext.current
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { factoryContext ->
            DescriptionAdapterMdBinding.inflate(LayoutInflater.from(factoryContext)).root
        },
        update = {
            val meta = state.meta
            if (meta == null || meta !is MangaDexSearchMetadata) return@AndroidView
            val binding = DescriptionAdapterMdBinding.bind(it)

            // todo
            val ratingFloat = meta.rating
            binding.ratingBar.rating = ratingFloat?.div(2F) ?: 0F
            @SuppressLint("SetTextI18n")
            binding.rating.text = (round((ratingFloat ?: 0F) * 100.0) / 100.0).toString() + " - " + getRatingString(context, ratingFloat)
            binding.rating.isVisible = ratingFloat != null
            binding.ratingBar.isVisible = ratingFloat != null

            binding.moreInfo.bindDrawable(context, R.drawable.ic_info_24dp)

            binding.rating.setOnLongClickListener {
                context.copyToClipboard(
                    binding.rating.text.toString(),
                    binding.rating.text.toString(),
                )
                true
            }

            binding.moreInfo.setOnClickListener {
                openMetadataViewer()
            }
        },
    )
}
