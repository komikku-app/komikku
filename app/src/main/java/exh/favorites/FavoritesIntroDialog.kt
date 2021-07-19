package exh.favorites

import android.content.Context
import androidx.core.text.HtmlCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import uy.kohesive.injekt.injectLazy

class FavoritesIntroDialog {
    private val prefs: PreferencesHelper by injectLazy()

    fun show(context: Context) = MaterialAlertDialogBuilder(context)
        .setTitle(R.string.favorites_sync_notes)
        .setMessage(HtmlCompat.fromHtml(context.getString(R.string.favorites_sync_notes_message), HtmlCompat.FROM_HTML_MODE_LEGACY))
        .setPositiveButton(android.R.string.ok) { _, _ ->
            prefs.exhShowSyncIntro().set(false)
        }
        .setCancelable(false)
        .show()
}
