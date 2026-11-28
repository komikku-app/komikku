package eu.kanade.tachiyomi.ui.browse.migration.sources

import eu.kanade.domain.source.interactor.GetSourcesWithFavoriteCount
import eu.kanade.domain.source.interactor.SetMigrateSorting
import eu.kanade.domain.source.service.SourcePreferences
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class MigrateSourceScreenModelTest {

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a failed read of the sources ends the loading`() = runBlocking<Unit> {
        val preferences = mockk<SourcePreferences> {
            every { migrationSortingDirection().changes() } returns flowOf(SetMigrateSorting.Direction.ASCENDING)
            every { migrationSortingMode().changes() } returns flowOf(SetMigrateSorting.Mode.ALPHABETICAL)
        }
        val getSourcesWithFavoriteCount = mockk<GetSourcesWithFavoriteCount> {
            every { subscribe() } returns flow { throw IOException("database error") }
        }

        val model = MigrateSourceScreenModel(
            preferences = preferences,
            getSourcesWithFavoriteCount = getSourcesWithFavoriteCount,
            setMigrateSorting = mockk(),
        )

        val state = withTimeout(5.seconds) { model.state.first { !it.isLoading } }
        state.isEmpty shouldBe true
        // The failure is still reported
        withTimeout(5.seconds) { model.channel.first() } shouldBe MigrateSourceScreenModel.Event.FailedFetchingSourcesWithCount
    }
}
