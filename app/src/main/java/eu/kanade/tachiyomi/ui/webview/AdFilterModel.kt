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
    val dialog = MutableStateFlow<Dialog?>(null)

    private val _blockedCount = MutableStateFlow("")
    val blockedCount = _blockedCount.asStateFlow()
    private val _blockingInfoMap = MutableStateFlow(HashMap<String, BlockingInfo>())
    val blockingInfoMapState = _blockingInfoMap.asStateFlow()

    var currentPageUrl : String? = null

    private var dirtyBlockingInfo = false

    /** Should be called in [WebViewClient.shouldInterceptRequest] */
    fun onShouldInterceptRequest(result: FilterResult) {
        logRequest(result)
        updateBlockedCount()
    }

    private fun logRequest(filterResult: FilterResult) {
        val pageUrl = currentPageUrl ?: return
        _blockingInfoMap.update { data ->
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
            _blockingInfoMap.value.clear()
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
                _blockedCount.update { "OFF" }
            }
            dirtyBlockingInfo -> {
                _blockedCount.update { "-" }
            }
            else -> {
                val blockedUrlMap =
                    _blockingInfoMap.value[currentPageUrl]?.blockedUrlMap
                _blockedCount.update { (blockedUrlMap?.size ?: 0).toString() }
            }
        }
    }

    fun dismissDialog() {
        dialog.update { null }
    }

    fun showFilterLogDialog() {
        dialog.update { Dialog.FilterLogDialog }
    }

    fun showFilterSettingsDialog(onDismissDialog: (() -> Unit)? = null) {
        dialog.update { Dialog.FilterSettingsDialog(onDismissDialog) }
    }

    sealed interface Dialog {
        data object FilterLogDialog : Dialog
        data class FilterSettingsDialog(val onDismissDialog: (() -> Unit)?) : Dialog
    }

    @Immutable
    data class State(
        val dialog: Dialog? = null,
    )
}
