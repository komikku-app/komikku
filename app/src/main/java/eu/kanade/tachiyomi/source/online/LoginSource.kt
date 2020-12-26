package eu.kanade.tachiyomi.source.online

import android.app.Activity
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.ui.base.controller.DialogController

interface LoginSource : Source {
    val needsLogin: Boolean

    fun isLogged(): Boolean

    fun getLoginDialog(source: Source, activity: Activity): DialogController

    suspend fun login(username: String, password: String, twoFactorCode: String = ""): Boolean

    suspend fun logout(): Boolean
}
