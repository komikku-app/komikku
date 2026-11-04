package eu.kanade.tachiyomi.extension.model

import io.kotest.matchers.shouldBe
import mihon.domain.extension.model.ExtensionStore
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class ExtensionMatchingTest {

    private val pkg = "eu.kanade.tachiyomi.extension.all.mangadex"

    @Test
    fun `Identity key combines package name and signing key`() {
        installed(versionCode = 1, signature = YUZONO_KEY).identityKey shouldBe "${pkg}_$YUZONO_KEY"
    }

    @Test
    fun `Same package signed by another store's key is not an update`() {
        val installed = installed(versionCode = 211, signature = YUZONO_KEY)
        val available = listOf(available(versionCode = 106001, signature = KEIYOUSHI_KEY, store = keiyoushi))

        installed.findAvailable(available) shouldBe null
    }

    @Test
    fun `Same package and signing key is matched among other stores`() {
        val installed = installed(versionCode = 211, signature = YUZONO_KEY)
        val fromYuzono = available(versionCode = 212, signature = YUZONO_KEY, store = yuzono)
        val available = listOf(
            available(versionCode = 106001, signature = KEIYOUSHI_KEY, store = keiyoushi),
            fromYuzono,
        )

        installed.findAvailable(available) shouldBe fromYuzono
    }

    @Test
    fun `Another package with the same signing key is not matched`() {
        val installed = installed(versionCode = 1, signature = YUZONO_KEY)
        val available = listOf(available(versionCode = 2, signature = YUZONO_KEY, store = yuzono, pkgName = "other"))

        installed.findAvailable(available) shouldBe null
    }

    @Test
    fun `Newest of several stores sharing a signing key is picked`() {
        val installed = installed(versionCode = 1, signature = YUZONO_KEY)
        val newest = available(versionCode = 3, signature = YUZONO_KEY, store = komikku)
        val available = listOf(
            available(versionCode = 2, signature = YUZONO_KEY, store = yuzono),
            newest,
        )

        installed.findAvailable(available) shouldBe newest
    }

    @Test
    fun `Outdated when the version code or extension lib is newer`() {
        val installed = installed(versionCode = 5, signature = YUZONO_KEY, libVersion = 1.4)

        installed.isOutdatedComparedTo(available(versionCode = 5, signature = YUZONO_KEY, store = yuzono)) shouldBe
            false
        installed.isOutdatedComparedTo(available(versionCode = 4, signature = YUZONO_KEY, store = yuzono)) shouldBe
            false
        installed.isOutdatedComparedTo(available(versionCode = 6, signature = YUZONO_KEY, store = yuzono)) shouldBe
            true
        installed.isOutdatedComparedTo(
            available(versionCode = 5, signature = YUZONO_KEY, store = yuzono, libVersion = 1.6),
        ) shouldBe true
    }

    private fun installed(
        versionCode: Long,
        signature: String,
        libVersion: Double = 1.4,
    ) = Extension.Installed(
        name = "MangaDex",
        pkgName = pkg,
        versionName = "$libVersion.$versionCode",
        versionCode = versionCode,
        libVersion = libVersion,
        lang = "all",
        isNsfw = false,
        signatureHash = signature,
        pkgFactory = null,
        sources = emptyList(),
        icon = null,
        isShared = true,
    )

    private fun available(
        versionCode: Long,
        signature: String,
        store: ExtensionStore,
        libVersion: Double = 1.4,
        pkgName: String = pkg,
    ) = Extension.Available(
        name = "MangaDex",
        pkgName = pkgName,
        versionName = "$libVersion.$versionCode",
        versionCode = versionCode,
        libVersion = libVersion,
        lang = "all",
        isNsfw = false,
        signatureHash = signature,
        storeName = store.name,
        sources = emptyList(),
        apkUrl = "${store.indexUrl}/$pkgName.apk",
        iconUrl = "",
        store = store,
    )

    private fun store(name: String, signingKey: String) = ExtensionStore(
        indexUrl = "https://$name.example/repo.json",
        name = name,
        badgeLabel = name,
        signingKey = signingKey,
        contact = ExtensionStore.Contact(website = "", discord = null),
        isLegacy = true,
        extensionListUrl = null,
    )

    private val yuzono = store("yuzono", YUZONO_KEY)
    private val komikku = store("komikku", YUZONO_KEY)
    private val keiyoushi = store("keiyoushi", KEIYOUSHI_KEY)

    companion object {
        private const val YUZONO_KEY = "cbec121a"
        private const val KEIYOUSHI_KEY = "9add655a"
    }
}
