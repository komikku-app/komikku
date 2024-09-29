package sample.main

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.github.edsuns.adfilter.FilterResult
import sample.main.blocking.BlockingInfo
import sample.stripParamsAndAnchor
import timber.log.Timber

/**
 * Created by Edsuns@qq.com on 2021/2/27.
 */
class MainViewModel : ViewModel() {

    private val _blockingInfoMap = HashMap<String, BlockingInfo>()

    val blockingInfoMap = MutableLiveData(_blockingInfoMap)

    var currentPageUrl: MutableLiveData<String> = MutableLiveData()

    var dirtyBlockingInfo = false

    fun clearDirty() {
        if (dirtyBlockingInfo) {
            _blockingInfoMap.clear()
            dirtyBlockingInfo = false
        }
    }

    fun logRequest(filterResult: FilterResult) {
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
}
