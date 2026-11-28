package eu.kanade.tachiyomi.ui.library

import io.kotest.matchers.shouldBe
import kotlinx.collections.immutable.persistentSetOf
import org.junit.jupiter.api.Test
import tachiyomi.domain.library.model.LibraryGroup

class LibraryScreenModelStateTest {

    @Test
    fun `A category filter with included categories shows the library ungrouped`() {
        val state = LibraryScreenModel.State(
            groupType = LibraryGroup.BY_DEFAULT,
            filterCategory = true,
            includedCategories = persistentSetOf(5L),
        )

        state.effectiveGroupType shouldBe LibraryGroup.UNGROUPED
    }

    @Test
    fun `A category filter overrides any grouping`() {
        val state = LibraryScreenModel.State(
            groupType = LibraryGroup.BY_SOURCE,
            filterCategory = true,
            includedCategories = persistentSetOf(5L),
        )

        state.effectiveGroupType shouldBe LibraryGroup.UNGROUPED
    }

    @Test
    fun `Without a category filter the preference's grouping is used`() {
        val state = LibraryScreenModel.State(
            groupType = LibraryGroup.BY_SOURCE,
            filterCategory = false,
            includedCategories = persistentSetOf(5L),
        )

        state.effectiveGroupType shouldBe LibraryGroup.BY_SOURCE
    }

    @Test
    fun `A category filter without included categories keeps the preference's grouping`() {
        val state = LibraryScreenModel.State(
            groupType = LibraryGroup.BY_DEFAULT,
            filterCategory = true,
            includedCategories = persistentSetOf(),
        )

        state.effectiveGroupType shouldBe LibraryGroup.BY_DEFAULT
    }
}
