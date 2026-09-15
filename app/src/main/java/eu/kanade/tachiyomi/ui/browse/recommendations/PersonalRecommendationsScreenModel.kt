package eu.kanade.tachiyomi.ui.browse.recommendations

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.produceState
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import mihon.domain.manga.model.toDomainManga
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.PersonalTasteProfile
import tachiyomi.domain.manga.model.buildPersonalTasteProfile
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.taste.repository.TasteRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class PersonalRecommendationsScreenModel(
    private val mangaRepository: MangaRepository = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val tasteRepository: TasteRepository = Injekt.get(),
) : StateScreenModel<PersonalRecommendationsScreenModel.State>(State()) {

    private val searchSemaphore = Semaphore(MAX_CONCURRENT_SEARCHES)
    private var refreshJob: Job? = null
    private var generation = 0L
    private var sourceOffset = 0
    private var started = false

    fun start() {
        if (started) return
        started = true
        refresh()
        screenModelScope.launch {
            tasteRepository.getAllMangaTastesAsFlow()
                .combine(mangaRepository.getLibraryMangaAsFlow()) { tastes, library ->
                    tastes.map { it.mangaId to it.rating }.sortedBy { it.first } to
                        library.map { it.id }.sorted()
                }
                .distinctUntilChanged()
                .drop(1)
                .collectLatest { refresh() }
        }
    }

    fun refresh() {
        refreshJob?.cancel()
        val currentGeneration = ++generation
        refreshJob = screenModelScope.launch {
            try {
                mutableState.update { State() }

                val allManga = mangaRepository.getFavorites()
                if (currentGeneration != generation) return@launch
                val ratings = tasteRepository.getAllMangaTastes()
                    .mapNotNull { taste -> taste.mangaRating?.let { taste.mangaId to it } }
                    .toMap()
                val profile = buildPersonalTasteProfile(allManga, ratings)
                if (profile == null) {
                    mutableState.update { it.copy(isPreparing = false, needsRatings = true) }
                    return@launch
                }

                val enabledSources = enabledSources()
                val sources = selectSources(enabledSources)
                updateItems(sources.associateWith { Result.Loading }, currentGeneration)
                if (currentGeneration != generation) return@launch
                mutableState.update {
                    it.copy(
                        isPreparing = false,
                    )
                }

                val libraryKeys = allManga.mapTo(hashSetOf()) { it.source to it.url }
                if (currentGeneration != generation) return@launch
                supervisorScope {
                    sources.forEach { source ->
                        launch {
                            searchSemaphore.withPermit {
                                updateItem(source, search(source, profile, libraryKeys), currentGeneration)
                            }
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (currentGeneration == generation) {
                    mutableState.update { it.copy(isPreparing = false, preparationFailed = true) }
                }
            }
        }
    }

    @Composable
    fun getManga(initialManga: Manga): androidx.compose.runtime.State<Manga> {
        return produceState(initialValue = initialManga) {
            getManga.subscribe(initialManga.url, initialManga.source)
                .filterNotNull()
                .collectLatest { value = it }
        }
    }

    private fun enabledSources(): List<CatalogueSource> {
        val enabledLanguages = sourcePreferences.enabledLanguages().get()
        val disabledSources = sourcePreferences.disabledSources().get()
        val pinnedSources = sourcePreferences.pinnedSources().get()
        return sourceManager.getVisibleSources()
            .filterIsInstance<CatalogueSource>()
            .filter { it.lang in enabledLanguages && it.id.toString() !in disabledSources }
            .sortedWith(compareBy({ it.id.toString() !in pinnedSources }, { it.name.lowercase() }, { it.lang }))
    }

    private fun selectSources(sources: List<CatalogueSource>): List<CatalogueSource> {
        if (sources.size <= MAX_SOURCES) return sources
        val start = sourceOffset.mod(sources.size)
        sourceOffset = (start + MAX_SOURCES).mod(sources.size)
        return List(MAX_SOURCES) { index -> sources[(start + index).mod(sources.size)] }
    }

    private suspend fun search(
        source: CatalogueSource,
        profile: PersonalTasteProfile,
        libraryKeys: Set<Pair<Long, String>>,
    ): Result {
        try {
            val ranked = withTimeout(SOURCE_LOAD_TIMEOUT_MILLIS) {
                val candidates = source.getPopularManga(1).mangas
                    .asSequence()
                    .distinctBy { it.url }
                    .filterNot { manga -> source.id to manga.url in libraryKeys }
                    .take(MAX_CANDIDATES_PER_SOURCE)
                    .toList()

                var loadedDetails = 0
                var firstDetailFailure: Exception? = null
                val detailedCandidates = candidates.map { summary ->
                    try {
                        source.getMangaUpdate(
                            manga = summary,
                            chapters = emptyList(),
                            fetchDetails = true,
                            fetchChapters = false,
                        ).manga.also { loadedDetails++ }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        firstDetailFailure = firstDetailFailure ?: error
                        summary
                    }
                }
                if (candidates.isNotEmpty() && loadedDetails == 0 && firstDetailFailure != null) {
                    throw checkNotNull(firstDetailFailure)
                }

                detailedCandidates
                    .asSequence()
                    .map { it.toDomainManga(source.id) }
                    .map { manga -> manga to profile.score(manga.ogGenre.orEmpty()) }
                    .filter { (_, score) -> score > 0 }
                    .sortedWith(compareByDescending<Pair<Manga, Int>> { it.second }.thenBy { it.first.title })
                    .map { it.first }
                    .take(MAX_RESULTS_PER_SOURCE)
                    .toList()
                    .let { networkToLocalManga(it) }
            }

            return Result.Success(ranked)
        } catch (error: TimeoutCancellationException) {
            return Result.Error(error)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return Result.Error(error)
        }
    }

    private fun updateItems(items: Map<Source, Result>, currentGeneration: Long) {
        mutableState.update {
            if (currentGeneration != generation) {
                it
            } else {
                it.copy(
                    items = items.toSortedMap(SOURCE_COMPARATOR).toPersistentMap(),
                )
            }
        }
    }

    private fun updateItem(source: Source, result: Result, currentGeneration: Long) {
        if (!screenModelScope.isActive) return
        mutableState.update { current ->
            if (currentGeneration != generation) {
                current
            } else {
                current.copy(
                    items = (current.items + (source to result))
                        .toSortedMap(SOURCE_COMPARATOR)
                        .toPersistentMap(),
                )
            }
        }
    }

    @Immutable
    data class State(
        val isPreparing: Boolean = true,
        val needsRatings: Boolean = false,
        val preparationFailed: Boolean = false,
        val items: PersistentMap<Source, Result> = persistentMapOf(),
    ) {
        val progress = items.count { it.value !is Result.Loading }
        val hasResults = items.values.any { it is Result.Success && it.manga.isNotEmpty() }
        val allSourcesFailed = items.isNotEmpty() && items.values.all { it is Result.Error }
        val isFinished = !isPreparing && !needsRatings && !preparationFailed && progress == items.size
    }

    sealed interface Result {
        data object Loading : Result
        data class Error(val throwable: Throwable) : Result
        data class Success(val manga: List<Manga>) : Result
    }

    private companion object {
        val SOURCE_COMPARATOR = compareBy<Source>(
            { it.name.lowercase() },
            { it.lang },
            { it.id },
        )
        const val MAX_CONCURRENT_SEARCHES = 4
        const val MAX_RESULTS_PER_SOURCE = 12
        const val MAX_CANDIDATES_PER_SOURCE = 8
        const val MAX_SOURCES = 8
        const val SOURCE_LOAD_TIMEOUT_MILLIS = 30_000L
    }
}
