package sample.main.blocking

/**
 * Created by Edsuns@qq.com on 2021/2/27.
 */
data class BlockingInfo(
    var allRequests: Int = 0,
    var blockedRequests: Int = 0,
    val blockedUrlMap: LinkedHashMap<String, String> = LinkedHashMap(),
)
