package eu.kanade.tachiyomi.ui.reader.chapter

import androidx.recyclerview.widget.LinearLayoutManager
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.callbacks.onDismiss
import com.afollestad.materialdialogs.customview.customView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.databinding.ReaderChaptersSheetBinding
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.ReaderPresenter
import eu.kanade.tachiyomi.util.lang.launchUI
import eu.kanade.tachiyomi.util.system.dpToPx

class ReaderChapterDialog(private val activity: ReaderActivity) : ReaderChapterAdapter.OnBookmarkClickListener {
    private val binding = ReaderChaptersSheetBinding.inflate(activity.layoutInflater, null, false)

    var presenter: ReaderPresenter = activity.presenter
    var adapter: FlexibleAdapter<ReaderChapterItem>? = null
    var dialog: MaterialDialog

    init {
        dialog = MaterialDialog(activity)
            .title(R.string.chapters)
            .customView(view = binding.root)
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
                onDismiss {
                    destroy()
                }
                refreshList()
            }
    }

    private fun refreshList() {
        launchUI {
            val chapters = with(presenter.getChapters(activity)) {
                when (activity.presenter.manga?.sorting) {
                    Manga.CHAPTER_SORTING_SOURCE -> sortedBy { it.source_order }
                    Manga.CHAPTER_SORTING_NUMBER -> sortedByDescending { it.chapter_number }
                    Manga.CHAPTER_SORTING_UPLOAD_DATE -> sortedBy { it.date_upload }
                    else -> sortedBy { it.source_order }
                }
            }

            adapter?.clear()
            adapter?.updateDataSet(chapters)

            (binding.chapterRecycler.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(
                adapter?.getGlobalPositionOf(chapters.find { it.isCurrent }) ?: 0,
                (binding.chapterRecycler.height / 2).dpToPx
            )
        }
    }

    fun destroy() {
        adapter = null
    }

    override fun bookmarkChapter(chapter: Chapter) {
        presenter.toggleBookmark(chapter)
        refreshList()
    }
}
