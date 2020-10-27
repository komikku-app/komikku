package eu.kanade.tachiyomi.ui.browse.source.browse

import android.graphics.Color
import android.view.View
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.glide.GlideApp
import eu.kanade.tachiyomi.data.glide.toMangaThumbnail
import eu.kanade.tachiyomi.util.system.getResourceColor
import exh.metadata.MetadataUtil
import exh.metadata.metadata.EHentaiSearchMetadata
import exh.metadata.metadata.base.RaisedSearchMetadata
import exh.util.SourceTagsUtil
import kotlinx.android.synthetic.main.source_enhanced_ehentai_list_item.date_posted
import kotlinx.android.synthetic.main.source_enhanced_ehentai_list_item.genre
import kotlinx.android.synthetic.main.source_enhanced_ehentai_list_item.language
import kotlinx.android.synthetic.main.source_enhanced_ehentai_list_item.rating_bar
import kotlinx.android.synthetic.main.source_enhanced_ehentai_list_item.thumbnail
import kotlinx.android.synthetic.main.source_enhanced_ehentai_list_item.title
import kotlinx.android.synthetic.main.source_enhanced_ehentai_list_item.uploader
import java.util.Date

/**
 * Class used to hold the displayed data of a manga in the catalogue, like the cover or the title.
 * All the elements from the layout file "item_catalogue_list" are available in this class.
 *
 * @param view the inflated view for this holder.
 * @param adapter the adapter handling this holder.
 * @constructor creates a new catalogue holder.
 */
class SourceEnhancedEHentaiListHolder(private val view: View, adapter: FlexibleAdapter<*>) :
    SourceHolder(view, adapter) {

    private val favoriteColor = view.context.getResourceColor(R.attr.colorOnSurface, 0.38f)
    private val unfavoriteColor = view.context.getResourceColor(R.attr.colorOnSurface)

    /**
     * Method called from [CatalogueAdapter.onBindViewHolder]. It updates the data for this
     * holder with the given manga.
     *
     * @param manga the manga to bind.
     */
    override fun onSetValues(manga: Manga) {
        title.text = manga.title
        title.setTextColor(if (manga.favorite) favoriteColor else unfavoriteColor)

        // Set alpha of thumbnail.
        thumbnail.alpha = if (manga.favorite) 0.3f else 1.0f

        setImage(manga)
    }

    override fun onSetMetadataValues(manga: Manga, metadata: RaisedSearchMetadata) {
        if (metadata !is EHentaiSearchMetadata) return

        if (metadata.uploader != null) {
            uploader.text = metadata.uploader
        }

        val pair = when (metadata.genre) {
            "doujinshi" -> SourceTagsUtil.DOUJINSHI_COLOR to R.string.doujinshi
            "manga" -> SourceTagsUtil.MANGA_COLOR to R.string.manga
            "artistcg" -> SourceTagsUtil.ARTIST_CG_COLOR to R.string.artist_cg
            "gamecg" -> SourceTagsUtil.GAME_CG_COLOR to R.string.game_cg
            "western" -> SourceTagsUtil.WESTERN_COLOR to R.string.western
            "non-h" -> SourceTagsUtil.NON_H_COLOR to R.string.non_h
            "imageset" -> SourceTagsUtil.IMAGE_SET_COLOR to R.string.image_set
            "cosplay" -> SourceTagsUtil.COSPLAY_COLOR to R.string.cosplay
            "asianporn" -> SourceTagsUtil.ASIAN_PORN_COLOR to R.string.asian_porn
            "misc" -> SourceTagsUtil.MISC_COLOR to R.string.misc
            else -> "" to 0
        }

        if (pair.first.isNotBlank()) {
            genre.setBackgroundColor(Color.parseColor(pair.first))
            genre.text = view.context.getString(pair.second)
        } else genre.text = metadata.genre

        metadata.datePosted?.let { date_posted.text = MetadataUtil.EX_DATE_FORMAT.format(Date(it)) }

        metadata.averageRating?.let { rating_bar.rating = it.toFloat() }

        val locale = SourceTagsUtil.getLocaleSourceUtil(metadata.tags.firstOrNull { it.namespace == "language" }?.name)
        val pageCount = metadata.length

        language.text = if (locale != null && pageCount != null) {
            view.resources.getQuantityString(R.plurals.browse_language_and_pages, pageCount, pageCount, locale.toLanguageTag().toUpperCase())
        } else if (pageCount != null) {
            view.resources.getQuantityString(R.plurals.num_pages, pageCount, pageCount)
        } else locale?.toLanguageTag()?.toUpperCase()
    }

    override fun setImage(manga: Manga) {
        GlideApp.with(view.context).clear(thumbnail)

        if (!manga.thumbnail_url.isNullOrEmpty()) {
            val radius = view.context.resources.getDimensionPixelSize(R.dimen.card_radius)
            val requestOptions = RequestOptions().transform(CenterCrop(), RoundedCorners(radius))
            GlideApp.with(view.context)
                .load(manga.toMangaThumbnail())
                .diskCacheStrategy(DiskCacheStrategy.DATA)
                .apply(requestOptions)
                .dontAnimate()
                .placeholder(android.R.color.transparent)
                .into(thumbnail)
        }
    }
}
