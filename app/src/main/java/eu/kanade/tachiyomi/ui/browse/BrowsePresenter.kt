package eu.kanade.tachiyomi.ui.browse

import android.os.Bundle
import androidx.compose.runtime.getValue
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.ui.browse.extension.ExtensionsPresenter
import eu.kanade.tachiyomi.ui.browse.feed.FeedPresenter
import eu.kanade.tachiyomi.ui.browse.migration.sources.MigrationSourcesPresenter
import eu.kanade.tachiyomi.ui.browse.source.SourcesController
import eu.kanade.tachiyomi.ui.browse.source.SourcesPresenter
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class BrowsePresenter(
    preferences: PreferencesHelper = Injekt.get(),
) : BasePresenter<BrowseController>() {

    val isDownloadOnly: Boolean by preferences.downloadedOnly().asState()
    val isIncognitoMode: Boolean by preferences.incognitoMode().asState()

    // SY -->
    val feedTabInFront = preferences.feedTabInFront().get()
    // SY <--

    // SY -->
    val sourcesPresenter = SourcesPresenter(presenterScope, controllerMode = SourcesController.Mode.CATALOGUE, smartSearchConfig = null)
    val feedPresenter = FeedPresenter(presenterScope)

    // SY <--
    val extensionsPresenter = ExtensionsPresenter(presenterScope)
    val migrationSourcesPresenter = MigrationSourcesPresenter(presenterScope)

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        sourcesPresenter.onCreate()
        // SY -->
        feedPresenter.onCreate()
        // SY <--
        extensionsPresenter.onCreate()
        migrationSourcesPresenter.onCreate()
    }

    // SY -->
    override fun onDestroy() {
        super.onDestroy()
        feedPresenter.onDestroy()
    }
    // SY <--
}
