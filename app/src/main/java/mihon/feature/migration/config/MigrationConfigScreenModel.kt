package mihon.feature.migration.config

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.source.model.Source
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/*
 * KMK: the screen model of Mihon's / Komikku's MigrationConfigScreen (which sources the migration searches, in which
 * order), moved here unchanged from the screen file so the desktop app borrows it (P19); Android's screen uses it as
 * before. It uses the Android-only LocaleHelper (desktop counterpart in app-shared/src/desktopMain), hence a desktop copy.
 */

class MigrationConfigScreenModel(
    val sourcePreferences: SourcePreferences = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
) : StateScreenModel<MigrationConfigScreenModel.State>(State()) {

    // KMK -->
    private val pinnedSources by lazy { sourcePreferences.pinnedSources().get().mapNotNull { it.toLongOrNull() } }
    private val disabledSources by lazy { sourcePreferences.disabledSources().get().mapNotNull { it.toLongOrNull() } }
    // KMK <--

    private val sourcesComparator = { includedSources: /* KMK --> */ Map<Long, Int> /* KMK <-- */ ->
        compareBy<MigrationSource>(
            // KMK -->
            // { !it.isSelected },
            { includedSources[it.source.id] ?: Int.MAX_VALUE },
            // KMK <--
            { with(it) { "$name ($shortLanguage)" } },
        )
    }

    init {
        screenModelScope.launchIO {
            initSources()
            mutableState.update { it.copy(isLoading = false) }
        }
    }

    private fun updateSources(action: (List<MigrationSource>) -> List<MigrationSource>) {
        mutableState.update { state ->
            val updatedSources = action(state.sources)
            val includedSources = updatedSources.mapNotNull { if (!it.isSelected) null else it.id }
                // KMK -->
                .mapIndexed { index, id -> id to index }.toMap()
            // KMK <--
            state.copy(sources = updatedSources.sortedWith(sourcesComparator(includedSources)))
        }
        saveSources()
    }

    private fun initSources() {
        val languages = sourcePreferences.enabledLanguages().get()
        val includedSources = sourcePreferences.migrationSources().get()
            // KMK -->
            .mapIndexed { index, id -> id to index }.toMap()
        // KMK <--
        val sources = sourceManager
            // KMK -->
            .getVisibleSources()
            // KMK <--
            .asSequence()
            .filterIsInstance<HttpSource>()
            .filter { it.lang in languages }
            .map {
                val source = Source(
                    id = it.id,
                    lang = it.lang,
                    name = it.name,
                    supportsLatest = false,
                    isStub = false,
                )
                MigrationSource(
                    source = source,
                    isSelected = when {
                        includedSources.isNotEmpty() -> source.id in includedSources
                        pinnedSources.isNotEmpty() -> source.id in pinnedSources
                        else -> source.id !in disabledSources
                    },
                )
            }
            .toList()

        mutableState.update { state ->
            state.copy(sources = sources.sortedWith(sourcesComparator(includedSources)))
        }
    }

    fun toggleSelection(id: Long) {
        updateSources { sources ->
            sources.map { source ->
                source.copy(isSelected = if (source.source.id == id) !source.isSelected else source.isSelected)
            }
        }
    }

    fun toggleSelection(config: SelectionConfig) {
        val isSelected: (Long) -> Boolean = {
            when (config) {
                SelectionConfig.All -> true
                SelectionConfig.None -> false
                SelectionConfig.Pinned -> it in pinnedSources
                SelectionConfig.Enabled -> it !in disabledSources
            }
        }
        updateSources { sources ->
            sources.map { source ->
                source.copy(isSelected = isSelected(source.source.id))
            }
        }
    }

    fun orderSource(from: Int, to: Int) {
        updateSources {
            it.toMutableList()
                .apply {
                    add(to, removeAt(from))
                }
                .toList()
        }
    }

    fun saveSources() {
        state.value.sources
            .filter { source -> source.isSelected }
            .map { source -> source.source.id }
            .let { sources -> sourcePreferences.migrationSources().set(sources) }
    }

    data class State(
        val isLoading: Boolean = true,
        val sources: List<MigrationSource> = emptyList(),
    )

    enum class SelectionConfig {
        All,
        None,
        Pinned,
        Enabled,
    }
}

data class MigrationSource(
    val source: Source,
    val isSelected: Boolean,
    // KMK -->
    val shortLanguage: String = LocaleHelper.getShortDisplayName(source.lang),
    val upperShortLanguage: String = shortLanguage.uppercase(),
    // KMK <--
) {
    val id: Long
        inline get() = source.id

    val name: String
        inline get() = source.name
}
