package app.komikku.desktop.platform

import app.komikku.desktop.updater.DesktopOs
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * M2: the folder dialog per OS (macOS's own folder panel, which shows folders created while it is open; Swing's chooser
 * elsewhere) and the macOS switch being set only around the panel. The dialogs themselves cannot open headless.
 */
class FolderDialogSelectionTest {

    @Test
    fun `macOS gets its folder panel, Windows and Linux the Swing chooser`() {
        folderDialogKind(DesktopOs.MacOs) shouldBe FolderDialogKind.MacFolderPanel
        folderDialogKind(DesktopOs.Windows) shouldBe FolderDialogKind.SwingChooser
        folderDialogKind(DesktopOs.Linux) shouldBe FolderDialogKind.SwingChooser
        folderDialogKind(DesktopOs.current("Mac OS X")) shouldBe FolderDialogKind.MacFolderPanel
    }

    @Test
    fun `the folder switch is set only while the panel is open`() {
        System.clearProperty(MAC_FOLDER_DIALOG_PROPERTY)
        withSystemProperty(MAC_FOLDER_DIALOG_PROPERTY, "true") { System.getProperty(MAC_FOLDER_DIALOG_PROPERTY) } shouldBe "true"
        System.getProperty(MAC_FOLDER_DIALOG_PROPERTY) shouldBe null

        // An earlier value comes back, also when the dialog fails
        System.setProperty(MAC_FOLDER_DIALOG_PROPERTY, "false")
        try {
            shouldThrow<IllegalStateException> {
                withSystemProperty(MAC_FOLDER_DIALOG_PROPERTY, "true") { error("dialog failed") }
            }
            System.getProperty(MAC_FOLDER_DIALOG_PROPERTY) shouldBe "false"
        } finally {
            System.clearProperty(MAC_FOLDER_DIALOG_PROPERTY)
        }
    }
}
