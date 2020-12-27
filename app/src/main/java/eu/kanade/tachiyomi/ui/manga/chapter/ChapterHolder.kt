package eu.kanade.tachiyomi.ui.manga.chapter

import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.View
import androidx.core.view.isVisible
import eu.davidea.viewholders.FlexibleViewHolder
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.databinding.ChaptersItemBinding
import eu.kanade.tachiyomi.source.LocalSource
import exh.EH_SOURCE_ID
import exh.EXH_SOURCE_ID
import exh.metadata.MetadataUtil
import java.util.Date

class ChapterHolder(
    view: View,
    private val adapter: ChaptersAdapter
) : FlexibleViewHolder(view, adapter) {

    private val binding = ChaptersItemBinding.bind(view)

    fun bind(item: ChapterItem, manga: Manga) {
        val chapter = item.chapter

        binding.chapterTitle.text = when (manga.displayMode) {
            Manga.DISPLAY_NUMBER -> {
                val number = adapter.decimalFormat.format(chapter.chapter_number.toDouble())
                itemView.context.getString(R.string.display_mode_chapter, number)
            }
            else -> chapter.name
        }

        // Set correct text color
        val chapterTitleColor = when {
            chapter.read -> adapter.readColor
            chapter.bookmark -> adapter.bookmarkedColor
            else -> adapter.unreadColor
        }
        binding.chapterTitle.setTextColor(chapterTitleColor)

        val chapterDescriptionColor = when {
            chapter.read -> adapter.readColor
            chapter.bookmark -> adapter.bookmarkedColor
            else -> adapter.unreadColorSecondary
        }
        binding.chapterDescription.setTextColor(chapterDescriptionColor)

        binding.bookmarkIcon.isVisible = chapter.bookmark

        val descriptions = mutableListOf<CharSequence>()

        if (chapter.date_upload > 0) {
            // SY -->
            if (manga.source == EH_SOURCE_ID || manga.source == EXH_SOURCE_ID) {
                descriptions.add(MetadataUtil.EX_DATE_FORMAT.format(Date(chapter.date_upload)))
            } else /* SY <-- */ descriptions.add(adapter.dateFormat.format(Date(chapter.date_upload)))
        }
        if ((!chapter.read || (adapter.preserveReadingPosition && (manga.source == EH_SOURCE_ID || manga.source == EXH_SOURCE_ID))) && chapter.last_page_read > 0) {
            val lastPageRead = SpannableString(itemView.context.getString(R.string.chapter_progress, chapter.last_page_read + 1)).apply {
                setSpan(ForegroundColorSpan(adapter.readColor), 0, length, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            descriptions.add(lastPageRead)
        }
        if (!chapter.scanlator.isNullOrBlank()) {
            descriptions.add(chapter.scanlator!!)
        }

        if (descriptions.isNotEmpty()) {
            binding.chapterDescription.text = descriptions.joinTo(SpannableStringBuilder(), " • ")
        } else {
            binding.chapterDescription.text = ""
        }

        binding.download.isVisible = item.manga.source != LocalSource.ID
        binding.download.setState(item.status)
    }
}
