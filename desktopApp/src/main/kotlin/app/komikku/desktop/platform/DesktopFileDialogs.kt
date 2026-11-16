package app.komikku.desktop.platform

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities

/**
 * KMK: native file choosers behind an interface so screens can be tested with fixed paths. The desktop
 * app registers [AwtFileDialogs]; tests register their own implementation.
 */
interface DesktopFileDialogs {
    /** "Save as" dialog; `null` when cancelled. */
    fun saveFile(title: String, directory: File?, suggestedName: String): File?

    /** "Open" dialog for files ending in one of [extensions]; `null` when cancelled. */
    fun openFile(title: String, directory: File?, extensions: Set<String>): File?

    /** Folder chooser; `null` when cancelled. */
    fun chooseDirectory(title: String, directory: File?): File?
}

/** AWT `FileDialog` (native look) for files, Swing `JFileChooser` for folders (AWT has none on Windows / Linux). */
class AwtFileDialogs : DesktopFileDialogs {

    override fun saveFile(title: String, directory: File?, suggestedName: String): File? = onEdt {
        val dialog = FileDialog(null as Frame?, title, FileDialog.SAVE).apply {
            this.directory = directory?.absolutePath
            file = suggestedName
            isVisible = true
        }
        dialog.file?.let { File(dialog.directory, it) }
    }

    override fun openFile(title: String, directory: File?, extensions: Set<String>): File? = onEdt {
        val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD).apply {
            this.directory = directory?.absolutePath
            setFilenameFilter { _, name -> extensions.isEmpty() || extensions.any { name.endsWith(".$it", ignoreCase = true) } }
            isVisible = true
        }
        dialog.file?.let { File(dialog.directory, it) }
    }

    override fun chooseDirectory(title: String, directory: File?): File? = onEdt {
        val chooser = JFileChooser(directory).apply {
            dialogTitle = title
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }

    private fun <T> onEdt(block: () -> T): T {
        if (SwingUtilities.isEventDispatchThread()) return block()
        var result: Result<T>? = null
        SwingUtilities.invokeAndWait { result = runCatching(block) }
        return result!!.getOrThrow()
    }
}
