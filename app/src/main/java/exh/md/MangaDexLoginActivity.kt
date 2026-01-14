package exh.md

import android.net.Uri
import androidx.lifecycle.lifecycleScope
import eu.kanade.tachiyomi.ui.setting.track.BaseOAuthLoginActivity
import exh.md.utils.MdUtil
import kotlinx.coroutines.flow.first
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaDexLoginActivity : BaseOAuthLoginActivity() {

    override fun handleResult(uri: Uri) {
        val code = uri.getQueryParameter("code")
        if (code != null) {
            lifecycleScope.launchIO {
                val sourceManager = Injekt.get<SourceManager>()
                sourceManager.isInitialized.first { it }
                MdUtil.getEnabledMangaDex(sourceManager = sourceManager)?.login(code)
                returnToSettings()
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
