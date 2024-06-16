package exh.ui.metadata.adapters

import android.content.Context
import android.graphics.Color
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.FloatRange
import androidx.core.content.ContextCompat
import androidx.core.graphics.alpha
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import eu.kanade.tachiyomi.source.R
import eu.kanade.tachiyomi.util.system.dpToPx
import exh.util.SourceTagsUtil
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.sy.SYMR
import kotlin.math.roundToInt

object MetadataUIUtil {
    fun getRatingString(
        context: Context,
        @FloatRange(from = 0.0, to = 10.0) rating: Float? = null,
    ) = when (rating?.roundToInt()) {
        0 -> SYMR.strings.rating0
        1 -> SYMR.strings.rating1
        2 -> SYMR.strings.rating2
        3 -> SYMR.strings.rating3
        4 -> SYMR.strings.rating4
        5 -> SYMR.strings.rating5
        6 -> SYMR.strings.rating6
        7 -> SYMR.strings.rating7
        8 -> SYMR.strings.rating8
        9 -> SYMR.strings.rating9
        10 -> SYMR.strings.rating10
        else -> SYMR.strings.no_rating
    }.let { context.stringResource(it) }

    fun getGenreAndColour(context: Context, genre: String) = when (genre) {
        "doujinshi", "Doujinshi" -> SourceTagsUtil.GenreColor.DOUJINSHI_COLOR to SYMR.strings.doujinshi
        "manga", "Japanese Manga", "Manga" -> SourceTagsUtil.GenreColor.MANGA_COLOR to SYMR.strings.entry_type_manga
        "artistcg", "artist CG", "artist-cg", "Artist CG" ->
            SourceTagsUtil.GenreColor.ARTIST_CG_COLOR to SYMR.strings.artist_cg
        "gamecg", "game CG", "game-cg", "Game CG" -> SourceTagsUtil.GenreColor.GAME_CG_COLOR to SYMR.strings.game_cg
        "western" -> SourceTagsUtil.GenreColor.WESTERN_COLOR to SYMR.strings.western
        "non-h", "non-H" -> SourceTagsUtil.GenreColor.NON_H_COLOR to SYMR.strings.non_h
        "imageset", "image Set" -> SourceTagsUtil.GenreColor.IMAGE_SET_COLOR to SYMR.strings.image_set
        "cosplay" -> SourceTagsUtil.GenreColor.COSPLAY_COLOR to SYMR.strings.cosplay
        "asianporn", "asian Porn" -> SourceTagsUtil.GenreColor.ASIAN_PORN_COLOR to SYMR.strings.asian_porn
        "misc" -> SourceTagsUtil.GenreColor.MISC_COLOR to SYMR.strings.misc
        "Korean Manhwa" -> SourceTagsUtil.GenreColor.ARTIST_CG_COLOR to SYMR.strings.entry_type_manhwa
        "Chinese Manhua" -> SourceTagsUtil.GenreColor.GAME_CG_COLOR to SYMR.strings.entry_type_manhua
        "Comic" -> SourceTagsUtil.GenreColor.WESTERN_COLOR to SYMR.strings.entry_type_comic
        "artbook" -> SourceTagsUtil.GenreColor.IMAGE_SET_COLOR to SYMR.strings.artbook
        "webtoon" -> SourceTagsUtil.GenreColor.NON_H_COLOR to SYMR.strings.entry_type_webtoon
        "Video" -> SourceTagsUtil.GenreColor.WESTERN_COLOR to SYMR.strings.video
        else -> null
    }?.let { (genreColor, stringId) ->
        genreColor.color to context.stringResource(stringId)
    }

    fun TextView.bindDrawable(
        context: Context,
        @DrawableRes drawable: Int,
        // KMK -->
        @ColorInt color: Int = context.getResourceColor(R.attr.colorAccent),
        // KMK <--
    ) {
        ContextCompat.getDrawable(context, drawable)?.apply {
            // KMK -->
            setTint(color)
            // KMK <--
            setBounds(0, 0, 20.dpToPx, 20.dpToPx)
            setCompoundDrawables(this, null, null, null)
        }
    }

    /**
     * Returns the color for the given attribute.
     *
     * @param resource the attribute.
     * @param alphaFactor the alpha number [0,1].
     */
    @ColorInt
    fun Context.getResourceColor(@AttrRes resource: Int, alphaFactor: Float = 1f): Int {
        val typedArray = obtainStyledAttributes(intArrayOf(resource))
        val color = typedArray.getColor(0, 0)
        typedArray.recycle()

        if (alphaFactor < 1f) {
            val alpha = (color.alpha * alphaFactor).roundToInt()
            return Color.argb(alpha, color.red, color.green, color.blue)
        }

        return color
    }
}
