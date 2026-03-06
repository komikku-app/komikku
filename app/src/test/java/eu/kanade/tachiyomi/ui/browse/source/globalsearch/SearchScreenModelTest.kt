package eu.kanade.tachiyomi.ui.browse.source.globalsearch

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SearchScreenModelTest {

    @Test
    fun normalizeSearchSourceFilter_switchesToAllWhenPinnedOnlyHasNoPinnedSources() {
        assertEquals(
            SourceFilter.All,
            normalizeSearchSourceFilter(
                currentFilter = SourceFilter.PinnedOnly,
                hasPinnedSources = false,
            ),
        )
    }

    @Test
    fun normalizeSearchSourceFilter_keepsCurrentFilterOtherwise() {
        assertEquals(
            SourceFilter.PinnedOnly,
            normalizeSearchSourceFilter(
                currentFilter = SourceFilter.PinnedOnly,
                hasPinnedSources = true,
            ),
        )
        assertEquals(
            SourceFilter.All,
            normalizeSearchSourceFilter(
                currentFilter = SourceFilter.All,
                hasPinnedSources = false,
            ),
        )
    }
}
