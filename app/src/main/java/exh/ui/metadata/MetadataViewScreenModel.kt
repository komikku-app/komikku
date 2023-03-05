package exh.ui.metadata

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.coroutineScope
import eu.kanade.domain.manga.interactor.GetFlatMetadataById
import eu.kanade.tachiyomi.source.online.MetadataSource
import exh.metadata.metadata.base.RaisedSearchMetadata
import exh.source.getMainSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import tachiyomi.core.util.lang.launchIO
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MetadataViewScreenModel(
    val mangaId: Long,
    val sourceId: Long,
    private val getFlatMetadataById: GetFlatMetadataById = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
) : StateScreenModel<MetadataViewState>(MetadataViewState.Loading) {
    private val _manga = MutableStateFlow<Manga?>(null)
    val manga = _manga.asStateFlow()

    init {
        coroutineScope.launchIO {
            _manga.value = getManga.await(mangaId)
        }

        coroutineScope.launchIO {
            val metadataSource = sourceManager.get(sourceId)?.getMainSource<MetadataSource<*, *>>()
            if (metadataSource == null) {
                mutableState.value = MetadataViewState.SourceNotFound
                return@launchIO
            }

            mutableState.value = when (val flatMetadata = getFlatMetadataById.await(mangaId)) {
                null -> MetadataViewState.MetadataNotFound
                else -> MetadataViewState.Success(flatMetadata.raise(metadataSource.metaClass))
            }
        }
    }
}

sealed class MetadataViewState {
    object Loading : MetadataViewState()
    data class Success(val meta: RaisedSearchMetadata) : MetadataViewState()
    object MetadataNotFound : MetadataViewState()
    object SourceNotFound : MetadataViewState()
}
