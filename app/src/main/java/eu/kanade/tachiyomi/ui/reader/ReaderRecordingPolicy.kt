package eu.kanade.tachiyomi.ui.reader

/**
 * Decides what a reader page event records.
 *
 * Incognito mode suppresses reading history and external side effects. Chapter read state and page
 * progress are local reading state, so they are still recorded.
 */
internal object ReaderRecordingPolicy {

    data class PageDecision(
        /** Persist `last_page_read` for this page. */
        val saveProgress: Boolean,
        /** Mark the chapter read when this is its last page. */
        val markRead: Boolean,
        /** Run tracker, duplicate-chapter, delete-after-read and EH auto-mark work. */
        val runCompletionSideEffects: Boolean,
        /** Start the chapter-open and chapter-read sync jobs. */
        val triggerSync: Boolean,
    )

    fun decidePage(
        incognitoMode: Boolean,
        isErrorPage: Boolean,
        isLastPage: Boolean,
    ): PageDecision {
        val canSave = !isErrorPage
        val completed = canSave && isLastPage
        return PageDecision(
            saveProgress = canSave,
            markRead = completed,
            runCompletionSideEffects = completed && !incognitoMode,
            triggerSync = canSave && !incognitoMode,
        )
    }

    fun recordsHistory(incognitoMode: Boolean): Boolean = !incognitoMode
}
