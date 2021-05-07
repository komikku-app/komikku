package eu.kanade.tachiyomi.ui.reader.chapter

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.davidea.viewholders.FlexibleViewHolder
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.databinding.ReaderChapterItemBinding
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.view.setVectorCompat
import java.text.DateFormat
import java.text.DecimalFormat
import java.util.Date

class ReaderChapterItem(chapter: Chapter, val manga: Manga, val isCurrent: Boolean, context: Context, val dateFormat: DateFormat, val decimalFormat: DecimalFormat) :
    AbstractFlexibleItem<ReaderChapterItem.ViewHolder>(),
    Chapter by chapter {

    val readColor = context.getResourceColor(R.attr.colorOnSurface, 0.38f)
    val unreadColor = context.getResourceColor(R.attr.colorOnSurface)
    val bookmarkedColor = context.getResourceColor(R.attr.colorAccent)

    override fun getLayoutRes(): Int {
        return R.layout.reader_chapter_item
    }

    override fun createViewHolder(view: View, adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>): ViewHolder {
        return ViewHolder(view, adapter as ReaderChapterAdapter)
    }

    override fun bindViewHolder(
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
        holder: ViewHolder,
        position: Int,
        payloads: List<Any?>?
    ) {
        holder.bind(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ReaderChapterItem

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    inner class ViewHolder(view: View, private val adapter: ReaderChapterAdapter) : FlexibleViewHolder(view, adapter) {
        val binding = ReaderChapterItemBinding.bind(itemView)

        fun bind(item: ReaderChapterItem) {
            val manga = item.manga

            binding.chapterTitle.text = when (manga.displayMode) {
                Manga.CHAPTER_DISPLAY_NUMBER -> {
                    val number = item.decimalFormat.format(item.chapter_number.toDouble())
                    itemView.context.getString(R.string.display_mode_chapter, number)
                }
                else -> item.name
            }

            // Set correct text color
            val chapterColor = when {
                item.read -> item.readColor
                item.bookmark -> item.bookmarkedColor
                else -> item.unreadColor
            }
            binding.chapterTitle.setTextColor(chapterColor)
            binding.chapterScanlator.setTextColor(chapterColor)

            // bookmarkImage.isVisible = item.bookmark

            val descriptions = mutableListOf<CharSequence>()

            if (item.date_upload > 0) {
                descriptions.add(item.dateFormat.format(Date(item.date_upload)))
            }
            if (!item.scanlator.isNullOrBlank()) {
                descriptions.add(item.scanlator!!)
            }

            if (descriptions.isNotEmpty()) {
                binding.chapterScanlator.text = descriptions.joinTo(SpannableStringBuilder(), " • ")
            } else {
                binding.chapterScanlator.text = ""
            }

            if (item.bookmark) {
                binding.bookmarkImage.setVectorCompat(R.drawable.ic_bookmark_24dp, R.attr.colorAccent)
            } else {
                binding.bookmarkImage.setVectorCompat(R.drawable.ic_bookmark_border_24dp, R.attr.colorOnSurface)
            }

            if (item.isCurrent) {
                binding.chapterTitle.setTypeface(null, Typeface.BOLD_ITALIC)
                binding.chapterScanlator.setTypeface(null, Typeface.BOLD_ITALIC)
            } else {
                binding.chapterTitle.setTypeface(null, Typeface.NORMAL)
                binding.chapterScanlator.setTypeface(null, Typeface.NORMAL)
            }
            binding.bookmarkLayout.setOnClickListener {
                adapter.clickListener.bookmarkChapter(item)
            }
        }
    }
}
