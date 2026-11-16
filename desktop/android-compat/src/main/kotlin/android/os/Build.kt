package android.os

/**
 * android-compat: `android.os.Build`. Desktop reports itself as a generic device running
 * [VERSION.SDK_INT] = 34 (Android 14), so version-gated code takes the modern paths that the
 * compat layer implements.
 */
class Build private constructor() {

    object VERSION {
        @JvmField val SDK_INT: Int = VERSION_CODES.UPSIDE_DOWN_CAKE

        @JvmField val RELEASE: String = "14"

        @JvmField val INCREMENTAL: String = "desktop"

        @JvmField val CODENAME: String = "REL"

        @JvmField val PREVIEW_SDK_INT: Int = 0

        @JvmField val SECURITY_PATCH: String = ""
    }

    @Suppress("ktlint:standard:class-naming", "ClassName")
    object VERSION_CODES {
        const val CUR_DEVELOPMENT = 10000
        const val BASE = 1
        const val KITKAT = 19
        const val LOLLIPOP = 21
        const val LOLLIPOP_MR1 = 22
        const val M = 23
        const val N = 24
        const val N_MR1 = 25
        const val O = 26
        const val O_MR1 = 27
        const val P = 28
        const val Q = 29
        const val R = 30
        const val S = 31
        const val S_V2 = 32
        const val TIRAMISU = 33
        const val UPSIDE_DOWN_CAKE = 34
        const val VANILLA_ICE_CREAM = 35
        const val BAKLAVA = 36
    }

    companion object {
        const val UNKNOWN = "unknown"

        @JvmField val MANUFACTURER: String = System.getProperty("java.vendor") ?: UNKNOWN

        @JvmField val BRAND: String = "komikku"

        @JvmField val MODEL: String = "desktop"

        @JvmField val DEVICE: String = "desktop"

        @JvmField val PRODUCT: String = "desktop"

        @JvmField val HARDWARE: String = System.getProperty("os.arch") ?: UNKNOWN

        @JvmField val BOARD: String = UNKNOWN

        @JvmField val ID: String = UNKNOWN

        @JvmField val DISPLAY: String = "${System.getProperty("os.name")} ${System.getProperty("os.version")}"

        @JvmField val FINGERPRINT: String = "komikku/desktop/desktop:${VERSION.RELEASE}/$ID/${VERSION.INCREMENTAL}:user/release-keys"

        @JvmField val TAGS: String = "release-keys"

        @JvmField val TYPE: String = "user"

        @JvmField val SUPPORTED_ABIS: Array<String> = arrayOf(System.getProperty("os.arch") ?: UNKNOWN)
    }
}
