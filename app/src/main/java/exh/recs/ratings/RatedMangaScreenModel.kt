package exh.recs.ratings

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.taste.interactor.GetMangaTaste
import tachiyomi.domain.taste.model.MangaRating
import tachiyomi.domain.taste.model.MangaTaste
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class RatedMangaScreenModel(
    private val getMangaTaste: GetMangaTaste = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
) : StateScreenModel<RatedMangaScreenModel.State>(State.Loading(MangaRating.LOVE)) {
    private val selectedRating = MutableStateFlow(MangaRating.LOVE)

    init {
        screenModelScope.launch {
            getMangaTaste.subscribeAll()
                .combine(selectedRating) { tastes, rating -> tastes to rating }
                .collectLatest { (tastes, rating) ->
                    mutableState.value = runCatching {
                        val entries = tastes
                            .filter { it.rating == rating.value }
                            .sortedByDescending { it.updatedAt }
                            .mapNotNull { taste ->
                                val manga = getManga.await(taste.mangaId)
                                    ?: getManga.await(taste.url, taste.source)
                                    ?: return@mapNotNull null
                                RatedMangaEntry(taste = taste, manga = manga)
                            }
                        State.Success(rating, entries)
                    }.getOrElse { State.Error(rating) }
                }
        }
    }

    fun selectRating(rating: MangaRating) {
        selectedRating.value = rating
    }

    sealed interface State {
        val rating: MangaRating

        @Immutable
        data class Loading(override val rating: MangaRating) : State

        @Immutable
        data class Success(
            override val rating: MangaRating,
            val entries: List<RatedMangaEntry>,
        ) : State

        @Immutable
        data class Error(override val rating: MangaRating) : State
    }
}

@Immutable
data class RatedMangaEntry(
    val taste: MangaTaste,
    val manga: Manga,
)
