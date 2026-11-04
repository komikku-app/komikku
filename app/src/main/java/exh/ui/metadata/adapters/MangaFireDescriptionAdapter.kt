package exh.ui.metadata.adapters

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.view.LayoutInflater
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.isVisible
import eu.kanade.presentation.theme.colorscheme.AndroidViewColorScheme
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.DescriptionAdapterMdBinding
import eu.kanade.tachiyomi.source.mangafire.MangaFireOverlayMetadata
import eu.kanade.tachiyomi.ui.manga.MangaScreenModel.State
import eu.kanade.tachiyomi.util.system.copyToClipboard
import exh.ui.metadata.adapters.MetadataUIUtil.bindDrawable
import exh.ui.metadata.adapters.MetadataUIUtil.getRatingString
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.round

fun MangaFireOverlayMetadata.hasVisibleStats(): Boolean = rating != null || follows != null

/** Match MangaDex RaisedTag chip style: "Year: 2023", "Content Rating: Suggestive". */
fun MangaFireOverlayMetadata.mergeGenreChips(existing: List<String>?): List<String>? {
    val base = existing.orEmpty()
    val chips = buildList {
        year?.let { y ->
            val chip = "Year: $y"
            if (base.none { it.equals(chip, ignoreCase = true) || it.equals(y.toString(), ignoreCase = true) }) {
                add(chip)
            }
        }
        contentRating
            ?.takeUnless { it.equals("safe", ignoreCase = true) }
            ?.let { rating ->
                val chip = "Content Rating: $rating"
                if (base.none { it.equals(chip, ignoreCase = true) || it.equals(rating, ignoreCase = true) }) {
                    add(chip)
                }
            }
    }
    if (chips.isEmpty()) return existing
    return chips + base
}

/** Append alt titles like MangaDex [MdUtil.addAltTitleToDesc]; skip if heading already present. */
fun MangaFireOverlayMetadata.descriptionWithAltTitles(base: String?, heading: String): String? {
    if (altTitles.isEmpty()) return base
    if (!base.isNullOrBlank() && base.contains(heading)) return base
    val block = altTitles.joinToString("\n", "$heading:\n") { "• $it" }
    return if (base.isNullOrBlank()) block else "$base\n\n$block"
}

@Immutable
data class MangaFireDescriptionExtras(
    val description: String?,
    val genres: List<String>?,
)

@Composable
fun rememberMangaFireDescriptionExtras(state: State.Success): MangaFireDescriptionExtras {
    val overlay = state.mangaFireOverlay
    val heading = stringResource(SYMR.strings.alt_titles)
    return remember(state.manga.description, state.manga.genre, overlay, heading) {
        if (overlay == null) {
            MangaFireDescriptionExtras(state.manga.description, state.manga.genre)
        } else {
            MangaFireDescriptionExtras(
                description = overlay.descriptionWithAltTitles(state.manga.description, heading),
                genres = overlay.mergeGenreChips(state.manga.genre),
            )
        }
    }
}

@Composable
fun MangaFireDescription(state: State.Success) {
    val overlay = state.mangaFireOverlay?.takeIf { it.hasVisibleStats() } ?: return
    val context = LocalContext.current
    val colorScheme = AndroidViewColorScheme(MaterialTheme.colorScheme)
    val iconColor = colorScheme.iconColor
    val ratingBarColor = colorScheme.ratingBarColor
    val ratingBarSecondaryColor = colorScheme.ratingBarSecondaryColor
    val textColor = LocalContentColor.current.toArgb()

    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { factoryContext ->
            DescriptionAdapterMdBinding.inflate(LayoutInflater.from(factoryContext)).root
        },
        update = {
            val binding = DescriptionAdapterMdBinding.bind(it)
            val ratingFloat = overlay.rating
            binding.ratingBar.rating = ratingFloat?.div(2F) ?: 0F
            @SuppressLint("SetTextI18n")
            binding.rating.text = buildString {
                append(round((ratingFloat ?: 0F) * 100.0) / 100.0)
                overlay.voteCount?.let { votes -> append(" ($votes)") }
                append(" - ")
                append(getRatingString(context, ratingFloat))
            }
            binding.rating.isVisible = ratingFloat != null
            binding.ratingBar.isVisible = ratingFloat != null
            binding.ratingBar.supportProgressTintList = ColorStateList.valueOf(ratingBarColor)
            binding.ratingBar.supportSecondaryProgressTintList = ColorStateList.valueOf(ratingBarSecondaryColor)
            binding.rating.setTextColor(textColor)

            val followsCount = overlay.follows
            binding.follows.text = followsCount?.toString().orEmpty()
            binding.follows.isVisible = followsCount != null
            if (followsCount != null) {
                binding.follows.bindDrawable(context, R.drawable.ic_book_24dp, iconColor)
                binding.follows.setTextColor(textColor)
            }

            binding.moreInfo.isVisible = false

            listOf(binding.rating, binding.follows).forEach { textView ->
                textView.setOnLongClickListener {
                    context.copyToClipboard(
                        textView.text.toString(),
                        textView.text.toString(),
                    )
                    true
                }
            }
        },
    )
}
