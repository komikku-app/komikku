package exh.log

import android.content.Context
import androidx.annotation.StringRes
import androidx.preference.PreferenceManager
import eu.kanade.tachiyomi.core.R

enum class EHLogLevel(@StringRes val nameRes: Int, @StringRes val description: Int) {
    MINIMAL(R.string.log_minimal, R.string.log_minimal_desc),
    EXTRA(R.string.log_extra, R.string.log_extra_desc),
    EXTREME(R.string.log_extreme, R.string.log_extreme_desc),
    ;

    companion object {
        private var curLogLevel: Int? = null

        val currentLogLevel get() = values()[curLogLevel!!]

        fun init(context: Context) {
            curLogLevel = PreferenceManager.getDefaultSharedPreferences(context)
                .getInt("eh_log_level", MINIMAL.ordinal) // todo
        }

        fun shouldLog(requiredLogLevel: EHLogLevel): Boolean {
            return curLogLevel!! >= requiredLogLevel.ordinal
        }
    }
}
