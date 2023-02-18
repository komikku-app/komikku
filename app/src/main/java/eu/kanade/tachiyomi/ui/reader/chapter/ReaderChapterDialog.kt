package eu.kanade.tachiyomi.ui.reader.chapter

import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.ReaderChaptersDialogBinding
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.ReaderViewModel
import eu.kanade.tachiyomi.util.system.dpToPx
import kotlinx.coroutines.launch
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.service.getChapterSort

class ReaderChapterDialog(private val activity: ReaderActivity) : ReaderChapterAdapter.OnBookmarkClickListener {
    private val binding = ReaderChaptersDialogBinding.inflate(activity.layoutInflater)

    var viewModel: ReaderViewModel = activity.viewModel
    var adapter: FlexibleAdapter<ReaderChapterItem>? = null
    var dialog: AlertDialog

    init {
        dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.chapters)
            .setView(binding.root)
            .setNegativeButton(android.R.string.cancel, null)
            .setOnDismissListener { destroy() }
            .create()

        adapter = ReaderChapterAdapter(this@ReaderChapterDialog)
        binding.chapterRecycler.adapter = adapter

        adapter?.mItemClickListener = FlexibleAdapter.OnItemClickListener { _, position ->
            val item = adapter?.getItem(position)
            if (item != null && item.chapter.id != viewModel.getCurrentChapter()?.chapter?.id) {
                dialog.dismiss()
                activity.lifecycleScope.launch {
                    viewModel.loadNewChapterFromDialog(item.chapter)
                }
            }
            true
        }

        binding.chapterRecycler.layoutManager = LinearLayoutManager(activity)
        activity.lifecycleScope.launch {
            refreshList()
        }

        dialog.show()
    }

    private fun refreshList(scroll: Boolean = true) {
        val chapterSort = getChapterSort(viewModel.manga!!)
        val chapters = viewModel.getChapters(activity)
            .sortedWith { a, b ->
                chapterSort(a.chapter, b.chapter)
            }

        adapter?.clear()
        adapter?.updateDataSet(chapters)

        if (scroll) {
            (binding.chapterRecycler.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(
                adapter?.getGlobalPositionOf(chapters.find { it.isCurrent }) ?: 0,
                (binding.chapterRecycler.height / 2).dpToPx,
            )
        }
    }

    fun destroy() {
        adapter = null
    }

    override fun bookmarkChapter(chapter: Chapter) {
        viewModel.toggleBookmark(chapter.id, !chapter.bookmark)
        refreshList(scroll = false)
    }
}
