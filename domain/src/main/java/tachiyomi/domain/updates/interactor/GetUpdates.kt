package tachiyomi.domain.updates.interactor

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.retry
import tachiyomi.domain.updates.model.UpdatesWithRelations
import tachiyomi.domain.updates.repository.UpdatesRepository
import java.util.Calendar
import kotlin.time.Duration.Companion.seconds

class GetUpdates(
    private val repository: UpdatesRepository,
) {

    suspend fun await(read: Boolean, after: Long): List<UpdatesWithRelations> {
        // SY -->
        return flow {
            emit(repository.awaitWithRead(read, after))
        }
            .catchNPE()
            .first()
        // SY <--
    }

    fun subscribe(calendar: Calendar): Flow<List<UpdatesWithRelations>> {
        return repository.subscribeAll(calendar.time.time, limit = 250)
            // SY -->
            .catchNPE()
        // SY <--
    }

    fun subscribe(read: Boolean, after: Long): Flow<List<UpdatesWithRelations>> {
        return repository.subscribeWithRead(read, after)
            // SY -->
            .catchNPE()
        // SY <--
    }

    // SY -->
    private fun <T> Flow<T>.catchNPE() = retry {
        if (it is NullPointerException) {
            delay(5.seconds)
            true
        } else {
            false
        }
    }
    // SY <--
}
