package app.komikku.desktop.settings

import android.app.Application
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.komikku.desktop.KomikkuApp
import app.komikku.desktop.di.DesktopInjekt
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import tachiyomi.domain.backup.service.BackupPreferences
import tachiyomi.domain.library.model.GroupLibraryMode
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * P3: the settings of the scheduled work on the desktop settings screens, bound to Android's preferences: library
 * update interval and grouped updates (Settings → Library), automatic backup frequency and the last automatic backup
 * (Settings → Data and storage).
 */
@OptIn(ExperimentalTestApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScheduleSettingsUiTest {

    private lateinit var app: Application

    @BeforeAll
    fun boot() {
        val dir = File(System.getProperty("komikku.dataDir"), "schedule-settings-ui").apply { mkdirs() }
        app = DesktopInjekt.init(dir)
    }

    @Test
    fun `library update interval and automatic backup frequency`() = runDesktopComposeUiTest(width = 1200, height = 1000) {
        val library = Injekt.get<LibraryPreferences>()
        val backup = Injekt.get<BackupPreferences>()
        backup.lastAutoBackupTimestamp().set(0)
        setContent { KomikkuApp(context = app) }
        onNodeWithText(MR.strings.label_more.localized()).performClick()
        waitForTag("more-settings")
        onNodeWithTag("more-settings").performClick()

        // Settings → Library: automatic updates every 12 hours, grouped updates "all"
        waitForTag("settings-library")
        onNodeWithTag("settings-library").performClick()
        waitForText(MR.strings.pref_library_update_interval.localized())
        onNodeWithText(MR.strings.pref_library_update_interval.localized()).performClick()
        waitForText(MR.strings.update_12hour.localized())
        onNodeWithText(MR.strings.update_12hour.localized()).performClick()
        waitUntil(timeoutMillis = 5_000) { library.autoUpdateInterval().get() == 12 }
        onNodeWithText(SYMR.strings.library_group_updates.localized()).performClick()
        waitForText(SYMR.strings.library_group_updates_all.localized())
        onNodeWithText(SYMR.strings.library_group_updates_all.localized()).performClick()
        waitUntil(timeoutMillis = 5_000) { library.groupLibraryUpdateType().get() == GroupLibraryMode.ALL }
        back()

        // Settings → Data and storage: automatic backups every 6 hours; "last automatically backed up"
        waitForTag("settings-data")
        onNodeWithTag("settings-data").performClick()
        waitForTag("data-storage")
        waitForText(lastBackup(MR.strings.relative_time_span_never.localized()), substring = true)
        onNodeWithText(MR.strings.pref_backup_interval.localized()).performClick()
        waitForText(MR.strings.update_6hour.localized())
        onNodeWithText(MR.strings.update_6hour.localized()).performClick()
        waitUntil(timeoutMillis = 5_000) { backup.backupInterval().get() == 6 }
        runOnIdle { backup.lastAutoBackupTimestamp().set(System.currentTimeMillis()) }
        waitForText(lastBackup(MR.strings.updates_last_update_info_just_now.localized()), substring = true)
    }

    private fun lastBackup(time: String) = MR.strings.last_auto_backup_info.localized().substringBefore("%") + time

    private fun ComposeUiTest.back() = onNodeWithContentDescription(MR.strings.action_bar_up_description.localized()).performClick()

    private fun ComposeUiTest.waitForTag(tag: String) =
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.waitForText(text: String, substring: Boolean = false) =
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }
}
