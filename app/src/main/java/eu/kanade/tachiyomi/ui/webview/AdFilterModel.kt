package eu.kanade.tachiyomi.ui.webview

import android.webkit.WebViewClient
import androidx.compose.runtime.Immutable
import androidx.lifecycle.MutableLiveData
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
    // State flow variable to represent the current state
    private val mutableState = MutableStateFlow(State())
    val state: StateFlow<State> = mutableState.asStateFlow()
    val blockedCount = MutableStateFlow("")

    private val _blockingInfoMap = HashMap<String, BlockingInfo>()

    val blockingInfoMap = MutableLiveData(_blockingInfoMap)

    var currentPageUrl: MutableLiveData<String> = MutableLiveData()

    var dirtyBlockingInfo = false

    /** Should be called in [WebViewClient.shouldInterceptRequest] */
    fun onShouldInterceptRequest(result: FilterResult) {
        logRequest(result)
    }

    private fun logRequest(filterResult: FilterResult) {
        val pageUrl = currentPageUrl.value ?: return
        val data = _blockingInfoMap
        val blockingInfo = data[pageUrl] ?: BlockingInfo()
        data[pageUrl] = blockingInfo
        if (filterResult.shouldBlock) {
            val requestUrl = filterResult.resourceUrl.stripParamsAndAnchor()
            blockingInfo.blockedUrlMap[requestUrl] = filterResult.rule ?: ""
            blockingInfo.blockedRequests++
            Timber.v("Web request $requestUrl blocked by rule \"${filterResult.rule}\"")
        }
        blockingInfo.allRequests++
        blockingInfoMap.postValue(data)
    }

    fun onPageStarted(url: String?) {
        url?.let { currentPageUrl.value = it }
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
            _blockingInfoMap.clear()
            dirtyBlockingInfo = false
        }
    }

    fun isFilterOn(): Boolean {
        val enabledFilterCount = filterViewModel.enabledFilterCount.value ?: 0
        return filterViewModel.isEnabled.value == true && enabledFilterCount > 0
    }

    private fun updateBlockedCount() {
        when {
            !isFilterOn() && !filterViewModel.isCustomFilterEnabled() -> {
                blockedCount.update { "OFF" }
                mutableState.update {
                    it.copy(blockedCount = "OFF")
                }
            }
            dirtyBlockingInfo -> {
                mutableState.update {
                    blockedCount.update { "-" }
                    it.copy(blockedCount = "-")
                }
            }
            else -> {
                val blockedUrlMap =
                    blockingInfoMap.value?.get(currentPageUrl.value)?.blockedUrlMap
                blockedCount.update { (blockedUrlMap?.size ?: 0).toString() }
                mutableState.update {
                    it.copy(blockedCount = (blockedUrlMap?.size ?: 0).toString())
                }
            }
        }
    }

    interface Dialog {
    }

    @Immutable
    data class State(
        val dialog: Dialog? = null,
        val blockedCount: String = "",
    )
}
