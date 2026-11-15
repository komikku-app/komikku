package eu.kanade.domain.extension.interactor

import eu.kanade.domain.source.service.SourcePreferences
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import mihon.domain.extension.interactor.UpdateExtensionStores
import mihon.domain.extension.model.ChangedExtensionStore
import mihon.domain.extension.model.ExtensionStore
import mihon.domain.extension.model.ExtensionStoresRefresh
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore.InMemoryPreference

class RefreshExtensionStoresTest {

    private val disabledRepos = InMemoryPreference<Set<String>>("disabled_repos", null, emptySet())
    private val sourcePreferences = mockk<SourcePreferences> {
        every { disabledRepos() } returns this@RefreshExtensionStoresTest.disabledRepos
    }
    private val updateExtensionStores = mockk<UpdateExtensionStores>()
    private val refreshExtensionStores = RefreshExtensionStores(updateExtensionStores, sourcePreferences)

    @Test
    fun `moves disabled stores to their new URL`() = runBlocking<Unit> {
        disabledRepos.set(setOf(KEI_OLD, OTHER))
        coEvery { updateExtensionStores() } returns ExtensionStoresRefresh(renamed = mapOf(KEI_OLD to KEI_NEW))

        refreshExtensionStores() shouldBe emptyList()
        disabledRepos.get() shouldBe setOf(KEI_NEW, OTHER)
    }

    @Test
    fun `reports changed stores only if they are enabled`() = runBlocking<Unit> {
        disabledRepos.set(setOf(OTHER))
        val enabled = ChangedExtensionStore(store(YU), servedName = "Keiyoushi")
        val disabled = ChangedExtensionStore(store(OTHER), servedName = "Someone else")
        coEvery { updateExtensionStores() } returns ExtensionStoresRefresh(changed = listOf(enabled, disabled))

        refreshExtensionStores() shouldContainExactly listOf(enabled)
        disabledRepos.get() shouldBe setOf(OTHER)
    }

    private fun store(indexUrl: String) = ExtensionStore(
        indexUrl = indexUrl,
        name = indexUrl,
        badgeLabel = indexUrl,
        signingKey = "key",
        contact = ExtensionStore.Contact(website = indexUrl, discord = null),
        isLegacy = false,
        extensionListUrl = null,
    )

    private companion object {
        const val KEI_OLD = "https://raw.githubusercontent.com/keiyoushi/extensions/repo/repo.json"
        const val KEI_NEW = "https://github.com/keiyoushi/extensions/raw/repo/index.pb"
        const val YU = "https://github.com/yuzono/manga-repo/raw/repo/index.pb"
        const val OTHER = "https://example.org/index.pb"
    }
}
