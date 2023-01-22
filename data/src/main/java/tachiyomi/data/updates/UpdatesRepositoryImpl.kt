package tachiyomi.data.updates

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.data.AndroidDatabaseHandler
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.updates.model.UpdatesWithRelations
import tachiyomi.domain.updates.repository.UpdatesRepository

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
