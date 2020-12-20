package exh.ui.metadata

import android.os.Bundle
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.MetadataSource
import eu.kanade.tachiyomi.util.lang.asFlow
import exh.metadata.metadata.base.FlatMetadata
import exh.metadata.metadata.base.RaisedSearchMetadata
import exh.metadata.metadata.base.getFlatMetadataForManga
import exh.source.getMainSource
import exh.ui.base.CoroutinePresenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.plus
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MetadataViewPresenter(
    val manga: Manga,
    val source: Source,
    val preferences: PreferencesHelper = Injekt.get(),
    private val db: DatabaseHelper = Injekt.get()
) : CoroutinePresenter<MetadataViewController>() {

    val meta = MutableStateFlow<RaisedSearchMetadata?>(null)

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        getMangaMetaObservable()
            .onEach {
                if (it == null) return@onEach
                val mainSource = source.getMainSource()
                if (mainSource is MetadataSource<*, *>) {
                    meta.value = it.raise(mainSource.metaClass)
                }
            }
            .launchIn(scope + Dispatchers.IO)

        meta
            .onEachView { view, metadata ->
                view.onNextMangaInfo(metadata)
            }
            .launchIn(scope)
    }

    private fun getMangaMetaObservable(): Flow<FlatMetadata?> {
        return db.getFlatMetadataForManga(manga.id!!).asRxObservable().asFlow()
    }
}
