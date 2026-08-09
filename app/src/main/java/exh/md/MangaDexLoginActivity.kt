package exh.md

import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import eu.kanade.tachiyomi.ui.setting.track.BaseOAuthLoginActivity
import exh.md.utils.MdUtil
import kotlinx.coroutines.flow.first
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaDexLoginActivity : BaseOAuthLoginActivity() {

    override fun handleResult(uri: Uri) {
        val code = uri.getQueryParameter("code")
        if (code != null) {
            lifecycleScope.launchIO {
                try {
                    val sourceManager = Injekt.get<SourceManager>()
                    sourceManager.isInitialized.first { it }
                    val success = MdUtil.getEnabledMangaDex(sourceManager = sourceManager)?.login(code) == true
                    if (!success) {
                        Toast.makeText(this@MangaDexLoginActivity, stringResource(KMR.strings.login_failed), Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@MangaDexLoginActivity, e.message ?: stringResource(MR.strings.unknown_error), Toast.LENGTH_LONG).show()
                } finally {
                    returnToSettings()
                }
            }
        } else {
            lifecycleScope.launchIO {
                val sourceManager = Injekt.get<SourceManager>()
                sourceManager.isInitialized.first { it }
                MdUtil.getEnabledMangaDex(sourceManager = sourceManager)?.logout()
                returnToSettings()
            }
        }
    }
}
