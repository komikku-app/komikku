package exh.metadata

import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

/**
 * Metadata utils
 */
object MetadataUtil {
    fun humanReadableByteCount(bytes: Long, si: Boolean): String {
        val unit = if (si) 1000 else 1024
        if (bytes < unit) return "$bytes B"
        val exp = (ln(bytes.toDouble()) / ln(unit.toDouble())).toInt()
        val pre = (if (si) "kMGTPE" else "KMGTPE")[exp - 1] + if (si) "" else "i"
        return String.format("%.1f %sB", bytes / unit.toDouble().pow(exp.toDouble()), pre)
    }

    private const val KB_FACTOR: Long = 1000
    private const val KIB_FACTOR: Long = 1024
    private const val MB_FACTOR = 1000 * KB_FACTOR
    private const val MIB_FACTOR = 1024 * KIB_FACTOR
    private const val GB_FACTOR = 1000 * MB_FACTOR
    private const val GIB_FACTOR = 1024 * MIB_FACTOR

    fun parseHumanReadableByteCount(bytes: String): Double? {
        val ret = bytes.substringBefore(' ').toDouble()
        return when (bytes.substringAfter(' ')) {
            "GB" -> ret * GB_FACTOR
            "GiB" -> ret * GIB_FACTOR
            "MB" -> ret * MB_FACTOR
            "MiB" -> ret * MIB_FACTOR
            "KB" -> ret * KB_FACTOR
            "KiB" -> ret * KIB_FACTOR
            else -> null
        }
    }

    val ONGOING_SUFFIX = arrayOf(
        "[ongoing]",
        "(ongoing)",
        "{ongoing}",
        "<ongoing>",
        "ongoing",
        "[incomplete]",
        "(incomplete)",
        "{incomplete}",
        "<incomplete>",
        "incomplete",
        "[wip]",
        "(wip)",
        "{wip}",
        "<wip>",
        "wip",
    )

    val EX_DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
}
