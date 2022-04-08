package eu.kanade.tachiyomi.ui.browse.migration.advanced.design

import android.os.Bundle
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.SourceManager
import uy.kohesive.injekt.injectLazy

class MigrationSourceAdapter(
    var items: List<MigrationSourceItem>,
    controllerPre: PreMigrationController,
) : FlexibleAdapter<MigrationSourceItem>(
    items,
    controllerPre,
    true,
) {
    val preferences: PreferencesHelper by injectLazy()
    val sourceManager: SourceManager by injectLazy()

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

    fun updateItems() {
        items = currentItems
    }

    companion object {
        private const val SELECTED_SOURCES_KEY = "selected_sources"
    }
}
