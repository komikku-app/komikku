package mihon.domain.extension.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class ExtensionStoreTest {

    @Test
    fun `Label prefers the badge label over the name`() {
        store(YUZONO_URL, YUZONO_KEY, name = "Yuzono", badgeLabel = "Yu").label shouldBe "Yu"
        store(YUZONO_URL, YUZONO_KEY, name = "Yuzono", badgeLabel = " ").label shouldBe "Yuzono"
    }

    @Test
    fun `Refresh keeping the signing key is accepted`() {
        val saved = store(YUZONO_URL, YUZONO_KEY)
        val fetched = store(YUZONO_URL, YUZONO_KEY, name = "Renamed")

        saved.canBeRefreshedTo(fetched, listOf(saved)) shouldBe true
    }

    @Test
    fun `Refresh moving to a new URL with the same signing key is accepted`() {
        val saved = store(KEIYOUSHI_LEGACY_URL, KEIYOUSHI_KEY)
        val fetched = store(KEIYOUSHI_V2_URL, KEIYOUSHI_KEY)

        saved.canBeRefreshedTo(fetched, listOf(saved)) shouldBe true
    }

    @Test
    fun `Refresh to a store signed by another key is refused`() {
        // e.g. a repo.json whose index_v2 now points to another store's index
        val saved = store(YUZONO_URL, YUZONO_KEY)
        val fetched = store(KEIYOUSHI_V2_URL, KEIYOUSHI_KEY)

        saved.canBeRefreshedTo(fetched, listOf(saved)) shouldBe false
    }

    @Test
    fun `Store migrated without a signing key takes the fetched one`() {
        val saved = store(YUZONO_URL, NO_SIGNING_KEY)
        val fetched = store(YUZONO_URL, YUZONO_KEY)

        saved.canBeRefreshedTo(fetched, listOf(saved)) shouldBe true
    }

    @Test
    fun `Refresh moving onto another saved store with another signing key is refused`() {
        val saved = store(YUZONO_URL, NO_SIGNING_KEY)
        val other = store(KEIYOUSHI_V2_URL, KEIYOUSHI_KEY)
        val fetched = store(KEIYOUSHI_V2_URL, YUZONO_KEY)

        saved.canBeRefreshedTo(fetched, listOf(saved, other)) shouldBe false
    }

    @Test
    fun `Refresh moving onto another saved store with the same signing key is accepted`() {
        val saved = store(KEIYOUSHI_LEGACY_URL, KEIYOUSHI_KEY)
        val other = store(KEIYOUSHI_V2_URL, KEIYOUSHI_KEY)
        val fetched = store(KEIYOUSHI_V2_URL, KEIYOUSHI_KEY)

        saved.canBeRefreshedTo(fetched, listOf(saved, other)) shouldBe true
        saved.canBeRefreshedTo(fetched, listOf(saved, store(KEIYOUSHI_V2_URL, NO_SIGNING_KEY))) shouldBe true
    }

    @Test
    fun `Renamed store URLs are remapped`() {
        val disabled = setOf(KEIYOUSHI_LEGACY_URL, YUZONO_URL)

        disabled.remapIndexUrls(mapOf(KEIYOUSHI_LEGACY_URL to KEIYOUSHI_V2_URL)) shouldBe
            setOf(KEIYOUSHI_V2_URL, YUZONO_URL)
    }

    @Test
    fun `Chained renames are followed and cycles stop`() {
        setOf("a").remapIndexUrls(mapOf("a" to "b", "b" to "c")) shouldBe setOf("c")
        setOf("a").remapIndexUrls(mapOf("a" to "b", "b" to "a")) shouldBe setOf("b")
    }

    @Test
    fun `Nothing renamed keeps the URLs`() {
        val disabled = setOf(YUZONO_URL)

        disabled.remapIndexUrls(emptyMap()) shouldBe disabled
    }

    // KMK -->
    @Test
    fun `GitHub owner is parsed from GitHub-hosted index URLs`() {
        store(YUZONO_URL, YUZONO_KEY).githubOwner shouldBe "yuzono"
        store(KEIYOUSHI_LEGACY_URL, KEIYOUSHI_KEY).githubOwner shouldBe "keiyoushi"
        store(KEIYOUSHI_V2_URL, KEIYOUSHI_KEY).githubOwner shouldBe "keiyoushi"
        store("https://github.com/some-org/repo/blob/main/index.min.json", "k").githubOwner shouldBe "some-org"
        store("https://Komikku-App.github.io/extensions/index.min.json", "k").githubOwner shouldBe "Komikku-App"
        store("https://komikku-app.github.io", "k").githubOwner shouldBe "komikku-app"
    }

    @Test
    fun `GitHub owner is null for other hosts and malformed URLs`() {
        store("https://example.com/yuzono/manga-repo/repo", "k").githubOwner shouldBe null
        store("https://raw.githubusercontent.com.evil.com/yuzono/repo/x", "k").githubOwner shouldBe null
        store("https://evil.com/https://raw.githubusercontent.com/yuzono/repo/x", "k").githubOwner shouldBe null
        store("http://raw.githubusercontent.com/yuzono/manga-repo/repo", "k").githubOwner shouldBe null
        store("https://raw.githubusercontent.com/yuzono", "k").githubOwner shouldBe null
        store("https://raw.githubusercontent.com/bad.name/repo/x", "k").githubOwner shouldBe null
        store("https://github.com/keiyoushi/extensions", "k").githubOwner shouldBe null
        store("https://a.b.github.io/x", "k").githubOwner shouldBe null
        store("NO_URL", NO_SIGNING_KEY).githubOwner shouldBe null
    }

    @Test
    fun `GitHub avatar URL points to the owner's avatar`() {
        store(YUZONO_URL, YUZONO_KEY).githubAvatarUrl shouldBe "https://github.com/yuzono.png?size=144"
        store("https://example.com/index.min.json", "k").githubAvatarUrl shouldBe null
    }
    // KMK <--

    private fun store(
        indexUrl: String,
        signingKey: String,
        name: String = "Store",
        badgeLabel: String = name,
    ) = ExtensionStore(
        indexUrl = indexUrl,
        name = name,
        badgeLabel = badgeLabel,
        signingKey = signingKey,
        contact = ExtensionStore.Contact(website = "", discord = null),
        isLegacy = false,
        extensionListUrl = null,
    )

    companion object {
        private const val YUZONO_URL = "https://raw.githubusercontent.com/yuzono/manga-repo/repo/repo.json"
        private const val KEIYOUSHI_LEGACY_URL = "https://raw.githubusercontent.com/keiyoushi/extensions/repo/repo.json"
        private const val KEIYOUSHI_V2_URL = "https://github.com/keiyoushi/extensions/raw/repo/index.pb"
        private const val YUZONO_KEY = "cbec121a"
        private const val KEIYOUSHI_KEY = "9add655a"
    }
}
