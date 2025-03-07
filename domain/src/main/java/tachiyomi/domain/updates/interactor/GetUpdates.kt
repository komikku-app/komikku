package tachiyomi.domain.updates.interactor

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.retry
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.updates.model.UpdatesWithRelations
import tachiyomi.domain.updates.repository.UpdatesRepository
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

class GetUpdates(
    private val repository: UpdatesRepository,
) {

    suspend fun await(read: Boolean, after: Long): List<UpdatesWithRelations> {
        // SY -->
        return flow {
            emit(repository.awaitWithRead(read, after, limit = 500))
        }
            .catchNPE()
            .first()
        // SY <--
    }

    fun subscribe(instant: Instant): Flow<List<UpdatesWithRelations>> {
        return repository.subscribeAll(instant.toEpochMilli(), limit = 500)
            // SY -->
            .catchNPE()
        // SY <--
    }

    fun subscribe(read: Boolean, after: Long): Flow<List<UpdatesWithRelations>> {
        return repository.subscribeWithRead(read, after, limit = 500)
            // SY -->
            .catchNPE()
        // SY <--
    }

    // SY -->
    private fun <T> Flow<T>.catchNPE() = retry {
        if (it is NullPointerException) {
            delay(0.5.seconds)
            true
        } else {
            false
        }
    }.catch {
        this@GetUpdates.logcat(LogPriority.ERROR, it)
    }
    // SY <--
}
