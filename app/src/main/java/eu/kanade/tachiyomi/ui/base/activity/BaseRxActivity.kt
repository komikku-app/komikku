package eu.kanade.tachiyomi.ui.base.activity

import android.os.Bundle
import androidx.viewbinding.ViewBinding
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.util.system.LocaleHelper
import exh.ui.lock.LockActivityDelegate
import nucleus.view.NucleusAppCompatActivity

abstract class BaseRxActivity<VB : ViewBinding, P : BasePresenter<*>> : NucleusAppCompatActivity<P>() {

    lateinit var binding: VB

    init {
        @Suppress("LeakingThis")
        LocaleHelper.updateConfiguration(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LockActivityDelegate.onCreate(this)
    }
}
