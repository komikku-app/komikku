package exh.log

import android.content.Context
import androidx.preference.PreferenceManager
import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.sy.SYMR

enum class EHLogLevel(val nameRes: StringResource, val description: StringResource) {
    MINIMAL(SYMR.strings.log_minimal, SYMR.strings.log_minimal_desc),
    EXTRA(SYMR.strings.log_extra, SYMR.strings.log_extra_desc),
    EXTREME(SYMR.strings.log_extreme, SYMR.strings.log_extreme_desc),
    ;

    companion object {
        private var _curLogLevel: Int = MINIMAL.ordinal
        val curLogLevel: Int get() = _curLogLevel

        const val EH_LOG_LEVEL_PREF = "eh_log_level"

        fun defaultLogLevel(isDebugBuildType: Boolean) = if (isDebugBuildType) EXTRA.ordinal else MINIMAL.ordinal

        val currentLogLevel get() = entries.getOrNull(curLogLevel) ?: MINIMAL

        fun init(
            context: Context,
            isDebugBuildType: Boolean = false,
        ) {
            _curLogLevel = PreferenceManager.getDefaultSharedPreferences(context)
                .getInt(EH_LOG_LEVEL_PREF, defaultLogLevel(isDebugBuildType))
        }

        /**
         * Same as Mihon's `verboseLogging`, which is:
         * - Always follow [Companion.curLogLevel] value which user set
         * - If user hasn't set it, default value is `EXTRA` for *Debug* build type, `MINIMAL` for *Release* build type
         */
        fun isExtraLogging() = shouldLog(EXTRA)

        /**
         * Enable extremely detail `||EH-NETWORK-JSON` log by [maybeInjectEHLogger]
         */
        fun isExtremeLogging() = shouldLog(EXTREME)

        private fun shouldLog(requiredLogLevel: EHLogLevel): Boolean {
            return curLogLevel >= requiredLogLevel.ordinal
        }
    }
}
