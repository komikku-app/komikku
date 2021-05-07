package eu.kanade.tachiyomi.ui.reader.chapter

import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.kanade.tachiyomi.data.database.models.Chapter

class ReaderChapterAdapter(
    dialog: OnBookmarkClickListener
) : FlexibleAdapter<ReaderChapterItem>(null, dialog, true) {

    /**
     * Listener for browse item clicks.
     */
    val clickListener: OnBookmarkClickListener = dialog

    /**
     * Listener which should be called when user clicks the download icons.
     */
    interface OnBookmarkClickListener {
        fun bookmarkChapter(chapter: Chapter)
    }
}
