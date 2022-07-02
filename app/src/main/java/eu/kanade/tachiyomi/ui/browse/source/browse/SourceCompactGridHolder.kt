package eu.kanade.tachiyomi.ui.browse.source.browse

import androidx.core.view.isVisible
import coil.dispose
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.kanade.domain.manga.model.Manga
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.coil.MangaCoverFetcher
import eu.kanade.tachiyomi.databinding.SourceCompactGridItemBinding
import eu.kanade.tachiyomi.util.view.loadAutoPause
import exh.metadata.metadata.MangaDexSearchMetadata
import exh.metadata.metadata.base.RaisedSearchMetadata

/**
 * Class used to hold the displayed data of a manga in the catalogue, like the cover or the title.
 * All the elements from the layout file "item_source_grid" are available in this class.
 *
 * @param binding the inflated view for this holder.
 * @param adapter the adapter handling this holder.
 * @constructor creates a new catalogue holder.
 */
class SourceCompactGridHolder(
    override val binding: SourceCompactGridItemBinding,
    adapter: FlexibleAdapter<*>,
) : SourceHolder<SourceCompactGridItemBinding>(binding.root, adapter) {

    /**
     * Method called from [CatalogueAdapter.onBindViewHolder]. It updates the data for this
     * holder with the given manga.
     *
     * @param manga the manga to bind.
     */
    override fun onSetValues(manga: Manga) {
        // Set manga title
        binding.title.text = manga.title

        // Set alpha of thumbnail.
        binding.thumbnail.alpha = if (manga.favorite) 0.3f else 1.0f

        // For rounded corners
        binding.badges.leftBadges.clipToOutline = true
        binding.badges.rightBadges.clipToOutline = true

        // Set favorite badge
        binding.badges.favoriteText.isVisible = manga.favorite

        setImage(manga)
    }

    // SY -->
    override fun onSetMetadataValues(manga: Manga, metadata: RaisedSearchMetadata) {
        if (metadata is MangaDexSearchMetadata) {
            metadata.followStatus?.let {
                binding.badges.localText.text = itemView.context.resources.getStringArray(R.array.md_follows_options).asList()[it]
                binding.badges.localText.isVisible = true
            }
            metadata.relation?.let {
                binding.badges.localText.setText(it.resId)
                binding.badges.localText.isVisible = true
            }
        }
    }
    // SY <--

    override fun setImage(manga: Manga) {
        binding.thumbnail.dispose()
        binding.thumbnail.loadAutoPause(manga) {
            setParameter(MangaCoverFetcher.USE_CUSTOM_COVER, false)
        }
    }
}
