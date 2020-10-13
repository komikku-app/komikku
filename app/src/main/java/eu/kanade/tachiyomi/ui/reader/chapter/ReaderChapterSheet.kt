package eu.kanade.tachiyomi.ui.reader.chapter

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import com.mikepenz.fastadapter.listeners.ClickEventHook
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.databinding.ReaderChaptersSheetBinding
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.ReaderPresenter
import eu.kanade.tachiyomi.util.lang.launchUI
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.widget.SimpleSeekBarListener
import exh.util.isExpanded
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import reactivecircus.flowbinding.android.view.clicks

/**
 * Color filter sheet to toggle custom filter and brightness overlay.
 */
class ReaderChapterSheet(private val activity: ReaderActivity) : BottomSheetDialog(activity) {
    private var sheetBehavior: BottomSheetBehavior<*>? = null

    private val binding = ReaderChaptersSheetBinding.inflate(activity.layoutInflater, null, false)
    private var initialized = false

    var presenter: ReaderPresenter
    var adapter: FastAdapter<ReaderChapterItem>? = null
    private val itemAdapter = ItemAdapter<ReaderChapterItem>()
    var shouldCollapse = true
    var selectedChapterId = -1L

    init {
        setContentView(binding.root)

        sheetBehavior = BottomSheetBehavior.from(binding.root.parent as ViewGroup)

        presenter = activity.presenter
        adapter = FastAdapter.with(itemAdapter)
        binding.chapterRecycler.adapter = adapter
        adapter?.onClickListener = { _, _, item, _ ->
            if (!sheetBehavior.isExpanded()) {
                false
            } else {
                if (item.chapter.id != presenter.getCurrentChapter()?.chapter?.id) {
                    shouldCollapse = false
                    presenter.loadNewChapterFromSheet(item.chapter)
                }
                true
            }
        }
        adapter?.addEventHook(
            object : ClickEventHook<ReaderChapterItem>() {
                override fun onBind(viewHolder: RecyclerView.ViewHolder): View? {
                    return if (viewHolder is ReaderChapterItem.ViewHolder) {
                        viewHolder.bookmarkButton
                    } else {
                        null
                    }
                }

                override fun onClick(
                    v: View,
                    position: Int,
                    fastAdapter: FastAdapter<ReaderChapterItem>,
                    item: ReaderChapterItem
                ) {
                    presenter.toggleBookmark(item.chapter)
                    refreshList()
                }
            }
        )

        binding.chapterRecycler.layoutManager = LinearLayoutManager(context)
        // refreshList()
        binding.webviewButton.clicks()
            .onEach { activity.openMangaInBrowser() }
            .launchIn(activity.scope)

        binding.pageSeekbar.setOnSeekBarChangeListener(
            object : SimpleSeekBarListener() {

                @SuppressLint("SetTextI18n")
                override fun onProgressChanged(seekBar: SeekBar, value: Int, fromUser: Boolean) {
                    if (activity.viewer != null && fromUser) {
                        binding.pageText.text = "${value + 1}/${binding.pageSeekbar.max + 1}"
                        binding.pageSeekbar.progress = value
                        activity.moveToPageIndex(value)
                    }
                }
            }
        )
    }

    override fun onStart() {
        super.onStart()
        sheetBehavior?.skipCollapsed = true
        sheetBehavior?.state = BottomSheetBehavior.STATE_EXPANDED
    }

    override fun show() {
        if (!initialized) {
            refreshList()
            initialized = true
        }
        binding.pageText.text = activity.binding.pageText.text
        binding.pageSeekbar.max = activity.binding.pageSeekbar.max
        binding.pageSeekbar.progress = activity.binding.pageSeekbar.progress
        super.show()
    }

    fun refreshList() {
        launchUI {
            val chapters = with(presenter.getChapters(context)) {
                when (activity.presenter.manga?.sorting) {
                    Manga.SORTING_SOURCE -> sortedBy { it.source_order }
                    Manga.SORTING_NUMBER -> sortedByDescending { it.chapter_number }
                    Manga.SORTING_UPLOAD_DATE -> sortedBy { it.date_upload }
                    else -> sortedBy { it.source_order }
                }
            }

            selectedChapterId = chapters.find { it.isCurrent }?.chapter?.id ?: -1L
            itemAdapter.clear()
            itemAdapter.add(chapters)

            (binding.chapterRecycler.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(
                adapter?.getPosition(presenter.getCurrentChapter()?.chapter?.id ?: 0L) ?: 0,
                (binding.chapterRecycler.height / 2).dpToPx
            )
        }
    }
}
