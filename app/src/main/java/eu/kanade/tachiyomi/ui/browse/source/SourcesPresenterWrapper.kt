package eu.kanade.tachiyomi.ui.browse.source

import android.os.Bundle
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter

class SourcesPresenterWrapper(controllerMode: SourcesController.Mode, smartSearchConfig: SourcesController.SmartSearchConfig?) : BasePresenter<SourcesPresenter>() {

    val presenter = SourcesPresenter(presenterScope, controllerMode = controllerMode, smartSearchConfig = smartSearchConfig)

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)
        presenter.onCreate()
    }
}
