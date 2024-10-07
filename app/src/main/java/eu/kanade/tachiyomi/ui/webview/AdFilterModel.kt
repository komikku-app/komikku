package eu.kanade.tachiyomi.ui.webview

import android.webkit.WebViewClient
import io.github.edsuns.adfilter.FilterResult
import io.github.edsuns.adfilter.FilterViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import sample.main.WebClient
import sample.main.blocking.BlockingInfo
import sample.stripParamsAndAnchor
import tachiyomi.core.common.util.lang.launchUI
import timber.log.Timber
import java.util.Locale

class AdFilterModel(
    val filterViewModel: FilterViewModel,
) {
    val dialog = MutableStateFlow<Dialog?>(null)

    /** number of (unique) blocked requests on current page */
    val blockedCount by lazy { _blockedCount.asStateFlow() }
    private val _blockedCount by lazy { MutableStateFlow("") }

    private val currentPageUrl by lazy { MutableStateFlow("") }

    /**
     * Contains the blocking info for each visited [currentPageUrl]:
     * - Total requests made
     * - Total requests blocked
     * - Detail of blocked requests (URL & filter rule)
     *
     * This map should be cleaned when there is changes with [FilterViewModel.filters].
     */
    val blockingInfoMap by lazy { _blockingInfoMap.asStateFlow() }
    private val _blockingInfoMap by lazy { MutableStateFlow(HashMap<String, BlockingInfo>()) }

    /**
     * Indicates that [_blockingInfoMap] should be cleared.
     * When this is set, `blockingInfoMap` will be cleared when new page loaded.
     * This often will be set when filters settings changed (enable, disable, add, remove, update)
     * via [FilterViewModel.onDirty] flag.
     */
    var dirtyBlockingInfo = false

    /**
     * This function should be called on every page load to check if filters settings had been
     * changed (by mean of set [dirtyBlockingInfo]), it will then clear **all** blocked requests
     * information within [_blockingInfoMap].
     */
    private fun clearDirty() {
        if (dirtyBlockingInfo) {
            _blockingInfoMap.value.clear()
            dirtyBlockingInfo = false
        }
    }

    /**
     * Log filter result of each request for every visited [currentPageUrl].
     */
    private fun logRequest(filterResult: FilterResult) {
        val pageUrl = currentPageUrl.value
        val data = _blockingInfoMap.value.toMutableMap()
        val blockingInfo = data[pageUrl]?.copy() ?: BlockingInfo()
        if (filterResult.shouldBlock) {
            val requestUrl = filterResult.resourceUrl.stripParamsAndAnchor()
            blockingInfo.blockedUrlMap[requestUrl] = filterResult.rule ?: ""
            blockingInfo.blockedRequests++
            Timber.v("Web request $requestUrl blocked by rule \"${filterResult.rule}\"")
        }
        blockingInfo.allRequests++
        data[pageUrl] = blockingInfo
        _blockingInfoMap.update { HashMap(data) }
    }

    /**
     * Should be called in [WebClient.onPageStarted],
     * an override of [WebViewClient.onPageStarted],
     * to perform necessary actions when page starts loading, such as
     * reset blocked requests info when filters settings changed.
     */
    fun onPageStarted(url: String?) {
        launchUI {
            url?.let { url -> currentPageUrl.update { url } }
            // Clear the counter if filters settings had been changed
            clearDirty()
            updateBlockedCount()
        }
    }

    /**
     * Update blocked count text
     */
    internal fun updateBlockedCount() {
        val blockedUrlMap =
            blockingInfoMap.value[currentPageUrl.value]?.blockedUrlMap
        val blockedCount = blockedUrlMap?.size
        when {
            !filterViewModel.isFilterOn() && !filterViewModel.isCustomFilterEnabled() -> {
                _blockedCount.update { "OFF" }
            }
            blockedCount == null -> {
                _blockedCount.update { "-" }
            }
            else -> {
                _blockedCount.update { String.format(Locale.getDefault(), "%d", blockedUrlMap.size) }
            }
        }
    }

    /**
     * Should be called in [WebClient.shouldInterceptRequest],
     * an override of [WebViewClient.shouldInterceptRequest],
     * to perform necessary actions each time a request is made, such as
     * log filtering results of requests.
     */
    fun onShouldInterceptRequest(result: FilterResult) {
        if (filterViewModel.isFilterOn()) logRequest(result)
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

        data class FilterSettingsDialog(
            val onDismissDialog: (() -> Unit)?,
        ) : Dialog
    }
}
