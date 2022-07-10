package eu.kanade.tachiyomi.ui.browse.source.browse

import com.jakewharton.rxrelay.PublishRelay
import eu.kanade.core.util.asFlow
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.MetadataMangasPage
import eu.kanade.tachiyomi.source.model.SManga
import exh.metadata.metadata.base.RaisedSearchMetadata
import kotlinx.coroutines.flow.Flow

/**
 * A general pager for source requests (latest updates, popular, search)
 */
abstract class Pager(var currentPage: Int = 1) {

    var hasNextPage = true
        private set

    protected val results: PublishRelay</* SY --> */ Triple /* SY <-- */<Int, List<SManga> /* SY --> */, List<RaisedSearchMetadata>? /* SY <-- */>> = PublishRelay.create()

    fun asFlow(): Flow</* SY --> */ Triple /* SY <-- */<Int, List<SManga> /* SY --> */, List<RaisedSearchMetadata>?> /* SY <-- */> {
        return results.asObservable().asFlow()
    }

    abstract suspend fun requestNextPage()

    fun onPageReceived(mangasPage: MangasPage) {
        val page = currentPage
        currentPage++
        hasNextPage = mangasPage.hasNextPage && mangasPage.mangas.isNotEmpty()
        // SY -->
        val mangasMetadata = if (mangasPage is MetadataMangasPage) {
            mangasPage.mangasMetadata
        } else null
        // SY <--
        results.call( /* SY <-- */ Triple /* SY <-- */ (page, mangasPage.mangas /* SY --> */, mangasMetadata /* SY <-- */))
    }
}
