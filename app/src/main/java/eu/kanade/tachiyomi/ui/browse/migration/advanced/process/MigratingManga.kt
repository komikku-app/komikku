package eu.kanade.tachiyomi.ui.browse.migration.advanced.process

import eu.kanade.domain.manga.interactor.GetManga
import eu.kanade.domain.manga.model.Manga
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import exh.util.DeferredField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.coroutines.CoroutineContext

class MigratingManga(
    private val getManga: GetManga,
    private val sourceManager: SourceManager,
    val mangaId: Long,
    parentContext: CoroutineContext,
) {
    val searchResult = DeferredField<Long?>()

    // <MAX, PROGRESS>
    val progress = MutableStateFlow(1 to 0)

    val migrationJob = parentContext + SupervisorJob() + Dispatchers.Default

    var migrationStatus = MigrationStatus.RUNNING

    @Volatile
    private var manga: Manga? = null
    suspend fun manga(): Manga? {
        if (manga == null) manga = getManga.await(mangaId)
        return manga
    }

    suspend fun mangaSource(): Source {
        return sourceManager.getOrStub(manga()?.source ?: -1)
    }

    fun toModal(): MigrationProcessItem {
        // Create the model object.
        return MigrationProcessItem(this)
    }
}
