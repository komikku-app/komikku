package app.komikku.desktop.platform

import app.komikku.desktop.updater.DesktopOs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

/**
 * AWT `FileDialog` (native look) for files on every OS. Folders ([folderDialogKind]): macOS's own folder panel (an AWT
 * `FileDialog` while `apple.awt.fileDialogForDirectories` is set: "New Folder", live updates), Swing `JFileChooser`
 * elsewhere (AWT has no folder dialog on Windows / Linux). M2: `JFileChooser` did not show folders created after it
 * was opened, on macOS the usual way to make one.
 *
 * P16 review fix: these are windows of their own, outside the app lock's gate, so a chooser open when the app locks
 * (after the main window was minimised and shown again) is closed as cancelled, and none opens while [locked].
 */
class AwtFileDialogs(
    private val locked: StateFlow<Boolean> = MutableStateFlow(false),
    private val os: DesktopOs = DesktopOs.current(),
) : DesktopFileDialogs {

    override fun saveFile(title: String, directory: File?, suggestedName: String): File? = onEdt {
        val dialog = FileDialog(null as Frame?, title, FileDialog.SAVE).apply {
            this.directory = directory?.absolutePath
            file = suggestedName
        }
        whileUnlocked(locked, close = dialog::dispose) {
            dialog.isVisible = true
            dialog.file?.let { File(dialog.directory, it) }
        }
    }

    override fun openFile(title: String, directory: File?, extensions: Set<String>): File? = onEdt {
        val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD).apply {
            this.directory = directory?.absolutePath
            setFilenameFilter { _, name -> extensions.isEmpty() || extensions.any { name.endsWith(".$it", ignoreCase = true) } }
        }
        whileUnlocked(locked, close = dialog::dispose) {
            dialog.isVisible = true
            dialog.file?.let { File(dialog.directory, it) }
        }
    }

    override fun chooseDirectory(title: String, directory: File?): File? = onEdt {
        when (folderDialogKind(os)) {
            FolderDialogKind.MacFolderPanel -> chooseDirectoryWithMacPanel(title, directory)
            FolderDialogKind.SwingChooser -> chooseDirectoryWithSwing(title, directory)
        }
    }

    private fun chooseDirectoryWithMacPanel(title: String, directory: File?): File? {
        val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD).apply {
            this.directory = directory?.absolutePath
        }
        return whileUnlocked(locked, close = dialog::dispose) {
            // Read when the panel opens, so set only around it (and restored): other file dialogs pick files
            withSystemProperty(MAC_FOLDER_DIALOG_PROPERTY, "true") { dialog.isVisible = true }
            dialog.file?.let { File(dialog.directory, it) }
        }
    }

    private fun chooseDirectoryWithSwing(title: String, directory: File?): File? {
        val chooser = JFileChooser(directory).apply {
            dialogTitle = title
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        }
        return whileUnlocked(locked, close = chooser::cancelSelection) {
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
        }
    }

    private fun <T> onEdt(block: () -> T): T {
        if (SwingUtilities.isEventDispatchThread()) return block()
        var result: Result<T>? = null
        SwingUtilities.invokeAndWait { result = runCatching(block) }
        return result!!.getOrThrow()
    }
}

/** Which folder dialog [AwtFileDialogs] opens on [os]. */
internal enum class FolderDialogKind { MacFolderPanel, SwingChooser }

internal fun folderDialogKind(os: DesktopOs): FolderDialogKind = when (os) {
    DesktopOs.MacOs -> FolderDialogKind.MacFolderPanel
    // Windows: no native folder dialog in AWT / Swing without JNA or a helper, so JFileChooser stays
    DesktopOs.Windows, DesktopOs.Linux -> FolderDialogKind.SwingChooser
}

/** Makes macOS's AWT `FileDialog` pick folders instead of files. */
internal const val MAC_FOLDER_DIALOG_PROPERTY = "apple.awt.fileDialogForDirectories"

/** Runs [block] with the system property [key] set to [value], then puts back what was there (or clears it). */
internal fun <T> withSystemProperty(key: String, value: String, block: () -> T): T {
    val previous = System.getProperty(key)
    System.setProperty(key, value)
    try {
        return block()
    } finally {
        if (previous == null) System.clearProperty(key) else System.setProperty(key, previous)
    }
}

/**
 * Runs [show] (a modal chooser, blocking until it is closed) unless [locked]; [close] is called on the event thread once
 * the app locks while it is open, and its result is then dropped (as if cancelled).
 */
internal fun <T> whileUnlocked(locked: StateFlow<Boolean>, close: () -> Unit, show: () -> T?): T? {
    if (locked.value) return null
    val watcher = CoroutineScope(Dispatchers.Default).launch {
        locked.first { it }
        SwingUtilities.invokeLater(close)
    }
    return try {
        show().takeUnless { locked.value }
    } finally {
        watcher.cancel()
    }
}
