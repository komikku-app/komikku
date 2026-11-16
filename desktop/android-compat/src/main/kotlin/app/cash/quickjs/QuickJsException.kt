package app.cash.quickjs

/** android-compat: `app.cash.quickjs.QuickJsException`. */
class QuickJsException @JvmOverloads constructor(
    detailMessage: String?,
    @Suppress("UNUSED_PARAMETER") jsStackTrace: String? = null,
) : RuntimeException(detailMessage)
