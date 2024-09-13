package tachiyomi.domain.source.interactor

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.domain.source.model.FeedSavedSearchUpdate
import tachiyomi.domain.source.repository.FeedSavedSearchRepository
import java.util.Collections

class ReorderFeed(
    private val feedSavedSearchRepository: FeedSavedSearchRepository,
) {

    private val mutex = Mutex()

    suspend fun moveUp(feed: FeedSavedSearch): Result = await(feed, MoveTo.UP)

    suspend fun moveDown(feed: FeedSavedSearch): Result = await(feed, MoveTo.DOWN)

    private suspend fun await(feed: FeedSavedSearch, moveTo: MoveTo) = withNonCancellableContext {
        mutex.withLock {
            val feeds = feedSavedSearchRepository.getGlobal()
                .toMutableList()

            val currentIndex = feeds.indexOfFirst { it.id == feed.id }
            if (currentIndex == -1) {
                return@withNonCancellableContext Result.Unchanged
            }

            val newPosition = when (moveTo) {
                MoveTo.UP -> currentIndex - 1
                MoveTo.DOWN -> currentIndex + 1
            }.toInt()

            try {
                Collections.swap(feeds, currentIndex, newPosition)

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

    suspend fun sortAlphabetically(updates: List<FeedSavedSearchUpdate>?) = withNonCancellableContext {
        if (updates == null) return@withNonCancellableContext
        mutex.withLock {
            try {
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

    private enum class MoveTo {
        UP,
        DOWN,
    }
}
