package eu.kanade.tachiyomi.ui.reader.chapter

import androidx.recyclerview.widget.LinearLayoutManager
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.callbacks.onDismiss
import com.afollestad.materialdialogs.customview.customView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.databinding.ReaderChaptersDialogBinding
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.ReaderPresenter
import eu.kanade.tachiyomi.util.chapter.getChapterSort
import eu.kanade.tachiyomi.util.system.dpToPx

class ReaderChapterDialog(private val activity: ReaderActivity) : ReaderChapterAdapter.OnBookmarkClickListener {
    private val binding = ReaderChaptersDialogBinding.inflate(activity.layoutInflater, null, false)

    var presenter: ReaderPresenter = activity.presenter
    var adapter: FlexibleAdapter<ReaderChapterItem>? = null
    var dialog: MaterialDialog

    init {
        dialog = MaterialDialog(activity)
            .title(R.string.chapters)
            .customView(view = binding.root)
            .negativeButton(android.R.string.cancel)
            .onDismiss { destroy() }
            .show {
                adapter = ReaderChapterAdapter(this@ReaderChapterDialog)
                binding.chapterRecycler.adapter = adapter

                adapter?.mItemClickListener = FlexibleAdapter.OnItemClickListener { _, position ->
                    val item = adapter?.getItem(position)
                    if (item != null && item.id != presenter.getCurrentChapter()?.chapter?.id) {
                        dismiss()
                        presenter.loadNewChapterFromSheet(item)
                    }
                    true
                }

                binding.chapterRecycler.layoutManager = LinearLayoutManager(context)
                refreshList()
            }
    }

    private fun refreshList() {
        val chapters = presenter.getChapters(activity)
            .sortedWith(getChapterSort(presenter.manga!!))

        adapter?.clear()
        adapter?.updateDataSet(chapters)

        (binding.chapterRecycler.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(
            adapter?.getGlobalPositionOf(chapters.find { it.isCurrent }) ?: 0,
            (binding.chapterRecycler.height / 2).dpToPx
        )
    }

    fun destroy() {
        adapter = null
    }

    override fun bookmarkChapter(chapter: Chapter) {
        presenter.toggleBookmark(chapter)
        refreshList()
    }
}
