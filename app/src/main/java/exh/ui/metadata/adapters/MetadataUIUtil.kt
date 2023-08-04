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
import kotlin.math.roundToInt

object MetadataUIUtil {
    fun getRatingString(context: Context, @FloatRange(from = 0.0, to = 10.0) rating: Float? = null) = when (rating?.roundToInt()) {
        0 -> R.string.rating0
        1 -> R.string.rating1
        2 -> R.string.rating2
        3 -> R.string.rating3
        4 -> R.string.rating4
        5 -> R.string.rating5
        6 -> R.string.rating6
        7 -> R.string.rating7
        8 -> R.string.rating8
        9 -> R.string.rating9
        10 -> R.string.rating10
        else -> R.string.no_rating
    }.let { context.getString(it) }

    fun getGenreAndColour(context: Context, genre: String) = when (genre) {
        "doujinshi", "Doujinshi" -> SourceTagsUtil.GenreColor.DOUJINSHI_COLOR to R.string.doujinshi
        "manga", "Japanese Manga", "Manga" -> SourceTagsUtil.GenreColor.MANGA_COLOR to R.string.entry_type_manga
        "artistcg", "artist CG", "artist-cg", "Artist CG" -> SourceTagsUtil.GenreColor.ARTIST_CG_COLOR to R.string.artist_cg
        "gamecg", "game CG", "game-cg", "Game CG" -> SourceTagsUtil.GenreColor.GAME_CG_COLOR to R.string.game_cg
        "western" -> SourceTagsUtil.GenreColor.WESTERN_COLOR to R.string.western
        "non-h", "non-H" -> SourceTagsUtil.GenreColor.NON_H_COLOR to R.string.non_h
        "imageset", "image Set" -> SourceTagsUtil.GenreColor.IMAGE_SET_COLOR to R.string.image_set
        "cosplay" -> SourceTagsUtil.GenreColor.COSPLAY_COLOR to R.string.cosplay
        "asianporn", "asian Porn" -> SourceTagsUtil.GenreColor.ASIAN_PORN_COLOR to R.string.asian_porn
        "misc" -> SourceTagsUtil.GenreColor.MISC_COLOR to R.string.misc
        "Korean Manhwa" -> SourceTagsUtil.GenreColor.ARTIST_CG_COLOR to R.string.entry_type_manhwa
        "Chinese Manhua" -> SourceTagsUtil.GenreColor.GAME_CG_COLOR to R.string.entry_type_manhua
        "Comic" -> SourceTagsUtil.GenreColor.WESTERN_COLOR to R.string.entry_type_comic
        "artbook" -> SourceTagsUtil.GenreColor.IMAGE_SET_COLOR to R.string.artbook
        "webtoon" -> SourceTagsUtil.GenreColor.NON_H_COLOR to R.string.entry_type_webtoon
        "Video" -> SourceTagsUtil.GenreColor.WESTERN_COLOR to R.string.video
        else -> null
    }?.let { (genreColor, stringId) ->
        genreColor.color to context.getString(stringId)
    }

    fun TextView.bindDrawable(context: Context, @DrawableRes drawable: Int) {
        ContextCompat.getDrawable(context, drawable)?.apply {
            setTint(context.getResourceColor(R.attr.colorAccent))
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
