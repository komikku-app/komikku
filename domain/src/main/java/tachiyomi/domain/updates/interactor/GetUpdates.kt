package tachiyomi.domain.updates.interactor

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retry
import tachiyomi.domain.updates.model.UpdatesWithRelations
import tachiyomi.domain.updates.repository.UpdatesRepository
import java.util.Calendar

class GetUpdates(
    private val repository: UpdatesRepository,
) {

    fun subscribe(calendar: Calendar): Flow<List<UpdatesWithRelations>> = subscribe(calendar.time.time)

    fun subscribe(after: Long): Flow<List<UpdatesWithRelations>> {
        return repository.subscribeAll(after)
            // SY -->
            .let {
                var retries = 0
                it.retry {
                    (retries++ < 3) && it is NullPointerException
                }.onEach {
                    retries = 0
                }
            }
        // SY <--
    }
}
