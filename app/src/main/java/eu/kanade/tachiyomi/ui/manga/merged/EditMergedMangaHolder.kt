package eu.kanade.tachiyomi.ui.manga.merged

import android.view.View
import com.bumptech.glide.load.engine.DiskCacheStrategy
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.glide.GlideApp
import eu.kanade.tachiyomi.data.glide.toMangaThumbnail
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import eu.kanade.tachiyomi.util.system.getResourceColor
import exh.merged.sql.models.MergedMangaReference
import kotlinx.android.synthetic.main.edit_merged_settings_item.cover
import kotlinx.android.synthetic.main.edit_merged_settings_item.download
import kotlinx.android.synthetic.main.edit_merged_settings_item.get_chapter_updates
import kotlinx.android.synthetic.main.edit_merged_settings_item.remove
import kotlinx.android.synthetic.main.edit_merged_settings_item.reorder
import kotlinx.android.synthetic.main.edit_merged_settings_item.subtitle
import kotlinx.android.synthetic.main.edit_merged_settings_item.title
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class EditMergedMangaHolder(view: View, val adapter: EditMergedMangaAdapter) : BaseFlexibleViewHolder(view, adapter) {

    lateinit var reference: MergedMangaReference

    init {
        setDragHandleView(reorder)
        remove.setOnClickListener {
            adapter.editMergedMangaItemListener.onDeleteClick(bindingAdapterPosition)
        }
        get_chapter_updates.setOnClickListener {
            adapter.editMergedMangaItemListener.onToggleChapterUpdatesClicked(bindingAdapterPosition)
        }
        download.setOnClickListener {
            adapter.editMergedMangaItemListener.onToggleChapterDownloadsClicked(bindingAdapterPosition)
        }
        setHandelAlpha(adapter.isPriorityOrder)
    }

    override fun onItemReleased(position: Int) {
        super.onItemReleased(position)
        adapter.editMergedMangaItemListener.onItemReleased(position)
    }

    fun bind(item: EditMergedMangaItem) {
        reference = item.mergedMangaReference
        item.mergedManga?.toMangaThumbnail()?.let {
            GlideApp.with(itemView.context)
                .load(it)
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .centerCrop()
                .into(cover)
        }

        title.text = Injekt.get<SourceManager>().getOrStub(item.mergedMangaReference.mangaSourceId).toString()
        subtitle.text = item.mergedManga?.title
        updateDownloadChaptersIcon(item.mergedMangaReference.downloadChapters)
        updateChapterUpdatesIcon(item.mergedMangaReference.getChapterUpdates)
    }

    fun setHandelAlpha(isPriorityOrder: Boolean) {
        reorder.alpha = when (isPriorityOrder) {
            true -> 1F
            false -> 0.5F
        }
    }

    fun updateDownloadChaptersIcon(setTint: Boolean) {
        val color = if (setTint) {
            itemView.context.getResourceColor(R.attr.colorAccent)
        } else itemView.context.getResourceColor(R.attr.colorOnSurface)

        download.drawable.setTint(color)
    }

    fun updateChapterUpdatesIcon(setTint: Boolean) {
        val color = if (setTint) {
            itemView.context.getResourceColor(R.attr.colorAccent)
        } else itemView.context.getResourceColor(R.attr.colorOnSurface)

        get_chapter_updates.drawable.setTint(color)
    }
}
