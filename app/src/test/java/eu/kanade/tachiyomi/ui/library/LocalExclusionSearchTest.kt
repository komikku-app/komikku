// KMK -->
package eu.kanade.tachiyomi.ui.library

import android.app.Application
import exh.search.SearchEngine
import io.mockk.mockk
import io.mockk.spyk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton

class LocalExclusionSearchTest {
    private val searchEngine = SearchEngine()
    private val screenModel = spyk(mockk<LibraryScreenModel>(), recordPrivateCalls = true)

    @BeforeEach
    fun setUp() {
        Injekt.addSingleton<Application>(mockk())
    }

    private fun matches(
        query: String,
        source: Long = 0L,
        title: String = "Orion",
        genres: List<String> = emptyList(),
    ): Boolean {
        val manga = Manga.create().copy(source = source, ogTitle = title, ogGenre = genres)
        val libraryManga = LibraryManga(manga, emptyList(), 1, 0, 0, 0, 0, 0, 0, 0)
        val queries = searchEngine.parseQuery(query)
        // Exercise the actual production predicate without starting the ScreenModel's background subscriptions.
        val method = LibraryScreenModel::class.java.declaredMethods.single { it.name == "filterManga" }
        method.isAccessible = true
        return method.invoke(screenModel, queries, libraryManga, null, null, true, null, null, emptyMap<Long, TriState>()) as Boolean
    }

    @Test
    fun unrelatedExclusionKeepsLocalManga() = assertEquals(true, matches("-meteor"))

    @Test
    fun matchingExclusionRemovesLocalManga() = assertEquals(false, matches("-orion"))

    @Test
    fun otherFieldsStillExcludeLocalManga() = assertEquals(false, matches("-space", genres = listOf("Space")))

    @Test
    fun localSourceIsNotMatchedAsNumericZero() = assertEquals(true, matches("-0"))

    @Test
    fun matchingRemoteSourceIsExcluded() = assertEquals(false, matches("-42", source = 42L))

    @Test
    fun unrelatedRemoteSourceIsKept() = assertEquals(true, matches("-43", source = 42L))

    @Test
    fun positiveTitleStillMatches() = assertEquals(true, matches("orion"))

    @Test
    fun unrelatedPositiveTitleDoesNotMatch() = assertEquals(false, matches("meteor"))
}
// KMK <--
