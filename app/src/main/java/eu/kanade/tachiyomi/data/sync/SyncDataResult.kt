package eu.kanade.tachiyomi.data.sync

/**
 * Captures the most recent sync failure reported by a [eu.kanade.tachiyomi.data.sync.service.SyncService]
 * implementation so [SyncManager] can propagate the message without losing context.
 */
internal object SyncFailureState {
    private var failure: Pair<String, Boolean>? = null

    fun report(message: String, errorAlreadyNotified: Boolean = true) {
        failure = message to errorAlreadyNotified
    }

    fun consume(): Pair<String, Boolean>? = failure.also { failure = null }
}

sealed interface SyncDataResult {
    data object Success : SyncDataResult

    /**
     * @param message Human-readable failure reason.
     * @param errorAlreadyNotified `true` when a sync service already showed a sync error notification.
     */
    data class Failure(
        val message: String,
        val errorAlreadyNotified: Boolean = false,
    ) : SyncDataResult
}
