package eu.kanade.domain.vault

import android.content.Context
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.source.service.SourcePreferences
import exh.source.ExhPreferences
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.vault.VaultSession

@Execution(ExecutionMode.SAME_THREAD)
class VaultScopedPreferencesTest {

    private val preferenceStore = mockk<PreferenceStore>()
    private val context = mockk<Context>()

    @BeforeEach
    fun setUp() {
        every { preferenceStore.getBoolean(any(), any()) } answers {
            preference<Boolean>(firstArg())
        }
        every { preferenceStore.getStringSet(any(), any()) } answers {
            preference<Set<String>>(firstArg())
        }
        every { preferenceStore.getLong(any(), any()) } answers {
            preference<Long>(firstArg())
        }
    }

    @AfterEach
    fun tearDown() {
        VaultSession.init(active = false)
    }

    @Test
    fun `standard profile uses the original keys`() {
        VaultSession.init(active = false)

        BasePreferences(context, preferenceStore).incognitoMode().key() shouldBe "__APP_STATE_incognito_mode"
        SourcePreferences(preferenceStore).incognitoExtensions().key() shouldBe "incognito_extensions"
        SourcePreferences(preferenceStore).enabledLanguages().key() shouldBe "source_languages"
        SourcePreferences(preferenceStore).lastUsedSource().key() shouldBe "__APP_STATE_last_catalogue_source"
        ExhPreferences(preferenceStore).ehIncognitoMode().key() shouldBe "eh_incognito_mode"
    }

    @Test
    fun `vault profile uses vault-scoped keys`() {
        VaultSession.init(active = true)

        val incognitoKey = BasePreferences(context, preferenceStore).incognitoMode().key()
        val incognitoExtensionsKey = SourcePreferences(preferenceStore).incognitoExtensions().key()
        val languagesKey = SourcePreferences(preferenceStore).enabledLanguages().key()
        val lastUsedSourceKey = SourcePreferences(preferenceStore).lastUsedSource().key()
        val ehIncognitoKey = ExhPreferences(preferenceStore).ehIncognitoMode().key()

        incognitoKey shouldBe "__APP_STATE_incognito_mode__vault"
        incognitoExtensionsKey shouldBe "incognito_extensions__vault"
        languagesKey shouldBe "source_languages__vault"
        lastUsedSourceKey shouldBe "__APP_STATE_last_catalogue_source__vault"
        ehIncognitoKey shouldBe "eh_incognito_mode__vault"

        VaultSession.isVaultKey(incognitoKey) shouldBe true
        VaultSession.isVaultKey(incognitoExtensionsKey) shouldBe true
        VaultSession.isVaultKey(languagesKey) shouldBe true
        VaultSession.isVaultKey(lastUsedSourceKey) shouldBe true
        VaultSession.isVaultKey(ehIncognitoKey) shouldBe true
    }

    private fun <T> preference(key: String): Preference<T> {
        val preference = mockk<Preference<T>>()
        every { preference.key() } returns key
        return preference
    }
}
