package app.komikku.desktop.instance

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.DesktopPreferenceStore
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import java.io.File

/**
 * ANSWER 013: the banner of a data folder that cannot be locked shows above the app until it is dismissed, and not
 * again for that data folder (its preferences); a folder that can be locked shows none.
 */
@OptIn(ExperimentalTestApi::class)
class UnlockableFolderBannerUiTest {

    @TempDir
    lateinit var dataDir: File

    private val warning = stringResource(KMR.strings.desktop_unlockable_data_folder)

    @Test
    fun `the banner shows until it is dismissed and not on the next start`() = runDesktopComposeUiTest {
        // A new store over the same file for each start, like a new process
        fun dismissed() = unlockableFolderBannerDismissed(DesktopPreferenceStore(File(dataDir, "preferences.json")))
        var start by mutableIntStateOf(0)
        setContent {
            key(start) { UnlockableFolderBanner(show = true, dismissed = remember { dismissed() }) { Text("App") } }
        }

        onNodeWithText(warning).assertExists()
        onNodeWithText("App").assertExists()
        onNodeWithText(stringResource(MR.strings.action_ok)).performClick()
        onNodeWithText(warning).assertDoesNotExist()

        start++
        waitForIdle()
        dismissed().get() shouldBe true
        onNodeWithText(warning).assertDoesNotExist()
        onNodeWithText("App").assertExists()
    }

    @Test
    fun `a folder that can be locked shows no banner`() = runDesktopComposeUiTest {
        val dismissed = unlockableFolderBannerDismissed(DesktopPreferenceStore(File(dataDir, "preferences.json")))
        setContent { UnlockableFolderBanner(show = false, dismissed = dismissed) { Text("App") } }

        onNodeWithText(warning).assertDoesNotExist()
        onNodeWithText("App").assertExists()
    }
}
