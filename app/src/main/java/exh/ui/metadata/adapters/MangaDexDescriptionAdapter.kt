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
import androidx.core.view.isVisible
import eu.kanade.presentation.theme.colorscheme.AndroidViewColorScheme
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.DescriptionAdapterMdBinding
import eu.kanade.tachiyomi.ui.manga.MangaScreenModel.State
import eu.kanade.tachiyomi.util.system.copyToClipboard
import exh.metadata.metadata.MangaDexSearchMetadata
import exh.ui.metadata.adapters.MetadataUIUtil.bindDrawable
import exh.ui.metadata.adapters.MetadataUIUtil.getRatingString
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.sy.SYMR
import kotlin.math.round

@Composable
fun MangaDexDescription(state: State.Success, openMetadataViewer: () -> Unit) {
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
            binding.rating.text =
                (round((ratingFloat ?: 0F) * 100.0) / 100.0).toString() + " - " + getRatingString(context, ratingFloat)
            binding.rating.isVisible = ratingFloat != null
            binding.ratingBar.isVisible = ratingFloat != null
            // KMK -->
            binding.ratingBar.supportProgressTintList = ColorStateList.valueOf(ratingBarColor)
            binding.ratingBar.supportSecondaryProgressTintList = ColorStateList.valueOf(ratingBarSecondaryColor)
            binding.rating.setTextColor(textColor)

            binding.moreInfo.bindDrawable(context, R.drawable.ic_info_24dp, iconColor)
            binding.moreInfo.text = context.stringResource(SYMR.strings.more_info)
            binding.moreInfo.setTextColor(iconColor)
            // KMK <--

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
