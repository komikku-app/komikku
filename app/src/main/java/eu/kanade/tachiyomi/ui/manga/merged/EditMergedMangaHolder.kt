package eu.kanade.tachiyomi.ui.manga.merged

import android.view.View
import coil3.load
import coil3.request.transformations
import coil3.transform.RoundedCornersTransformation
import eu.davidea.viewholders.FlexibleViewHolder
import eu.kanade.tachiyomi.databinding.EditMergedSettingsItemBinding
import eu.kanade.tachiyomi.util.system.dpToPx
import tachiyomi.domain.manga.model.MergedMangaReference
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class EditMergedMangaHolder(view: View, val adapter: EditMergedMangaAdapter) : FlexibleViewHolder(view, adapter) {

    lateinit var reference: MergedMangaReference
    var binding = EditMergedSettingsItemBinding.bind(view)

    init {
        setDragHandleView(binding.reorder)
        // KMK -->
        binding.cover.setOnClickListener {
            adapter.editMergedMangaItemListener.onOpenEntryClick(bindingAdapterPosition)
        }
        // KMK <--
        binding.remove.setOnClickListener {
            adapter.editMergedMangaItemListener.onDeleteClick(bindingAdapterPosition)
        }
        binding.getChapterUpdates.setOnClickListener {
            adapter.editMergedMangaItemListener.onToggleChapterUpdatesClicked(bindingAdapterPosition)
        }
        binding.download.setOnClickListener {
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
        item.mergedManga?.let {
            binding.cover.load(it) {
                transformations(RoundedCornersTransformation(4.dpToPx.toFloat()))
            }
        }

        binding.title.text = Injekt.get<SourceManager>().getOrStub(item.mergedMangaReference.mangaSourceId).toString()
        binding.subtitle.text = item.mergedManga?.title
        updateDownloadChaptersIcon(item.mergedMangaReference.downloadChapters)
        updateChapterUpdatesIcon(item.mergedMangaReference.getChapterUpdates)

        // KMK -->
        binding.holder.setCardBackgroundColor(adapter.colorScheme.surfaceElevation)
        binding.remove.imageTintList = adapter.colorScheme.imageButtonTintList
        // KMK <--
    }

    fun setHandelAlpha(isPriorityOrder: Boolean) {
        binding.reorder.alpha = when (isPriorityOrder) {
            true -> 1F
            false -> 0.5F
        }
    }

    fun updateDownloadChaptersIcon(setTint: Boolean) {
        val color = if (setTint) {
            // KMK -->
            adapter.colorScheme.secondary
        } else {
            adapter.colorScheme.onSurface
            // KMK <--
        }

        binding.download.drawable.setTint(color)
    }

    fun updateChapterUpdatesIcon(setTint: Boolean) {
        val color = if (setTint) {
            // KMK -->
            adapter.colorScheme.secondary
        } else {
            adapter.colorScheme.onSurface
            // KMK <--
        }

        binding.getChapterUpdates.drawable.setTint(color)
    }
}
