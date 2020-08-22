package eu.kanade.tachiyomi.ui.reader.chapter

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.items.AbstractItem
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.view.setVectorCompat
import java.text.DateFormat
import java.text.DecimalFormat
import java.util.Date

class ReaderChapterItem(val chapter: Chapter, val manga: Manga, val isCurrent: Boolean, context: Context, val dateFormat: DateFormat, val decimalFormat: DecimalFormat) :
    AbstractItem<ReaderChapterItem.ViewHolder>(),
    Chapter by chapter {

    val readColor = context.getResourceColor(R.attr.colorOnSurface, 0.38f)
    val unreadColor = context.getResourceColor(R.attr.colorOnSurface)
    val bookmarkedColor = context.getResourceColor(R.attr.colorAccent)

    /** defines the type defining this item. must be unique. preferably an id */
    override val type: Int = R.id.reader_chapter_layout

    /** defines the layout which will be used for this item in the list */
    override val layoutRes: Int = R.layout.reader_chapter_item

    override var identifier: Long = chapter.id!!

    override fun getViewHolder(v: View): ViewHolder {
        return ViewHolder(v)
    }

    class ViewHolder(view: View) : FastAdapter.ViewHolder<ReaderChapterItem>(view) {
        private var chapterTitle: TextView = view.findViewById(R.id.chapter_title)
        private var chapterSubtitle: TextView = view.findViewById(R.id.chapter_scanlator)
        var bookmarkButton: FrameLayout = view.findViewById(R.id.bookmark_layout)
        private var bookmarkImage: ImageView = view.findViewById(R.id.bookmark_image)

        override fun bindView(item: ReaderChapterItem, payloads: List<Any>) {
            val manga = item.manga

            chapterTitle.text = when (manga.displayMode) {
                Manga.DISPLAY_NUMBER -> {
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
            chapterTitle.setTextColor(chapterColor)
            chapterSubtitle.setTextColor(chapterColor)

            // bookmarkImage.isVisible = item.bookmark

            val descriptions = mutableListOf<CharSequence>()

            if (item.date_upload > 0) {
                descriptions.add(item.dateFormat.format(Date(item.date_upload)))
            }
            if (!item.scanlator.isNullOrBlank()) {
                descriptions.add(item.scanlator!!)
            }

            if (descriptions.isNotEmpty()) {
                chapterSubtitle.text = descriptions.joinTo(SpannableStringBuilder(), " • ")
            } else {
                chapterSubtitle.text = ""
            }

            if (item.bookmark) {
                bookmarkImage.setVectorCompat(R.drawable.ic_bookmark_24dp, R.attr.colorAccent)
            } else {
                bookmarkImage.setVectorCompat(R.drawable.ic_bookmark_border_24dp, R.attr.colorOnSurface)
            }

            if (item.isCurrent) {
                chapterTitle.setTypeface(null, Typeface.BOLD_ITALIC)
                chapterSubtitle.setTypeface(null, Typeface.BOLD_ITALIC)
            } else {
                chapterTitle.setTypeface(null, Typeface.NORMAL)
                chapterSubtitle.setTypeface(null, Typeface.NORMAL)
            }
        }

        override fun unbindView(item: ReaderChapterItem) {
            chapterTitle.text = null
            chapterSubtitle.text = null
        }
    }
}
