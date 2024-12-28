package tachiyomi.domain.release.service

class AppUpdatePolicy {
    companion object {
        const val DEVICE_ONLY_ON_WIFI = "wifi"
        const val DEVICE_NETWORK_NOT_METERED = "network_not_metered"
        const val DEVICE_CHARGING = "ac"
        const val DISABLE_AUTO_DOWNLOAD = "disable"
    }
}
