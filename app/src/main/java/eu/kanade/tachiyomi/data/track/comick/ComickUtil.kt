package eu.kanade.tachiyomi.data.track.comick

import java.util.Base64

// KMK -->
internal fun String.asHidToLong(): Long {
    require(length == 8) { "Comick HID must be 8 characters" }
    return Base64.getUrlDecoder().decode(this).fold(0L) { acc, byte ->
        (acc shl 8) or (byte.toLong() and 0xFF)
    }
}

internal fun Long.toHid(): String {
    require(this in 0 until (1L shl 48)) { "Invalid Comick title ID" }
    return Base64.getUrlEncoder().withoutPadding().encodeToString(
        ByteArray(6) { index -> (this shr (8 * (5 - index))).toByte() },
    )
}

internal fun Long.toApiListStatus(): Int = when (this) {
    Comick.READING -> 1
    Comick.COMPLETED -> 2
    Comick.ON_HOLD -> 3
    Comick.DROPPED -> 4
    Comick.PLAN_TO_READ -> 5
    else -> error("Unknown Comick status: $this")
}

internal fun Long.fromApiListStatus(): Long = when (this) {
    1L -> Comick.READING
    2L -> Comick.COMPLETED
    3L -> Comick.ON_HOLD
    4L -> Comick.DROPPED
    5L -> Comick.PLAN_TO_READ
    else -> error("Unknown Comick status: $this")
}
// KMK <--
