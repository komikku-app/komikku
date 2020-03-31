package eu.kanade.tachiyomi.ui.base.activity

import android.os.Bundle
import com.bluelinelabs.conductor.Router
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.util.system.LocaleHelper
import exh.ui.lock.LockActivityDelegate
import nucleus.view.NucleusAppCompatActivity

abstract class BaseRxActivity<P : BasePresenter<*>> : NucleusAppCompatActivity<P>() {

    init {
        @Suppress("LeakingThis")
        LocaleHelper.updateConfiguration(this)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LockActivityDelegate.onCreate(this)
    }
}
