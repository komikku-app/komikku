package exh.debug

import android.annotation.SuppressLint
import android.app.Activity
import android.util.Log
import androidx.core.text.HtmlCompat
import androidx.preference.PreferenceScreen
import com.afollestad.materialdialogs.MaterialDialog
import eu.kanade.tachiyomi.ui.setting.SettingsController
import eu.kanade.tachiyomi.util.preference.defaultValue
import eu.kanade.tachiyomi.util.preference.onClick
import eu.kanade.tachiyomi.util.preference.preference
import eu.kanade.tachiyomi.util.preference.preferenceCategory
import eu.kanade.tachiyomi.util.preference.switchPreference
import exh.util.capitalize
import java.util.Locale
import kotlin.reflect.KVisibility
import kotlin.reflect.full.declaredFunctions

class SettingsDebugController : SettingsController() {
    @SuppressLint("SetTextI18n")
    override fun setupPreferenceScreen(screen: PreferenceScreen) = screen.apply {
        title = "DEBUG MENU"

        preferenceCategory {
            title = "Functions"

            DebugFunctions::class.declaredFunctions.filter {
                it.visibility == KVisibility.PUBLIC
            }.forEach {
                preference {
                    title = it.name.replace("(.)(\\p{Upper})".toRegex(), "$1 $2").lowercase(Locale.getDefault()).capitalize(Locale.getDefault())
                    isPersistent = false

                    onClick {
                        try {
                            val result = it.call(DebugFunctions)
                            val text = "Function returned result:\n\n$result"
                            MaterialDialog(context)
                                .title(text = title.toString())
                                .message(text = text) {
                                    messageTextView.apply {
                                        setHorizontallyScrolling(true)
                                        setTextIsSelectable(true)
                                    }
                                }
                        } catch (t: Throwable) {
                            val text = "Function threw exception:\n\n${Log.getStackTraceString(t)}"
                            MaterialDialog(context)
                                .message(text = text) {
                                    messageTextView.apply {
                                        setHorizontallyScrolling(true)
                                        setTextIsSelectable(true)
                                    }
                                }
                        }.show()
                    }
                }
            }
        }

        preferenceCategory {
            title = "Toggles"

            DebugToggles.values().forEach {
                switchPreference {
                    title = it.name.replace('_', ' ').lowercase(Locale.getDefault()).capitalize(Locale.getDefault())
                    key = it.prefKey
                    defaultValue = it.default
                    summaryOn = if (it.default) "" else MODIFIED_TEXT
                    summaryOff = if (it.default) MODIFIED_TEXT else ""
                }
            }
        }
    }

    override fun onActivityStopped(activity: Activity) {
        super.onActivityStopped(activity)
        router.popCurrentController()
    }

    companion object {
        private val MODIFIED_TEXT = HtmlCompat.fromHtml("<font color='red'>MODIFIED</font>", HtmlCompat.FROM_HTML_MODE_LEGACY)
    }
}
