package app.komikku.desktop.ui.browse

import android.text.InputType
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import tachiyomi.domain.source.model.StubSource
import tachiyomi.domain.source.service.SourceManager

/** An in-test catalogue source with settings: fixed listings, details and chapters, no network. */
class FakeCatalogueSource : CatalogueSource, ConfigurableSource {

    override val id: Long = ID
    override val name: String = "Fake source"
    override val lang: String = "en"
    override val supportsLatest: Boolean = true

    private fun manga(title: String) = SManga.create().apply {
        url = "/manga/${title.lowercase().replace(' ', '-')}"
        this.title = title
    }

    override suspend fun getPopularManga(page: Int): MangasPage =
        MangasPage(listOf(manga("Popular manga $page")), hasNextPage = page < 2)

    override suspend fun getLatestUpdates(page: Int): MangasPage = MangasPage(listOf(manga("Latest manga")), hasNextPage = false)

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage =
        MangasPage(listOf(manga("Result for $query")), hasNextPage = false)

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val details = SManga.create().apply {
            url = manga.url
            title = manga.title
            author = "Fake author"
            description = "Fake description"
            genre = "Action, Comedy"
            initialized = true
        }
        val list = (2 downTo 1).map { number ->
            SChapter.create().apply {
                url = "${manga.url}/$number"
                name = "Chapter $number"
                chapter_number = number.toFloat()
            }
        }
        return SMangaUpdate(details, list)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = emptyList()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val context = screen.context
        screen.addPreference(
            SwitchPreferenceCompat(context).apply {
                key = "fake_switch"
                title = "Fake switch"
                setDefaultValue(false)
            },
        )
        screen.addPreference(
            ListPreference(context).apply {
                key = "fake_list"
                title = "Fake list"
                entries = arrayOf("One", "Two")
                entryValues = arrayOf("1", "2")
                summary = "%s"
                setDefaultValue("1")
            },
        )
        screen.addPreference(
            MultiSelectListPreference(context).apply {
                key = "fake_multi"
                title = "Fake multi"
                entries = arrayOf("Red", "Blue")
                entryValues = arrayOf("red", "blue")
                setDefaultValue(emptySet<String>())
            },
        )
        screen.addPreference(
            EditTextPreference(context).apply {
                key = "fake_text"
                title = "Fake text"
                setOnBindEditTextListener { it.inputType = InputType.TYPE_CLASS_NUMBER }
            },
        )
        val category = PreferenceCategory(context).apply { title = "Fake category" }
        screen.addPreference(category)
        category.addPreference(
            CheckBoxPreference(context).apply {
                key = "fake_check"
                title = "Fake check"
                setDefaultValue(false)
            },
        )
    }

    companion object {
        const val ID = 9001L
    }
}

/**
 * A [SourceManager] over fixed sources, for UI tests; [setSources] replaces them (an extension installed / updated),
 * a lookup of an id in [failingIds] throws.
 */
class FixedSourceManager(sources: List<Source>) : SourceManager {
    private val current = MutableStateFlow(sources.associateBy { it.id })
    private val map get() = current.value
    override val isInitialized: StateFlow<Boolean> = MutableStateFlow(true)
    override val sources: Flow<List<Source>> = current.map { it.values.toList() }

    @Volatile var failingIds: Set<Long> = emptySet()
    fun setSources(sources: List<Source>) {
        current.value = sources.associateBy { it.id }
    }
    override fun get(sourceKey: Long): Source? {
        check(sourceKey !in failingIds) { "Lookup of source $sourceKey failed" }
        return map[sourceKey]
    }
    override fun getOrStub(sourceKey: Long): Source = map[sourceKey] ?: StubSource(id = sourceKey, lang = "", name = "")
    override fun getAll(): List<Source> = map.values.toList()
    override fun getOnlineSources(): List<HttpSource> = map.values.filterIsInstance<HttpSource>()
    override fun getVisibleOnlineSources(): List<HttpSource> = getOnlineSources()
    override fun getVisibleSources(): List<Source> = getAll()
    override suspend fun getMergedSources(mangaId: Long): List<Source> = emptyList()
    override fun getStubSources(): List<StubSource> = emptyList()
}

/**
 * A minimal online source for source list and global search tests (an [HttpSource], so it is in the source filter
 * screen like an extension source) without network: search returns "<prefix> <query>" (or nothing, or fails with
 * [error]); popular returns "<prefix> popular".
 */
class SimpleSearchSource(
    override val id: Long,
    override val name: String,
    override val lang: String = "en",
    private val prefix: String? = name,
    private val error: String? = null,
) : HttpSource() {

    override val baseUrl: String = "https://simple.example.org"

    override val supportsLatest: Boolean = false

    private fun page(title: String?) = MangasPage(
        listOfNotNull(title).map { t ->
            SManga.create().apply {
                url = "/$id/${t.lowercase().replace(' ', '-')}"
                this.title = t
            }
        },
        hasNextPage = false,
    )

    override suspend fun getPopularManga(page: Int): MangasPage = page(prefix?.let { "$it popular" })

    override suspend fun getLatestUpdates(page: Int): MangasPage = page(null)

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
        error?.let { throw java.io.IOException(it) }
        return page(prefix?.let { "$it $query" })
    }

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = SMangaUpdate(manga, emptyList())

    override suspend fun getPageList(chapter: SChapter): List<Page> = emptyList()
}
