package eu.kanade.tachiyomi.ui.base.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.util.system.LocaleHelper
import exh.ui.lock.LockActivityDelegate

abstract class BaseActivity : AppCompatActivity() {

    init {
        @Suppress("LeakingThis")
        LocaleHelper.updateConfiguration(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LockActivityDelegate.onCreate(this)
    }
//    override fun onResume() {
//        super.onResume()
//    }

}
