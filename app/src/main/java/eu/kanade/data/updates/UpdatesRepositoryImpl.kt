package eu.kanade.data.updates

import eu.kanade.domain.updates.repository.UpdatesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.data.AndroidDatabaseHandler
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.updates.model.UpdatesWithRelations

class UpdatesRepositoryImpl(
    val databaseHandler: DatabaseHandler,
) : UpdatesRepository {

    override fun subscribeAll(after: Long): Flow<List<UpdatesWithRelations>> {
        return databaseHandler.subscribeToList {
            updatesViewQueries.updates(after, updateWithRelationMapper)
        }.map {
            databaseHandler.awaitList { (databaseHandler as AndroidDatabaseHandler).getUpdatesQuery(after) }
                .map(updatesViewMapper)
        }
    }
}
