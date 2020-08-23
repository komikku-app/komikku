package eu.kanade.tachiyomi.ui.reader.chapter

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.core.graphics.ColorUtils
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import com.mikepenz.fastadapter.listeners.ClickEventHook
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.ReaderChaptersSheetBinding
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.ReaderPresenter
import eu.kanade.tachiyomi.util.lang.launchUI
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.getResourceColor
import exh.util.collapse
import exh.util.expand
import exh.util.isExpanded
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.android.synthetic.main.reader_chapters_sheet.view.pill

class ReaderChapterSheet @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    LinearLayout(context, attrs) {

    lateinit var binding: ReaderChaptersSheetBinding

    var sheetBehavior: BottomSheetBehavior<View>? = null
    lateinit var presenter: ReaderPresenter
    var adapter: FastAdapter<ReaderChapterItem>? = null
    private val itemAdapter = ItemAdapter<ReaderChapterItem>()
    var shouldCollapse = true
    var selectedChapterId = -1L

    fun setup(activity: ReaderActivity) {
        presenter = activity.presenter
        binding = activity.readerBottomSheetBinding
        val fullPrimary = context.getResourceColor(R.attr.colorSurface)
        val primary = ColorUtils.setAlphaComponent(fullPrimary, 200)

        sheetBehavior = BottomSheetBehavior.from(this)
        binding.chaptersButton.setOnClickListener {
            if (sheetBehavior.isExpanded()) {
                sheetBehavior?.collapse()
            } else {
                sheetBehavior?.expand()
            }
        }

        binding.webviewButton.setOnClickListener {
            activity.openMangaInBrowser()
        }

        post {
            binding.chapterRecycler.alpha = if (sheetBehavior.isExpanded()) 1f else 0f
            binding.chapterRecycler.isClickable = sheetBehavior.isExpanded()
            binding.chapterRecycler.isFocusable = sheetBehavior.isExpanded()
        }

        sheetBehavior?.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onSlide(bottomSheet: View, progress: Float) {
                val trueProgress = max(progress, 0f)
                binding.pill.alpha = (1 - trueProgress) * 0.25f
                binding.chaptersButton.alpha = 1 - trueProgress
                binding.webviewButton.alpha = trueProgress
                binding.webviewButton.isVisible = binding.webviewButton.alpha > 0
                binding.chaptersButton.isInvisible = binding.chaptersButton.alpha <= 0
                backgroundTintList =
                    ColorStateList.valueOf(lerpColor(primary, fullPrimary, trueProgress))
                binding.chapterRecycler.alpha = trueProgress
            }

            override fun onStateChanged(p0: View, state: Int) {
                if (state == BottomSheetBehavior.STATE_COLLAPSED) {
                    shouldCollapse = true
                    sheetBehavior?.isHideable = false
                    (binding.chapterRecycler.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(
                        adapter?.getPosition(presenter.getCurrentChapter()?.chapter?.id ?: 0L) ?: 0,
                        binding.chapterRecycler.height / 2 - 30.dpToPx
                    )
                    binding.chaptersButton.alpha = 1f
                    binding.webviewButton.alpha = 0f
                }
                if (state == BottomSheetBehavior.STATE_EXPANDED) {
                    binding.chapterRecycler.alpha = 1F
                    binding.chaptersButton.alpha = 0f
                    binding.webviewButton.alpha = 1f
                }
                binding.chapterRecycler.isClickable = state == BottomSheetBehavior.STATE_EXPANDED
                binding.chapterRecycler.isFocusable = state == BottomSheetBehavior.STATE_EXPANDED
                binding.webviewButton.isVisible = state != BottomSheetBehavior.STATE_COLLAPSED
                binding.chaptersButton.isInvisible = state == BottomSheetBehavior.STATE_EXPANDED
            }
        })

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
        adapter?.addEventHook(object : ClickEventHook<ReaderChapterItem>() {
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
        })

        backgroundTintList = ColorStateList.valueOf(
            if (!sheetBehavior.isExpanded()) primary
            else fullPrimary
        )

        binding.chapterRecycler.layoutManager = LinearLayoutManager(context)
        refreshList()
    }

    fun refreshList() {
        launchUI {
            val chapters = presenter.getChapters(context)

            selectedChapterId = chapters.find { it.isCurrent }?.chapter?.id ?: -1L
            itemAdapter.clear()
            itemAdapter.add(chapters)

            (binding.chapterRecycler.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(
                adapter?.getPosition(presenter.getCurrentChapter()?.chapter?.id ?: 0L) ?: 0,
                binding.chapterRecycler.height / 2 - 30.dpToPx
            )
        }
    }

    fun lerpColor(colorStart: Int, colorEnd: Int, percent: Float): Int {
        val perc = (percent * 100).roundToInt()
        return Color.argb(
            lerpColorCalc(Color.alpha(colorStart), Color.alpha(colorEnd), perc),
            lerpColorCalc(Color.red(colorStart), Color.red(colorEnd), perc),
            lerpColorCalc(Color.green(colorStart), Color.green(colorEnd), perc),
            lerpColorCalc(Color.blue(colorStart), Color.blue(colorEnd), perc)
        )
    }

    fun lerpColorCalc(colorStart: Int, colorEnd: Int, percent: Int): Int {
        return (
            min(colorStart, colorEnd) * (100 - percent) + max(
                colorStart, colorEnd
            ) * percent
            ) / 100
    }
}
