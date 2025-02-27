package tachiyomi.domain.source.interactor

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.domain.source.model.FeedSavedSearchUpdate
import tachiyomi.domain.source.repository.FeedSavedSearchRepository

class ReorderFeed(
    private val feedSavedSearchRepository: FeedSavedSearchRepository,
) {

    private val mutex = Mutex()

    suspend fun changeOrder(feed: FeedSavedSearch, newIndex: Int, global: Boolean = true) = withNonCancellableContext {
        mutex.withLock {
            val feeds = if (global) {
                feedSavedSearchRepository.getGlobal()
                    .toMutableList()
            } else {
                feedSavedSearchRepository.getBySourceId(feed.source)
                    .toMutableList()
            }

            val currentIndex = feeds.indexOfFirst { it.id == feed.id }
            if (currentIndex == -1) {
                return@withNonCancellableContext Result.Unchanged
            }

            try {
                feeds.add(newIndex, feeds.removeAt(currentIndex))

                val updates = feeds.mapIndexed { index, feed ->
                    FeedSavedSearchUpdate(
                        id = feed.id,
                        feedOrder = index.toLong(),
                    )
                }

                feedSavedSearchRepository.updatePartial(updates)
                Result.Success
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
                Result.InternalError(e)
            }
        }
    }

    sealed interface Result {
        data object Success : Result
        data object Unchanged : Result
        data class InternalError(val error: Throwable) : Result
    }
}
