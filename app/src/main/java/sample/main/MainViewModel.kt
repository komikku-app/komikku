package sample.main

import androidx.lifecycle.ViewModel
import io.github.edsuns.adfilter.FilterResult
import io.github.edsuns.adfilter.FilterViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import sample.main.blocking.BlockingInfo
import sample.stripParamsAndAnchor
import timber.log.Timber

/**
 * Created by Edsuns@qq.com on 2021/2/27.
 */
class MainViewModel : ViewModel() {
    val currentPageUrl by lazy { MutableStateFlow("") }

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
    fun clearDirty() {
        if (dirtyBlockingInfo) {
            _blockingInfoMap.value.clear()
            dirtyBlockingInfo = false
        }
    }

    /**
     * Log filter result of each request for every visited [currentPageUrl].
     */
    fun logRequest(filterResult: FilterResult) {
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
}
