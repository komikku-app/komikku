package eu.kanade.tachiyomi.ui.webview

import android.webkit.WebViewClient
import androidx.compose.runtime.Immutable
import io.github.edsuns.adfilter.FilterResult
import io.github.edsuns.adfilter.FilterViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import sample.main.blocking.BlockingInfo
import sample.stripParamsAndAnchor
import timber.log.Timber

class AdFilterModel(
    val filterViewModel: FilterViewModel,
) {
    private val mutableState = MutableStateFlow(State())
    val state: StateFlow<State> = mutableState.asStateFlow()

    val blockedCount = MutableStateFlow("")
    private val blockingInfoMap = MutableStateFlow(HashMap<String, BlockingInfo>())

    private var currentPageUrl : String? = null

    private var dirtyBlockingInfo = false

    /** Should be called in [WebViewClient.shouldInterceptRequest] */
    fun onShouldInterceptRequest(result: FilterResult) {
        logRequest(result)
        updateBlockedCount()
    }

    private fun logRequest(filterResult: FilterResult) {
        val pageUrl = currentPageUrl ?: return
        blockingInfoMap.update { data ->
            val blockingInfo = data[pageUrl] ?: BlockingInfo()
            data[pageUrl] = blockingInfo
            if (filterResult.shouldBlock) {
                val requestUrl = filterResult.resourceUrl.stripParamsAndAnchor()
                blockingInfo.blockedUrlMap[requestUrl] = filterResult.rule ?: ""
                blockingInfo.blockedRequests++
                Timber.v("Web request $requestUrl blocked by rule \"${filterResult.rule}\"")
            }
            blockingInfo.allRequests++
            data
        }
    }

    fun onPageStarted(url: String?) {
        url?.let { currentPageUrl = it }
        updateBlockedCount()
    }

    fun progressChanged(newProgress: Int) {
//        webView.url?.let { currentPageUrl.value = it }
        if (newProgress == 10) {
            clearDirty()
            updateBlockedCount()
        }
    }

    private fun clearDirty() {
        if (dirtyBlockingInfo) {
            blockingInfoMap.value.clear()
            dirtyBlockingInfo = false
        }
    }

    private fun isFilterOn(): Boolean {
        val enabledFilterCount = filterViewModel.enabledFilterCount.value ?: 0
        return filterViewModel.isEnabled.value == true && enabledFilterCount > 0
    }

    private fun updateBlockedCount() {
        when {
            !isFilterOn() && !filterViewModel.isCustomFilterEnabled() -> {
                blockedCount.update { "OFF" }
            }
            dirtyBlockingInfo -> {
                blockedCount.update { "-" }
            }
            else -> {
                val blockedUrlMap =
                    blockingInfoMap.value[currentPageUrl]?.blockedUrlMap
                blockedCount.update { (blockedUrlMap?.size ?: 0).toString() }
            }
        }
    }

    interface Dialog {
    }

    @Immutable
    data class State(
        val dialog: Dialog? = null,
    )
}
