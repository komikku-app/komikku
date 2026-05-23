package eu.kanade.tachiyomi.data.library

/**
 * Holds a sync failure message to be reported by [LibraryUpdateJob] when a library update
 * was chained to run after [eu.kanade.tachiyomi.data.sync.SyncDataJob].
 */
object LibraryUpdateSyncResult {
    @Volatile
    private var pendingError: String? = null

    fun setFailure(message: String) {
        pendingError = message
    }

    fun consumeFailure(): String? {
        val error = pendingError
        pendingError = null
        return error
    }
}
