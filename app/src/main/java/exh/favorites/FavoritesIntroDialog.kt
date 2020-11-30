package exh.favorites

import android.content.Context
import androidx.core.text.HtmlCompat
import com.afollestad.materialdialogs.MaterialDialog
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import uy.kohesive.injekt.injectLazy

class FavoritesIntroDialog {
    private val prefs: PreferencesHelper by injectLazy()

    fun show(context: Context) = MaterialDialog(context)
        .title(R.string.favorites_sync_notes)
        .message(text = HtmlCompat.fromHtml(context.getString(R.string.favorites_sync_notes_message), HtmlCompat.FROM_HTML_MODE_LEGACY))
        .positiveButton(android.R.string.ok) {
            prefs.exhShowSyncIntro().set(false)
        }
        .cancelable(false)
        .show()
}
