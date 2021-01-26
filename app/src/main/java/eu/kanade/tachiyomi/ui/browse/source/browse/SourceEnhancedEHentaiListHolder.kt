package eu.kanade.tachiyomi.ui.browse.source.browse

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
import eu.kanade.tachiyomi.databinding.SourceEnhancedEhentaiListItemBinding
import eu.kanade.tachiyomi.util.system.getResourceColor
import exh.metadata.MetadataUtil
import exh.metadata.metadata.EHentaiSearchMetadata
import exh.metadata.metadata.base.RaisedSearchMetadata
import exh.util.SourceTagsUtil
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
    SourceHolder<SourceEnhancedEhentaiListItemBinding>(view, adapter) {

    override val binding = SourceEnhancedEhentaiListItemBinding.bind(view)

    private val favoriteColor = view.context.getResourceColor(R.attr.colorOnSurface, 0.38f)
    private val unfavoriteColor = view.context.getResourceColor(R.attr.colorOnSurface)

    /**
     * Method called from [CatalogueAdapter.onBindViewHolder]. It updates the data for this
     * holder with the given manga.
     *
     * @param manga the manga to bind.
     */
    override fun onSetValues(manga: Manga) {
        binding.title.text = manga.title
        binding.title.setTextColor(if (manga.favorite) favoriteColor else unfavoriteColor)

        // Set alpha of thumbnail.
        binding.thumbnail.alpha = if (manga.favorite) 0.3f else 1.0f

        setImage(manga)
    }

    override fun onSetMetadataValues(manga: Manga, metadata: RaisedSearchMetadata) {
        if (metadata !is EHentaiSearchMetadata) return

        if (metadata.uploader != null) {
            binding.uploader.text = metadata.uploader
        }

        val pair = when (metadata.genre) {
            "doujinshi" -> SourceTagsUtil.GenreColor.DOUJINSHI_COLOR to R.string.doujinshi
            "manga" -> SourceTagsUtil.GenreColor.MANGA_COLOR to R.string.manga
            "artistcg" -> SourceTagsUtil.GenreColor.ARTIST_CG_COLOR to R.string.artist_cg
            "gamecg" -> SourceTagsUtil.GenreColor.GAME_CG_COLOR to R.string.game_cg
            "western" -> SourceTagsUtil.GenreColor.WESTERN_COLOR to R.string.western
            "non-h" -> SourceTagsUtil.GenreColor.NON_H_COLOR to R.string.non_h
            "imageset" -> SourceTagsUtil.GenreColor.IMAGE_SET_COLOR to R.string.image_set
            "cosplay" -> SourceTagsUtil.GenreColor.COSPLAY_COLOR to R.string.cosplay
            "asianporn" -> SourceTagsUtil.GenreColor.ASIAN_PORN_COLOR to R.string.asian_porn
            "misc" -> SourceTagsUtil.GenreColor.MISC_COLOR to R.string.misc
            else -> null
        }

        if (pair != null) {
            binding.genre.setBackgroundColor(pair.first.color)
            binding.genre.text = view.context.getString(pair.second)
        } else binding.genre.text = metadata.genre

        metadata.datePosted?.let { binding.datePosted.text = MetadataUtil.EX_DATE_FORMAT.format(Date(it)) }

        metadata.averageRating?.let { binding.ratingBar.rating = it.toFloat() }

        val locale = SourceTagsUtil.getLocaleSourceUtil(metadata.tags.firstOrNull { it.namespace == "language" }?.name)
        val pageCount = metadata.length

        binding.language.text = if (locale != null && pageCount != null) {
            view.resources.getQuantityString(R.plurals.browse_language_and_pages, pageCount, pageCount, locale.toLanguageTag().toUpperCase())
        } else if (pageCount != null) {
            view.resources.getQuantityString(R.plurals.num_pages, pageCount, pageCount)
        } else locale?.toLanguageTag()?.toUpperCase()
    }

    override fun setImage(manga: Manga) {
        GlideApp.with(view.context).clear(binding.thumbnail)

        if (!manga.thumbnail_url.isNullOrEmpty()) {
            val radius = view.context.resources.getDimensionPixelSize(R.dimen.card_radius)
            val requestOptions = RequestOptions().transform(CenterCrop(), RoundedCorners(radius))
            GlideApp.with(view.context)
                .load(manga.toMangaThumbnail())
                .diskCacheStrategy(DiskCacheStrategy.DATA)
                .apply(requestOptions)
                .dontAnimate()
                .placeholder(android.R.color.transparent)
                .into(binding.thumbnail)
        }
    }
}
