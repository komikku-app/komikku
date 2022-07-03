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
import eu.kanade.domain.chapter.model.Chapter
import eu.kanade.domain.manga.model.Manga
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.ReaderChapterItemBinding
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.view.setVectorCompat
import java.text.DateFormat
import java.text.DecimalFormat
import java.util.Date
import eu.kanade.domain.manga.model.Manga.Companion as DomainManga

class ReaderChapterItem(val chapter: Chapter, val manga: Manga, val isCurrent: Boolean, context: Context, val dateFormat: DateFormat, val decimalFormat: DecimalFormat) :
    AbstractFlexibleItem<ReaderChapterItem.ViewHolder>() {

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
        payloads: List<Any?>?,
    ) {
        holder.bind(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ReaderChapterItem

        if (chapter.id != other.chapter.id) return false

        return true
    }

    override fun hashCode(): Int {
        return chapter.id.hashCode()
    }

    inner class ViewHolder(view: View, private val adapter: ReaderChapterAdapter) : FlexibleViewHolder(view, adapter) {
        val binding = ReaderChapterItemBinding.bind(itemView)

        fun bind(item: ReaderChapterItem) {
            val manga = item.manga
            val chapter = item.chapter

            binding.chapterTitle.text = when (manga.displayMode) {
                DomainManga.CHAPTER_DISPLAY_NUMBER -> {
                    val number = item.decimalFormat.format(chapter.chapterNumber.toDouble())
                    itemView.context.getString(R.string.display_mode_chapter, number)
                }
                else -> chapter.name
            }

            // Set correct text color
            val chapterColor = when {
                chapter.read -> item.readColor
                chapter.bookmark -> item.bookmarkedColor
                else -> item.unreadColor
            }
            binding.chapterTitle.setTextColor(chapterColor)
            binding.chapterScanlator.setTextColor(chapterColor)

            // bookmarkImage.isVisible = item.bookmark

            val descriptions = mutableListOf<CharSequence>()

            if (chapter.dateUpload > 0) {
                descriptions.add(item.dateFormat.format(Date(chapter.dateUpload)))
            }
            if (!chapter.scanlator.isNullOrBlank()) {
                descriptions.add(chapter.scanlator)
            }

            if (descriptions.isNotEmpty()) {
                binding.chapterScanlator.text = descriptions.joinTo(SpannableStringBuilder(), " • ")
            } else {
                binding.chapterScanlator.text = ""
            }

            if (chapter.bookmark) {
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
                adapter.clickListener.bookmarkChapter(chapter)
            }
        }
    }
}
