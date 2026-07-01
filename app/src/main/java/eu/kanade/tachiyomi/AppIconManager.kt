package eu.kanade.tachiyomi

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

object AppIconManager {

    sealed class AppIcon(val id: String, val alias: String) {
        object Default : AppIcon("default", ".MainActivityDefault")
        object Alt1 : AppIcon("alt1", ".MainActivityAlt1")

        companion object {
            val icons = listOf(Default, Alt1)
            fun fromId(id: String) = icons.find { it.id == id } ?: Default
        }
    }

    fun switchIcon(context: Context, icon: AppIcon) {
        val pm = context.packageManager
        val pkg = context.packageName

        // Disable all first
        AppIcon.icons.forEach {
            pm.setComponentEnabledSetting(
                ComponentName(pkg, "$pkg${it.alias}"),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }

        // Enable selected
        pm.setComponentEnabledSetting(
            ComponentName(pkg, "$pkg${icon.alias}"),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
    }
}
