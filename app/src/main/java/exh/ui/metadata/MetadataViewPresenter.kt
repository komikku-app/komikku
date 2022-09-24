package exh.ui.metadata

import android.os.Bundle
import eu.kanade.domain.manga.interactor.GetFlatMetadataById
import eu.kanade.domain.manga.model.Manga
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.MetadataSource
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.util.lang.launchNonCancellable
import exh.metadata.metadata.base.RaisedSearchMetadata
import exh.source.getMainSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MetadataViewPresenter(
    val manga: Manga,
    val source: Source,
    private val getFlatMetadataById: GetFlatMetadataById = Injekt.get(),
) : BasePresenter<MetadataViewController>() {
    private val _state = MutableStateFlow<MetadataViewState>(MetadataViewState.Loading)
    val state = _state.asStateFlow()

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        presenterScope.launchNonCancellable {
            val metadataSource = source.getMainSource<MetadataSource<*, *>>()
            if (metadataSource == null) {
                _state.value = MetadataViewState.SourceNotFound
                return@launchNonCancellable
            }

            _state.value = when (val flatMetadata = getFlatMetadataById.await(manga.id)) {
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
