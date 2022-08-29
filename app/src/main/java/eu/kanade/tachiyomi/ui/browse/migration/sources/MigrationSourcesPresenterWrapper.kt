package eu.kanade.tachiyomi.ui.browse.migration.sources

import android.os.Bundle
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter

class MigrationSourcesPresenterWrapper() : BasePresenter<MigrationSourcesController>() {

    val presenter = MigrationSourcesPresenter(presenterScope)

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)
        presenter.onCreate()
    }
}
