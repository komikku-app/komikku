package eu.kanade.tachiyomi.ui.browse.migration.advanced.design

import android.os.Bundle
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.source.SourceManager
import uy.kohesive.injekt.injectLazy

class MigrationSourceAdapter(
    controllerPre: PreMigrationController,
) : FlexibleAdapter<MigrationSourceItem>(
    null,
    controllerPre,
    true,
) {
    val sourceManager: SourceManager by injectLazy()

    // SY _->
    val sourcePreferences: SourcePreferences by injectLazy()
    // SY <--

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putParcelableArrayList(
            SELECTED_SOURCES_KEY,
            ArrayList(
                currentItems.map {
                    it.asParcelable()
                },
            ),
        )
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        val selectedSources = savedInstanceState
            .getParcelableArrayList<MigrationSourceItem.MigrationSource>(SELECTED_SOURCES_KEY)

        if (selectedSources != null) {
            updateDataSet(selectedSources.map { MigrationSourceItem.fromParcelable(sourceManager, it) })
        }

        super.onRestoreInstanceState(savedInstanceState)
    }

    companion object {
        private const val SELECTED_SOURCES_KEY = "selected_sources"
    }
}
