package exh.ui.metadata

import android.os.Bundle
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.MetadataSource
import eu.kanade.tachiyomi.util.lang.launchIO
import exh.metadata.metadata.base.RaisedSearchMetadata
import exh.metadata.metadata.base.getFlatMetadataForManga
import exh.source.getMainSource
import exh.ui.base.CoroutinePresenter
import exh.util.executeOnIO
import kotlinx.coroutines.flow.MutableStateFlow
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

        launchIO {
            val flatMetadata = db.getFlatMetadataForManga(manga.id!!).executeOnIO() ?: return@launchIO
            val mainSource = source.getMainSource<MetadataSource<*, *>>()
            if (mainSource != null) {
                meta.value = flatMetadata.raise(mainSource.metaClass)
            }
        }

        meta
            .inView { view, metadata ->
                view.onNextMangaInfo(metadata)
            }
            .launch()
    }
}
