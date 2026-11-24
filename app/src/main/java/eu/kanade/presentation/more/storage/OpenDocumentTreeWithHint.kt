package eu.kanade.presentation.more.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContracts

// KMK -->
/** Folder picker that can open at a starting location, for example Documents/Komikku. */
internal class OpenDocumentTreeWithHint : ActivityResultContracts.OpenDocumentTree() {
    override fun createIntent(context: Context, input: Uri?): Intent {
        val intent = super.createIntent(context, input)
        input?.let { intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, it) }
        return intent
    }
}
// KMK <--

// KMK -->
/**
 * The system picker only respects `EXTRA_INITIAL_URI` for tree URIs, so a folder hint is built as
 * the tree form of `Documents/<name>`. A document URI is accepted as well for providers that
 * expose folders that way.
 */
internal object FolderPickerHint {
    /**
     * Builds the document URI of `Documents/<name>`, the form Android documents the initial
     * location with. The tree form is accepted too, but only when a name is known, since the bare
     * Documents tree URI points at the storage root on some devices.
     */
    fun uriFor(folderName: String?): Uri =
        if (folderName.isNullOrEmpty()) {
            documentsFolderDocumentUri(DEFAULT_STORAGE_FOLDER_NAME)
        } else {
            documentsFolderDocumentUri(folderName)
        }
}
// KMK <--
